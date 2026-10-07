package com.eventverse.app.infrastructure.discovery

import ai.koog.prompt.executor.clients.deepseek.DeepSeekLLMClient
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import com.eventverse.app.InterviewGuessFn
import com.eventverse.app.infrastructure.EnvLoader
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Path
import java.time.Duration
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.writeText
import kotlin.test.Test
import org.junit.Assume.assumeTrue
import kotlin.test.assertTrue

/**
 * **Evals live wawancara (plan IV-C4, G3, OPT-IN).** Set kasus emas yang sama dengan C0, penilai yang
 * sama, alur G1-G5 penuh lewat [runInterviewFlow]. Tiga gerbang — tanpa semuanya test **dilewati**:
 *
 * ```
 * INTERVIEW_LIVE_EVALS=1 DEEPSEEK_API_KEY=sk-... INTERVIEW_LIVE_EVALS_CONFIRM=yes \
 *   ./gradlew :server:test --tests '*KoogInterviewLiveEvalsTest'
 * ```
 *
 * Disiplin biaya plan IV-C4: estimasi dicetak (margin ganda) dan **wajib dikonfirmasi**; ulangan
 * bawaan **1** dulu, baru 3 (`INTERVIEW_LIVE_EVALS_REPEAT=1..3`); saldo API dicek sebelum/sesudah;
 * laporan ke `docs/plannings/eval-iv-<tanggal-UTC>.md` tanpa menimpa laporan lama; kegagalan model
 * dan kegagalan penilai dipisah. Jalur fallback deterministik sengaja tidak ada — yang diukur LLM.
 *
 * **Skrip ini TIDAK dijalankan oleh agent** — butuh kunci API dan konfirmasi biaya eksplisit
 * pemilik akun (keputusan G3 milik koordinator).
 */
class KoogInterviewLiveEvalsTest {

    private fun repetitions(): Int =
        System.getenv("INTERVIEW_LIVE_EVALS_REPEAT")?.trim()?.toIntOrNull()?.coerceIn(1, 3) ?: 1

    @Test
    fun `alur wawancara penuh dinilai dari model hidup dan laporan tertulis`() = runBlocking {
        assumeTrue("INTERVIEW_LIVE_EVALS bukan 1 - evals live dilewati", System.getenv("INTERVIEW_LIVE_EVALS") == "1")
        val apiKey = System.getenv("DEEPSEEK_API_KEY")?.takeIf { it.isNotBlank() }
        assumeTrue("DEEPSEEK_API_KEY kosong - evals live dilewati", apiKey != null)
        val key = requireNotNull(apiKey)
        val reps = repetitions()
        val cases = selectedInterviewCases()
        val rounds = AgentInterviewGuesser.DEFAULT_MAX_CORRECTION_ROUNDS
        assumeTrue(
            "Konfirmasi biaya belum diberikan. " + InterviewLiveEvalReport.costEstimate(cases.size, reps, rounds) +
                ". Set INTERVIEW_LIVE_EVALS_CONFIRM=yes untuk menjalankan.",
            System.getenv("INTERVIEW_LIVE_EVALS_CONFIRM") == "yes"
        )

        val balanceBefore = fetchBalance(key)
        println("evals | saldo sebelum: " + (balanceBefore ?: "-"))

        val counting = CountingPromptExecutor(MultiLLMPromptExecutor(DeepSeekLLMClient(key)))
        val model = DiscoveryAgents.resolveModel(EnvLoader.get("INTERVIEW_AGENT_MODEL").takeIf { it.isNotBlank() })
        val guesser = AgentInterviewGuesser(counting, model = model)
        val results = mutableListOf<InterviewLiveCaseResult>()

        for (case in cases) {
            var pass = 0
            val modelFails = mutableListOf<String>()
            val graderFails = mutableListOf<String>()
            val turnsUsed = mutableListOf<Int>()
            val turnDurations = mutableListOf<Long>()
            var tokens: Long? = 0L

            repeat(reps) { rep ->
                val callsBefore = counting.calls
                val flow = runCatching {
                    runInterviewFlow(
                        case,
                        guessFn = InterviewGuessFn { step, pack, draft, narrative -> guesser.guess(step, pack, draft, narrative) }
                    )
                }
                flow.onSuccess { result ->
                    result.turns.forEach { t ->
                        println(
                            "evals | ${case.name} ulangan-$rep | ${t.step} | tebakan=${t.guessCount} " +
                                "kunci=${t.keyCovered}/${t.keyTotal} | ${t.durationMs}ms"
                        )
                    }
                    println("evals | " + result.verdict.logLine(guesser.agentRef))
                    turnsUsed += result.turns.size
                    turnDurations += result.turns.map { it.durationMs }
                    if (result.verdict.passed) pass++ else {
                        graderFails += "${case.name} ulangan-$rep: " +
                            result.verdict.failedCriteria.joinToString("; ") { "${it.criterion}: ${it.detail.take(120)}" }
                    }
                }.onFailure { modelFails += "${case.name} ulangan-$rep: ${it.message?.take(160)}" }

                val spent = counting.snapshot().drop(callsBefore)
                    .fold(0L to true) { acc, call -> (acc.first + (call.totalTokens?.toLong() ?: 0L)) to (acc.second && call.totalTokens != null) }
                tokens = if (flow.isFailure || !spent.second) null else tokens?.plus(spent.first)
            }

            results += InterviewLiveCaseResult(case.name, reps, pass, modelFails, graderFails, turnsUsed, turnDurations, tokens)
        }

        val balanceAfter = fetchBalance(key)
        println("evals | saldo sesudah: " + (balanceAfter ?: "-"))

        val report = InterviewLiveEvalReport.build(
            agentRef = guesser.agentRef,
            modelId = model.id,
            generatedAt = Clock.System.now().toString(),
            maxCorrectionRounds = rounds,
            balanceBefore = balanceBefore,
            balanceAfter = balanceAfter,
            baselineNote = "Baseline deterministik (`DeterministicInterviewGuesser`, B1) belum tersedia — " +
                "pelari alur sudah siap; baseline wajib 100% sebelum skor LLM dibandingkan.",
            results = results
        )
        println(report)
        writeReport(report)

        assertTrue(
            results.any { it.passCount > 0 || it.graderFailures.isNotEmpty() },
            "Tidak ada satu pun ulangan yang menghasilkan sesi ternilai — prompt/kontrak atau kunci API yang salah; lihat laporan"
        )
    }

    /** Lokasi laporan: `INTERVIEW_LIVE_EVAL_REPORT` bila ditetapkan, else `<root-repo>/docs/plannings/` tanpa menimpa. */
    private fun writeReport(report: String) {
        val override = System.getenv("INTERVIEW_LIVE_EVAL_REPORT")
        val target = override?.let { Path.of(it) } ?: run {
            var dir: Path? = Path.of(System.getProperty("user.dir")).toAbsolutePath()
            while (dir != null && !dir.resolve("settings.gradle.kts").exists()) dir = dir.parent
            val root = requireNotNull(dir) { "settings.gradle.kts tidak ditemukan dari cwd ke atas" }
            InterviewLiveEvalReport.reportPath(root.resolve("docs").resolve("plannings"))
        }
        target.parent?.createDirectories()
        target.writeText(report)
        println("evals | laporan tertulis: $target")
    }

    /** Saldo DeepSeek (best-effort); kegagalan jaringan bukan kegagalan eval. Kunci tidak pernah dicetak. */
    private fun fetchBalance(apiKey: String): String? = runCatching {
        val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
        val request = HttpRequest.newBuilder()
            .uri(URI.create("https://api.deepseek.com/user/balance"))
            .header("Authorization", "Bearer $apiKey")
            .timeout(Duration.ofSeconds(10))
            .GET().build()
        val body = client.send(request, HttpResponse.BodyHandlers.ofString()).body()
        Regex("\"total_balance\"\\s*:\\s*\"([^\"]+)\"").find(body)?.groupValues?.get(1)?.let { "$it (DeepSeek)" }
    }.getOrNull()
}
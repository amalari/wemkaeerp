package com.eventverse.app.infrastructure.discovery

import ai.koog.prompt.executor.clients.deepseek.DeepSeekLLMClient
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import com.eventverse.app.DiscoveryEvalGrader
import com.eventverse.app.DiscoveryGoldenCases
import com.eventverse.app.domain.discovery.DiscoveryRequest
import com.eventverse.app.domain.discovery.DeterministicDiscoveryAgent
import com.eventverse.app.infrastructure.EnvLoader
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import org.junit.Assume.assumeTrue
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * **Evals agent LLM hidup (plan SP-C5).** Set kasus emas yang **sama** dengan baseline
 * (`DiscoveryGoldenCases`, ≥ 10 vertikal), dinilai grader yang sama ([DiscoveryEvalGrader]), diulang
 * **≥ 3 ulangan per kasus** untuk mengukur variasi. Mencetak perkiraan biaya sebelum jalan dan menulis
 * laporan `docs/plannings/eval-SP-koog-<tanggal>.md` (skor per kasus & kriteria, variasi antar-ulangan,
 * putaran koreksi, token/waktu, perbandingan baseline).
 *
 * Tiga gerbang opt-in — tanpa semuanya test **dilewati** (`assumeTrue`) sehingga `:server:test` biasa
 * tetap tanpa jaringan, tanpa kunci, tanpa biaya:
 *
 * ```
 * DISCOVERY_LIVE_EVALS=1 DEEPSEEK_API_KEY=sk-… DISCOVERY_LIVE_EVALS_CONFIRM=yes \
 *   ./gradlew :server:test --tests '*KoogDiscoveryLiveEvalsTest'
 * ```
 *
 * `DISCOVERY_LIVE_EVALS_CONFIRM=yes` adalah **konfirmasi biaya**: perkiraan (kasus × ulangan × putaran)
 * dicetak di pesan penolakan, dan pelari wajib menyetujuinya eksplisit setelah meninjau.
 * `DISCOVERY_LIVE_EVALS_REPEAT` mengubah jumlah ulangan (1–5; bawaan 3). Jalur cadangan deterministik
 * sengaja **dimatikan**: evals mengukur LLM, bukan menyamarkan kegagalannya. Kunci API hanya dari env
 * dan tidak pernah dicetak.
 */
class KoogDiscoveryLiveEvalsTest {

    /** `DISCOVERY_LIVE_EVALS_CASES=sablon-bordir,klinik` menjalankan sebagian kasus (hemat biaya saat memverifikasi satu perbaikan). */
    private fun selectedCases() = System.getenv("DISCOVERY_LIVE_EVALS_CASES")?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }
        ?.let { names -> DiscoveryGoldenCases.all.filter { it.name in names }.ifEmpty { error("DISCOVERY_LIVE_EVALS_CASES tidak cocok dengan kasus mana pun: $names") } }
        ?: DiscoveryGoldenCases.all

    @Test
    fun `narasi emas dinilai berulang dari model hidup dan laporan tertulis`() = runBlocking {
        assumeTrue("DISCOVERY_LIVE_EVALS bukan 1 — evals LLM hidup dilewati", System.getenv("DISCOVERY_LIVE_EVALS") == "1")
        val apiKey = System.getenv("DEEPSEEK_API_KEY")?.takeIf { it.isNotBlank() }
        assumeTrue("DEEPSEEK_API_KEY kosong — evals LLM hidup dilewati", apiKey != null)
        val repetitions = System.getenv("DISCOVERY_LIVE_EVALS_REPEAT")?.trim()?.toIntOrNull()?.coerceIn(1, 5) ?: 3
        val rounds = KoogDiscoveryAgent.DEFAULT_MAX_CORRECTION_ROUNDS
        assumeTrue(
            "Konfirmasi biaya belum diberikan. " +
                DiscoveryLiveEvalReport.costEstimate(selectedCases().size, repetitions, rounds) +
                ". Set DISCOVERY_LIVE_EVALS_CONFIRM=yes untuk menjalankan.",
            System.getenv("DISCOVERY_LIVE_EVALS_CONFIRM") == "yes"
        )

        val counting = CountingPromptExecutor(MultiLLMPromptExecutor(DeepSeekLLMClient(requireNotNull(apiKey))))
        val model = DiscoveryAgents.resolveModel(EnvLoader.get("DISCOVERY_AGENT_MODEL").takeIf { it.isNotBlank() })
        val agent = KoogDiscoveryAgent(executor = counting, model = model, fallback = null)

        val results = mutableListOf<LiveCaseResult>()
        for (case in selectedCases()) {
            var pass = 0
            val failedCriteria = mutableListOf<String>()
            val roundsUsed = mutableListOf<Int>()
            val durations = mutableListOf<Long>()
            val tokens = mutableListOf<Long?>()

            repeat(repetitions) { rep ->
                val callsBefore = counting.calls
                val started = System.currentTimeMillis()
                val verdict = DiscoveryEvalGrader.grade(case, agent.draft(DiscoveryRequest(case.narrative, case.industryHint)))
                durations += System.currentTimeMillis() - started
                roundsUsed += (counting.calls - callsBefore).coerceAtLeast(1)
                tokens += counting.snapshot().drop(callsBefore)
                    .fold(0L to true) { acc, call ->
                        val known = acc.second && call.totalTokens != null
                        (acc.first + (call.totalTokens ?: 0)) to known
                    }
                    .let { (sum, known) -> if (known) sum else null }
                println("evals | ulangan-${rep + 1}/$repetitions | " + verdict.logLine(agent.agentRef))
                if (verdict.passed) pass++
                else failedCriteria += verdict.criteria.filter { !it.passed }.map { "${it.criterion}: ${it.detail.take(120)}" }
            }

            results += LiveCaseResult(
                caseName = case.name,
                repetitions = repetitions,
                passCount = pass,
                failureSummary = failedCriteria.distinct().joinToString().ifEmpty { "-" },
                rounds = roundsUsed,
                durationsMs = durations,
                totalTokens = tokens.reduceOrNull { acc, v -> if (acc != null && v != null) acc + v else null }
            )
        }

        val baseline = DeterministicDiscoveryAgent()
        val baselineLines = selectedCases().map { case ->
            DiscoveryEvalGrader.grade(case, baseline.draft(DiscoveryRequest(case.narrative, case.industryHint)))
                .logLine(baseline.agentRef)
        }
        val report = DiscoveryLiveEvalReport.build(
            agentRef = agent.agentRef,
            modelId = model.id,
            generatedAt = Clock.System.now().toString(),
            maxCorrectionRounds = rounds,
            baselineLines = baselineLines,
            results = results
        )
        println(report)
        writeReport(report)

        assertTrue(
            results.any { it.passCount > 0 },
            "Tidak satu pun ulangan lolos — prompt/kontrak atau kunci API yang salah; lihat laporan"
        )
    }

    /**
     * Lokasi laporan: `DISCOVERY_LIVE_EVAL_REPORT` bila ditetapkan, else `<root-repo>/docs/plannings/
     * eval-SP-koog-<tanggal>.md` — root dicari dari direktori kerja ke atas sampai `settings.gradle.kts`.
     */
    private fun writeReport(report: String) {
        val override = System.getenv("DISCOVERY_LIVE_EVAL_REPORT")
        val target = override?.let { Path.of(it) } ?: run {
            var dir: Path? = Path.of(System.getProperty("user.dir")).toAbsolutePath()
            while (dir != null && !dir.resolve("settings.gradle.kts").exists()) dir = dir.parent
            val root = dir ?: Path.of(".").toAbsolutePath()
            val date = Clock.System.now().toString().take(10)
            root.resolve("docs").resolve("plannings").resolve("eval-SP-koog-$date.md")
        }
        target.parent?.toFile()?.mkdirs()
        target.writeText(report)
        println("evals | laporan tertulis: $target")
    }
}

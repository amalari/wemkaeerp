package com.eventverse.app.infrastructure.builder

import ai.koog.prompt.executor.clients.deepseek.DeepSeekLLMClient
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import com.eventverse.app.domain.builder.ModuleEditRequest
import com.eventverse.app.domain.discovery.proposal.applyEdits
import com.eventverse.app.infrastructure.discovery.CountingPromptExecutor
import com.eventverse.app.infrastructure.discovery.DiscoveryAgents
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Path
import java.time.Duration
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.junit.Assume.assumeTrue

/**
 * **Eval live Builder (OPT-IN, berbiaya)**: penanya klarifikasi dan penyunting isian dijalankan dengan model sungguhan,
 * **flash lawan pro** pada kasus yang sama ([BuilderLiveEvalCases]). Tiga gerbang — tanpa semuanya test dilewati:
 *
 * ```
 * BUILDER_LIVE_EVALS=1 BUILDER_LIVE_EVALS_CONFIRM=yes DEEPSEEK_API_KEY=sk-... \
 *   ./gradlew :server:test --tests '*BuilderLiveEvalsTest'
 * ```
 *
 * Penilaian otomatis dan tegas (tanpa model penilai). Laporan ke `docs/plannings/eval-builder-<tanggal>.md` tanpa menimpa;
 * saldo API dicek sebelum/sesudah; kegagalan model (galat/timeout) dipisah dari kegagalan jawaban.
 */
class BuilderLiveEvalsTest {

    private class Row(val model: String, val stage: String, val name: String, val pass: Boolean, val note: String, val ms: Long, val tokens: Int?, val firstAttemptOk: Boolean? = null)

    @Test
    fun `eval live penanya dan penyunting, flash lawan pro`() = runBlocking<Unit> {
        assumeTrue("BUILDER_LIVE_EVALS bukan 1 - dilewati", System.getenv("BUILDER_LIVE_EVALS") == "1")
        val key = System.getenv("DEEPSEEK_API_KEY")?.takeIf { it.isNotBlank() }
        assumeTrue("DEEPSEEK_API_KEY kosong - dilewati", key != null)
        assumeTrue("Konfirmasi biaya belum diberikan (BUILDER_LIVE_EVALS_CONFIRM=yes)", System.getenv("BUILDER_LIVE_EVALS_CONFIRM") == "yes")
        val apiKey = requireNotNull(key)

        val models = listOf("deepseek-flash", "deepseek-v4-pro")
        val calls = (BuilderLiveEvalCases.clarify.size + BuilderLiveEvalCases.edit.size * 13 / 10) * models.size
        println("estimasi | model=${models.size} kasus-penanya=${BuilderLiveEvalCases.clarify.size} kasus-penyunting=${BuilderLiveEvalCases.edit.size} panggilan-LLM-maks≈$calls token-maks≈${calls * 2500}")
        val balanceBefore = fetchBalance(apiKey)
        println("evals | saldo sebelum: ${balanceBefore ?: "-"}")

        val rows = mutableListOf<Row>()
        val questionsSeen = mutableListOf<String>()
        for (modelId in models) {
            val counting = CountingPromptExecutor(MultiLLMPromptExecutor(DeepSeekLLMClient(apiKey)))
            val model = DiscoveryAgents.resolveModel(modelId)
            val clarifier = KoogNarrativeClarifier(counting, model, timeoutMillis = 180_000)
            val editor = KoogModuleEditor(counting, model, timeoutMillis = 180_000)

            for (c in BuilderLiveEvalCases.clarify) {
                val before = counting.calls
                val started = System.currentTimeMillis()
                val result = runCatching { clarifier.clarify(c.narrative, c.existingModules) }
                val ms = System.currentTimeMillis() - started
                val tokens = counting.snapshot().drop(before).sumOf { it.totalTokens ?: 0 }
                val row = result.fold(
                    onSuccess = { qs ->
                        val asked = qs.isNotEmpty()
                        if (asked) questionsSeen += "[$modelId/${c.name}] " + qs.joinToString(" | ") { it.question }
                        Row(modelId, "penanya", c.name, asked == c.shouldAsk && qs.size <= 3,
                            (if (asked) "bertanya ${qs.size}" else "tidak bertanya") + " (seharusnya " + (if (c.shouldAsk) "bertanya" else "tidak") + ")", ms, tokens)
                    },
                    onFailure = { Row(modelId, "penanya", c.name, false, "GALAT MODEL: ${it.message?.take(80)}", ms, tokens) }
                )
                println("evals | ${row.model} | ${row.stage} | ${row.name} | ${if (row.pass) "LULUS" else "GAGAL"} | ${row.note} | ${row.ms}ms")
                rows += row
            }

            for (c in BuilderLiveEvalCases.edit) {
                val before = counting.calls
                val started = System.currentTimeMillis()
                val outcome = runCatching { runEdit(editor, c) }
                val ms = System.currentTimeMillis() - started
                val tokens = counting.snapshot().drop(before).sumOf { it.totalTokens ?: 0 }
                val row = outcome.fold(
                    onSuccess = { Row(modelId, "penyunting", c.name, it.first == null, it.first ?: it.second, ms, tokens, it.third) },
                    onFailure = { Row(modelId, "penyunting", c.name, false, "GALAT MODEL: ${it.message?.take(80)}", ms, tokens) }
                )
                println("evals | ${row.model} | ${row.stage} | ${row.name} | ${if (row.pass) "LULUS" else "GAGAL"} | ${row.note} | ${row.ms}ms")
                rows += row
            }
        }

        val balanceAfter = fetchBalance(apiKey)
        println("evals | saldo sesudah: ${balanceAfter ?: "-"}")
        val report = buildReport(rows, questionsSeen, balanceBefore, balanceAfter)
        println(report)
        writeReport(report)
        assertTrue(rows.any { !it.note.startsWith("GALAT MODEL") }, "Semua kasus galat model - kunci API atau kontrak salah; lihat laporan")
    }

    /** Meniru [com.eventverse.app.domain.builder.EditModuleFromChat]: maksimal 2 percobaan, galat sunting dikirim balik. Mengembalikan (alasan gagal | null, catatan, lolos percobaan pertama). */
    private suspend fun runEdit(editor: KoogModuleEditor, c: EditEvalCase): Triple<String?, String, Boolean> {
        val base = BuilderLiveEvalCases.baseProposal
        var feedback: String? = null
        var firstOk = false
        repeat(2) { attempt ->
            val reply = editor.edit(ModuleEditRequest("klinik_poli", "Poli", base, c.message, c.answered, feedback)).getOrThrow()
            if (c.expectNoChange) {
                return if (reply.edits.isEmpty()) Triple(null, "tanpa sunting (benar)", true)
                else Triple("melakukan ${reply.edits.size} sunting padahal di luar isian", "", false)
            }
            if (reply.edits.isEmpty()) return Triple("tidak menyunting apa pun", "", false)
            val applied = base.applyEdits(reply.edits)
            if (applied.isSuccess) {
                if (attempt == 0) firstOk = true
                return Triple(c.check(applied.getOrThrow()), "lolos ${if (attempt == 0) "percobaan 1" else "setelah umpan balik"}", firstOk)
            }
            feedback = applied.exceptionOrNull()?.message
        }
        return Triple("sunting ditolak validator dua kali: ${feedback?.take(100)}", "", false)
    }

    private fun buildReport(rows: List<Row>, questions: List<String>, before: String?, after: String?): String = buildString {
        appendLine("# Eval Live Builder - penanya klarifikasi & penyunting isian (flash vs pro)")
        appendLine()
        appendLine("- Dibuat: ${Clock.System.now()} (UTC)")
        appendLine("- Penilaian: otomatis dan tegas (keputusan bertanya; keadaan field akhir setelah `applyEdits` + validator). Tidak ada model penilai.")
        appendLine("- Saldo API: sebelum ${before ?: "-"} -> sesudah ${after ?: "-"}")
        appendLine()
        for (stage in listOf("penanya", "penyunting")) {
            appendLine("## Tahap: $stage")
            appendLine()
            appendLine("| Model | Lulus | Galat model | Rata-rata ms | Maks ms | Token |")
            appendLine("|---|---|---|---|---|---|")
            rows.filter { it.stage == stage }.groupBy { it.model }.forEach { (m, rs) ->
                val ok = rs.count { it.pass }
                appendLine("| $m | $ok/${rs.size} | ${rs.count { it.note.startsWith("GALAT MODEL") }} | ${rs.map { it.ms }.average().toLong()} | ${rs.maxOf { it.ms }} | ${rs.sumOf { it.tokens ?: 0 }} |")
            }
            appendLine()
            appendLine("| Kasus | " + rows.filter { it.stage == stage }.map { it.model }.distinct().joinToString(" | ") + " |")
            appendLine("|---|" + "---|".repeat(rows.filter { it.stage == stage }.map { it.model }.distinct().size))
            rows.filter { it.stage == stage }.groupBy { it.name }.forEach { (n, rs) ->
                appendLine("| $n | " + rs.joinToString(" | ") { (if (it.pass) "LULUS" else "GAGAL") + " - " + it.note + " (${it.ms}ms)" } + " |")
            }
            appendLine()
        }
        val firstAttempt = rows.filter { it.stage == "penyunting" && it.firstAttemptOk != null }.groupBy { it.model }
        appendLine("## Penyunting: lolos pada percobaan pertama (tanpa umpan balik)")
        firstAttempt.forEach { (m, rs) -> appendLine("- $m: ${rs.count { it.firstAttemptOk == true }}/${rs.size}") }
        appendLine()
        appendLine("## Pertanyaan yang diajukan penanya (untuk ditinjau manusia - mutu isi tak bisa dinilai mesin)")
        questions.forEach { appendLine("- $it") }
        appendLine()
        appendLine("## Keputusan")
        appendLine()
        appendLine("- Model penanya (flash / pro): _")
        appendLine("- Model penyunting (flash / pro): _")
    }

    private fun writeReport(report: String) {
        var dir: Path? = Path.of(System.getProperty("user.dir")).toAbsolutePath()
        while (dir != null && !dir.resolve("settings.gradle.kts").exists()) dir = dir.parent
        val root = requireNotNull(dir) { "settings.gradle.kts tidak ditemukan dari cwd ke atas" }
        val date = Clock.System.now().toLocalDateTime(TimeZone.UTC).date.toString()
        val folder = root.resolve("docs").resolve("plannings")
        var target = folder.resolve("eval-builder-$date.md")
        var round = 2
        while (target.exists()) { target = folder.resolve("eval-builder-$date-ronde$round.md"); round++ }
        target.parent?.createDirectories()
        target.writeText(report)
        println("evals | laporan tertulis: $target")
    }

    private fun fetchBalance(apiKey: String): String? = runCatching {
        val request = HttpRequest.newBuilder().uri(URI.create("https://api.deepseek.com/user/balance"))
            .header("Authorization", "Bearer $apiKey").timeout(Duration.ofSeconds(10)).GET().build()
        val body = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build().send(request, HttpResponse.BodyHandlers.ofString()).body()
        Regex("\"total_balance\"\\s*:\\s*\"([^\"]+)\"").find(body)?.groupValues?.get(1)?.let { "$it (DeepSeek)" }
    }.getOrNull()
}

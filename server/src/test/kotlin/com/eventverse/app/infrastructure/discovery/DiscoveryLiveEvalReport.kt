package com.eventverse.app.infrastructure.discovery

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.Prompt
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.streaming.StreamFrame
import kotlinx.coroutines.flow.Flow
import java.util.Collections

/**
 * Satu ulangan evals hidup: lulus berapa dari berapa, di mana gagalnya, berapa putaran koreksi terpakai
 * (proxy: panggilan LLM), durasi, dan token (bila provider menyebutnya di metaInfo).
 */
data class LiveCaseResult(
    val caseName: String,
    val repetitions: Int,
    val passCount: Int,
    val failureSummary: String,
    val rounds: List<Int>,
    val durationsMs: List<Long>,
    /** Total token seluruh ulangan kasus ini; `null` = provider tidak menyebut token di metaInfo. */
    val totalTokens: Long?
) {
    val passRatio: String get() = "$passCount/$repetitions"

    /** `true` bila jumlah putaran antar-ulangan tidak seragam — sinyal ketidakstabilan model. */
    val roundsVary: Boolean get() = rounds.distinct().size > 1
}

/**
 * Perkiraan & laporan eval live (plan SP-C5) — **formatter murni** yang dites sendiri
 * (`DiscoveryLiveEvalReportTest`); yang berjaringan hanya `KoogDiscoveryLiveEvalsTest` yang memanggilnya.
 * Laporan berisi skor per kasus & per kriteria, variasi antar-ulangan, putaran koreksi, token/waktu, dan
 * perbandingan dengan baseline deterministik. **Rekomendasinya diisi koordinator** (G3 = keputusan, bukan janji).
 */
object DiscoveryLiveEvalReport {

    /** Kasar untuk perkiraan biaya (prompt sistem+contoh+narasi+jawaban per putaran); bukan angka tagihan. */
    const val EST_TOKENS_PER_ROUND = 6_000

    fun costEstimate(cases: Int, repetitions: Int, maxCorrectionRounds: Int): String {
        val maxRounds = maxCorrectionRounds + 1
        val maxCalls = cases * repetitions * maxRounds
        return "estimasi | kasus=$cases ulangan=$repetitions putaran-maks=$maxRounds " +
            "panggilan-LLM-maks=$maxCalls token-maks≈${maxCalls * EST_TOKENS_PER_ROUND}"
    }

    fun build(
        agentRef: String,
        modelId: String,
        generatedAt: String,
        maxCorrectionRounds: Int,
        baselineLines: List<String>,
        results: List<LiveCaseResult>
    ): String = buildString {
        appendLine("# Eval Live Koog — ScreenProposal (SP C5)")
        appendLine()
        appendLine("- Agent: `$agentRef` (model `$modelId`)")
        appendLine("- Dibuat: $generatedAt")
        appendLine("- Batas koreksi: $maxCorrectionRounds putaran per run")
        appendLine("- Penilaian: `DiscoveryEvalGrader` (kriteria §6); baseline deterministik wajib 100%")
        appendLine()
        appendLine("## Perkiraan biaya sebelum jalan")
        appendLine()
        appendLine("```")
        appendLine(costEstimate(results.size, results.maxOfOrNull { it.repetitions } ?: 0, maxCorrectionRounds))
        appendLine("```")
        appendLine()
        appendLine("## Hasil per kasus")
        appendLine()
        appendLine("| Kasus | Lulus | Putaran koreksi (min–maks) | Waktu (min–maks ms) | Token | Catatan |")
        appendLine("|---|---|---|---|---|---|")
        results.forEach { r ->
            appendLine(
                "| ${r.caseName} | ${r.passRatio} | ${r.rounds.min()}–${r.rounds.max()} | " +
                    "${r.durationsMs.min()}–${r.durationsMs.max()} | ${r.totalTokens?.toString() ?: "-"} | " +
                    r.failureSummary + " |"
            )
        }
        appendLine()
        appendLine("## Variasi antar-ulangan")
        appendLine()
        results.forEach { r ->
            val stability = if (r.roundsVary) "fluktuatif (putaran ${r.rounds.distinct().sorted()})" else "stabil"
            appendLine("- ${r.caseName}: ${r.passRatio} lulus, $stability")
        }
        appendLine()
        appendLine("## Perbandingan dengan baseline deterministik")
        appendLine()
        baselineLines.forEach { appendLine("`$it`  ") }
        appendLine()
        appendLine("## Rekomendasi (diisi koordinator — G3)")
        appendLine()
        appendLine("- Skor LLM: ${results.sumOf { it.passCount }} dari ${results.sumOf { it.repetitions }} ulangan; baseline: 100%.")
        appendLine("- Keputusan (Koog default / hanya bila deterministik gagal / belum layak): _")
    }
}

/**
 * Satu panggilan LLM yang dicatat [CountingPromptExecutor]: durasi dan pemakaian token dari metaInfo
 * (provider yang tidak menyebut token bernilai `null` — laporan menampilkannya sebagai "-", bukan nol).
 */
internal class LlmCall(val durationMs: Long, val totalTokens: Int?, val inputTokens: Int?, val outputTokens: Int?)

/**
 * `PromptExecutor` penghitung yang **mendelegasikan semuanya** ke executor produksi — satu-satunya
 * tambahannya adalah pencatatan durasi/token per panggilan untuk laporan C5. Jalur Koog
 * (`singleRunStrategy`, alat, parser) tetap persis produksi.
 */
internal class CountingPromptExecutor(private val delegate: PromptExecutor) : PromptExecutor() {

    private val recorded: MutableList<LlmCall> = Collections.synchronizedList(mutableListOf())

    val calls: Int get() = synchronized(recorded) { recorded.size }

    fun snapshot(): List<LlmCall> = synchronized(recorded) { recorded.toList() }

    override suspend fun execute(prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>): Message.Assistant {
        val started = System.currentTimeMillis()
        val answer = delegate.execute(prompt, model, tools)
        val meta = answer.metaInfo
        recorded += LlmCall(System.currentTimeMillis() - started, meta.totalTokensCount, meta.inputTokensCount, meta.outputTokensCount)
        return answer
    }

    override fun executeStreaming(prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>): Flow<StreamFrame> =
        delegate.executeStreaming(prompt, model, tools)

    override suspend fun moderate(prompt: Prompt, model: LLModel): ModerationResult = delegate.moderate(prompt, model)

    override fun close() = delegate.close()
}

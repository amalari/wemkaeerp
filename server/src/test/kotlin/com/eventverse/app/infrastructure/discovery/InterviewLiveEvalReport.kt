package com.eventverse.app.infrastructure.discovery

import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.io.path.exists
import java.nio.file.Path

/** Hasil satu ulangan eval live wawancara untuk satu kasus (plan IV-C4). */
data class InterviewLiveCaseResult(
    val caseName: String,
    val repetitions: Int,
    /** Ulangan yang lulus seluruh kriteria penilai. */
    val passCount: Int,
    /** Ulangan yang gagal **model**: penebak tidak menghasilkan usulan sah setelah putaran koreksi. */
    val modelFailures: List<String>,
    /** Ulangan yang gagal **penilai**: usulan sah, tetapi kriteria kunci tidak terpenuhi. */
    val graderFailures: List<String>,
    val turnsUsed: List<Int>,
    /** Durasi per giliran (ms) seluruh ulangan kasus ini — target plan: < 15-20 detik. */
    val turnDurationsMs: List<Long>,
    val totalTokens: Long?
) {
    val passRatio: String get() = "$passCount/$repetitions"
    val maxTurnMs: Long? get() = turnDurationsMs.maxOrNull()
    val latencyFlag: String get() = when (val m = maxTurnMs) {
        null -> "-"
        else -> if (m > LATENCY_TARGET_MS) "LAMBAT (${m}ms > $LATENCY_TARGET_MS)" else "ok"
    }

    companion object {
        /** Target latensi plan IV-C6: tiap giliran < ~15-20 detik. */
        const val LATENCY_TARGET_MS = 20_000L
    }
}

/**
 * Perkiraan biaya & laporan eval live wawancara (plan IV-C4) — **formatter murni** yang dites sendiri
 * (`InterviewLiveEvalReportTest`); yang berjaringan hanya `KoogInterviewLiveEvalsTest`.
 *
 * Pelajaran SP yang ditegakkan di sini: (1) estimasi biaya **margin ganda** — estimasi lama meleset ±4×,
 * jadi token per putaran ditaksir 2× `DiscoveryLiveEvalReport.EST_TOKENS_PER_ROUND`; (2) kegagalan
 * **penilai** dan **model** dipisah — penilai yang terlalu kaku bukan kesalahan model; (3) laporan ke
 * `eval-iv-<tanggal UTC>.md` dan **tidak pernah menimpa** laporan lama.
 */
object InterviewLiveEvalReport {

    /** Margin ganda atas `DiscoveryLiveEvalReport.EST_TOKENS_PER_ROUND` (6.000) — plan IV-C4. */
    const val EST_TOKENS_PER_ROUND = 12_000

    fun costEstimate(cases: Int, repetitions: Int, maxCorrectionRounds: Int, turnsPerCase: Int = 4): String {
        val maxRounds = maxCorrectionRounds + 1
        val maxCalls = cases * repetitions * turnsPerCase * maxRounds
        return "estimasi (margin ganda) | kasus=$cases ulangan=$repetitions giliran-per-kasus=$turnsPerCase " +
            "putaran-maks=$maxRounds panggilan-LLM-maks=$maxCalls token-maks≈${maxCalls * EST_TOKENS_PER_ROUND}"
    }

    /** Tanggal UTC untuk nama laporan — bukan tanggal lokal, agar dua mesin menyepakati satu nama. */
    fun utcDate(now: kotlinx.datetime.Instant = Clock.System.now()): String =
        now.toLocalDateTime(TimeZone.UTC).date.toString()

    /** Jalur laporan yang **tidak menimpa**: `eval-iv-<tanggal>.md`, lalu `-ronde2`, `-ronde3`, … */
    fun reportPath(dir: Path, existingCheck: (Path) -> Boolean = { it.exists() }, now: kotlinx.datetime.Instant = Clock.System.now()): Path {
        val date = utcDate(now)
        var candidate = dir.resolve("eval-iv-$date.md")
        var round = 2
        while (existingCheck(candidate)) {
            candidate = dir.resolve("eval-iv-$date-ronde$round.md")
            round++
        }
        return candidate
    }

    fun build(
        agentRef: String,
        modelId: String,
        generatedAt: String,
        maxCorrectionRounds: Int,
        balanceBefore: String?,
        balanceAfter: String?,
        baselineNote: String,
        results: List<InterviewLiveCaseResult>
    ): String = buildString {
        appendLine("# Eval Live Koog — Wawancara (IV C4)")
        appendLine()
        appendLine("- Agent: `$agentRef` (model `$modelId`)")
        appendLine("- Dibuat: $generatedAt (UTC)")
        appendLine("- Batas koreksi: $maxCorrectionRounds putaran per giliran; ulangan bawaan 1 (naik ke 3 via `INTERVIEW_LIVE_EVALS_REPEAT`)")
        appendLine("- Penilaian: `InterviewEvalGrader` (plan IV-C0); kalibrasi penilai terbuka — lihat KDoc grader")
        appendLine()
        appendLine("## Saldo API (sebelum -> sesudah)")
        appendLine()
        appendLine("- Sebelum: " + (balanceBefore ?: "-"))
        appendLine("- Sesudah: " + (balanceAfter ?: "-"))
        appendLine()
        appendLine("## Hasil per kasus")
        appendLine()
        appendLine("| Kasus | Lulus | Gagal model | Gagal penilai | Giliran (min-maks) | Latensi giliran | Token |")
        appendLine("|---|---|---|---|---|---|---|")
        results.forEach { r ->
            appendLine(
                "| ${r.caseName} | ${r.passRatio} | ${r.modelFailures.size} | ${r.graderFailures.size} | " +
                    "${r.turnsUsed.minOrNull() ?: "-"}-${r.turnsUsed.maxOrNull() ?: "-"} | ${r.latencyFlag} | ${r.totalTokens?.toString() ?: "-"} |"
            )
        }
        appendLine()
        appendLine("## Pemisahan kegagalan (gagal model vs gagal penilai)")
        appendLine()
        val modelFails = results.flatMap { it.modelFailures }
        val graderFails = results.flatMap { it.graderFailures }
        appendLine("- **Gagal model** (${modelFails.size}): ${modelFails.ifEmpty { listOf("tidak ada") }.joinToString("; ") { it.take(160) }}")
        appendLine("- **Gagal penilai** (${graderFails.size}): ${graderFails.ifEmpty { listOf("tidak ada") }.joinToString("; ") { it.take(160) }}")
        appendLine()
        appendLine("## Perbandingan dengan baseline deterministik")
        appendLine()
        appendLine(baselineNote)
        appendLine()
        appendLine("## Keputusan koordinator (G3)")
        appendLine()
        appendLine("- Skor LLM: ${results.sumOf { it.passCount }} dari ${results.sumOf { it.repetitions }} ulangan; baseline deterministik: lihat bagian perbandingan (wajib 100%).")
        appendLine("- Keputusan (Koog untuk tebakan / hanya bila deterministik gagal / belum layak): _")
    }
}

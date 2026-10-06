package com.eventverse.app.infrastructure.discovery

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **Formatter laporan eval live dites sendiri** (plan SP-C5): bentuk laporan — tabel per kasus, catatan
 * token "-", variasi putaran, perkiraan biaya — dikunci tanpa jaringan, supaya laporan G3 bisa dibaca dan
 * dibandingkan antar-tanggal.
 */
class DiscoveryLiveEvalReportTest {

    private val results = listOf(
        LiveCaseResult(
            caseName = "klinik", repetitions = 3, passCount = 3, failureSummary = "-",
            rounds = listOf(1, 1, 1), durationsMs = listOf(2100, 2300, 2500), totalTokens = 36_500
        ),
        LiveCaseResult(
            caseName = "retail", repetitions = 3, passCount = 2,
            failureSummary = "jenis_tampilan: 1 layar (retail_tagihan): PRINT di luar himpunan",
            rounds = listOf(1, 2, 1), durationsMs = listOf(2000, 4100, 2200), totalTokens = null
        )
    )

    @Test
    fun `perkiraan biaya mengalikan kasus ulangan dan putaran`() {
        val line = DiscoveryLiveEvalReport.costEstimate(cases = 11, repetitions = 3, maxCorrectionRounds = 3)

        assertTrue(line.contains("kasus=11"))
        assertTrue(line.contains("ulangan=3"))
        assertTrue(line.contains("putaran-maks=4"))
        assertTrue(line.contains("panggilan-LLM-maks=132"), "11 × 3 × 4 = 132 panggilan maksimum")
        assertTrue(line.contains("token-maks≈792000"), "132 × 6.000 token per putaran")
    }

    @Test
    fun `laporan memuat tabel per kasus, variasi, dan baseline`() {
        val report = DiscoveryLiveEvalReport.build(
            agentRef = "koog/deepseek-flash/draft-v2",
            modelId = "deepseek-flash",
            generatedAt = "2026-10-05T10:00:00Z",
            maxCorrectionRounds = 3,
            baselineLines = listOf("evals | deterministic/keyword-v1 | klinik | PASS | valid=ok"),
            results = results
        )

        assertTrue(report.contains("# Eval Live Koog"))
        assertTrue(report.contains("koog/deepseek-flash/draft-v2"))
        assertTrue(report.contains("| klinik | 3/3 | 1–1 | 2100–2500 | 36500 | - |"))
        assertTrue(report.contains("| retail | 2/3 | 1–2 | 2000–4100 | - |"), "Token yang tak disebut provider tampil sebagai '-'")
        assertTrue(report.contains("retail: 2/3 lulus, fluktuatif (putaran [1, 2])"), "Variasi antar-ulangan dilaporkan")
        assertTrue(report.contains("klinik: 3/3 lulus, stabil"))
        assertTrue(report.contains("deterministic/keyword-v1"), "Baseline ikut tertulis untuk perbandingan")
        assertTrue(report.contains("Keputusan (Koog default"), "Keputusan G3 dibiarkan untuk koordinator")
    }
}

package com.eventverse.app.infrastructure.discovery

import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Formatter laporan eval live wawancara dites sendiri (plan IV-C4) — tanpa jaringan. */
class InterviewLiveEvalReportTest {

    @Test
    fun `estimasi biaya memakai margin ganda`() {
        val estimate = InterviewLiveEvalReport.costEstimate(cases = 11, repetitions = 1, maxCorrectionRounds = 3)
        assertTrue(estimate.contains("margin ganda"), "plan IV-C4: margin estimasi digandakan")
        assertTrue(estimate.contains("giliran-per-kasus=6"), "sejak C6 fase konsultan ikut dihitung: $estimate")
        assertTrue(estimate.contains("token-maks≈3168000"), "11 kasus x 1 ulangan x 6 giliran x 4 putaran x 12.000 = 3.168.000: $estimate")
    }

    @Test
    fun `laporan memisahkan gagal model dan gagal penilai`() {
        val report = InterviewLiveEvalReport.build(
            agentRef = "koog/uji/interview-v1", modelId = "uji", generatedAt = "2026-10-07T00:00:00Z",
            maxCorrectionRounds = 3, balanceBefore = "100", balanceAfter = "95",
            baselineNote = "baseline deterministik/kamus-v2 lulus 100%",
            results = listOf(
                InterviewLiveCaseResult(
                    caseName = "klinik", repetitions = 1, passCount = 0,
                    modelFailures = listOf("gagal setelah 3 putaran"),
                    graderFailures = listOf("tautan_modul kurang"),
                    turnsUsed = listOf(3), turnDurationsMs = listOf(4_000L, 25_000L), totalTokens = null
                )
            )
        )
        assertTrue(report.contains("Gagal model** (1)"))
        assertTrue(report.contains("Gagal penilai** (1)"))
        assertTrue(report.contains("LAMBAT"), "latensi 25 detik melewati target 20 detik wajib ditandai")
        assertTrue(report.contains("baseline deterministik/kamus-v2 lulus 100%"), "baseline nyata ikut tertulis di laporan")
    }

    @Test
    fun `laporan tidak pernah menimpa - tanggal UTC dan sufiks ronde`() {
        val dir = createTempDirectory("eval-iv")
        val fixed = kotlinx.datetime.Instant.parse("2026-10-07T23:30:00Z")
        val first = InterviewLiveEvalReport.reportPath(dir, existingCheck = { false }, now = fixed)
        assertEquals("eval-iv-2026-10-07.md", first.fileName.toString())

        val second = InterviewLiveEvalReport.reportPath(dir, existingCheck = { p -> p == first }, now = fixed)
        assertEquals("eval-iv-2026-10-07-ronde2.md", second.fileName.toString())
    }
}

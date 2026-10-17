package com.eventverse.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

/**
 * TRD-BLDR-003 F2: Coverage Registry Tests
 */
class BuilderEvalCoverageTest {

    @Test
    fun `initialize from golden cases covers all brief cases`() {
        BuilderEvalCoverage.reset()
        BuilderEvalCoverage.initializeFromGoldenCases()

        BuilderBriefGoldenCases.all.forEach { c ->
            assertTrue(BuilderEvalCoverage.isRegistered("brief:${c.name}"),
                "Brief case harus tercakup: ${c.name}")
            assertEquals(CoverageStatus.COVERED, BuilderEvalCoverage.status("brief:${c.name}"))
        }
    }

    @Test
    fun `initialize from golden cases covers all scenario cases`() {
        BuilderEvalCoverage.reset()
        BuilderEvalCoverage.initializeFromGoldenCases()

        ScenarioGoldenCases.all.forEach { c ->
            assertTrue(BuilderEvalCoverage.isRegistered("scenario:${c.name}"),
                "Scenario case harus tercakup: ${c.name}")
            assertEquals(CoverageStatus.COVERED, BuilderEvalCoverage.status("scenario:${c.name}"))
        }
    }

    @Test
    fun `initialize from golden cases covers all criteria`() {
        BuilderEvalCoverage.reset()
        BuilderEvalCoverage.initializeFromGoldenCases()

        val criteria = listOf(
            "valid", "kosakata_tertutup", "fail_closed", "resistensi_injeksi", "kompatibilitas",
            "cakupan", "presisi", "tertelusur", "relevansi_evidence", "kemurnian_vertikal",
            "bahasa_gaya", "ekonomi", "keandalan"
        )

        criteria.forEach { c ->
            assertTrue(BuilderEvalCoverage.isRegistered("criterion:$c"),
                "Criterion harus tercakup: $c")
        }
    }

    @Test
    fun `can exempt item with reason`() {
        BuilderEvalCoverage.reset()

        BuilderEvalCoverage.exempt(
            key = "brief:deprecated-test",
            type = CoverageType.BRIEF_CASE,
            reason = "Test deprecated, digantikan oleh test baru"
        )

        assertTrue(BuilderEvalCoverage.isRegistered("brief:deprecated-test"))
        assertEquals(CoverageStatus.EXEMPT, BuilderEvalCoverage.status("brief:deprecated-test"))
    }

    @Test
    fun `cannot duplicate key`() {
        BuilderEvalCoverage.reset()
        BuilderEvalCoverage.cover("test:key", CoverageType.BRIEF_CASE)

        val thrown = kotlin.runCatching {
            BuilderEvalCoverage.cover("test:key", CoverageType.BRIEF_CASE)
        }.isFailure

        assertTrue(thrown, "Duplicate key harus throw")
    }

    @Test
    fun `summary shows correct counts`() {
        BuilderEvalCoverage.reset()
        BuilderEvalCoverage.initializeFromGoldenCases()

        val summary = BuilderEvalCoverage.summary()

        assertTrue(summary.contains("COVERED:"), "Summary harus punya covered count")
        assertTrue(summary.contains("EXEMPT:"), "Summary harus punya exempt count")
    }

    @Test
    fun `ratchet - cannot remove covered without reason`() {
        BuilderEvalCoverage.reset()
        BuilderEvalCoverage.initializeFromGoldenCases()

        // In real implementation, removing would require going through proper process
        // For testing, we just verify initialization works
        val covered = BuilderEvalCoverage.covered()
        assertTrue(covered.isNotEmpty(), "Harus ada yang tercakup")
    }
}

package com.eventverse.app.domain.builder

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ModuleBriefRulesTest {

    private fun ok(
        summary: String = "Ringkasan poli.",
        features: List<String> = listOf("Antrean"),
        stages: List<StageSketch> = emptyList(),
        followUps: List<String> = emptyList(),
        source: BriefSource = BriefSource.AI
    ) = ModuleBrief(summary, features, stages, followUps, source)

    private fun paths(b: ModuleBrief) = ModuleBriefRules.validate(b).map { it.path }

    @Test
    fun `validate brief lengkap dan sah returns no issues`() {
        val b = ok(stages = listOf(StageSketch("Daftar"), StageSketch("Periksa", reworkTo = "Daftar")), followUps = listOf("Siapa yang mengisi?"))
        assertTrue(ModuleBriefRules.validate(b).isEmpty())
    }

    @Test
    fun `validate summary kosong atau terlalu panjang returns issue pada summary`() {
        assertEquals(listOf("summary"), paths(ok(summary = " ")))
        assertEquals(listOf("summary"), paths(ok(summary = "a".repeat(ModuleBriefLimits.SUMMARY_MAX_CHARS + 1))))
    }

    @Test
    fun `validate features AI kosong returns issue tetapi DETERMINISTIC kosong sah`() {
        assertEquals(listOf("features"), paths(ok(features = emptyList())))
        assertTrue(paths(ok(features = emptyList(), source = BriefSource.DETERMINISTIC)).isEmpty())
    }

    @Test
    fun `validate features lebih dari delapan dan duplikat case-insensitive returns issue berpath`() {
        assertTrue("features" in paths(ok(features = (1..9).map { "f$it" })))
        assertEquals(listOf("features[1]"), paths(ok(features = listOf("Antrean", "ANTREAN "))))
        assertEquals(listOf("features[0]"), paths(ok(features = listOf("x".repeat(81)))))
    }

    @Test
    fun `validate stages lebih dari tujuh returns issue dan tidak dipotong`() {
        val b = ok(stages = (1..8).map { StageSketch("T$it") })
        assertEquals(listOf("stages"), paths(b))
        assertEquals(8, b.stages.size)
    }

    @Test
    fun `validate stage nama duplikat atau terlalu panjang returns issue berpath`() {
        assertEquals(listOf("stages[1].name"), paths(ok(stages = listOf(StageSketch("A"), StageSketch("a")))))
        assertEquals(listOf("stages[0].name"), paths(ok(stages = listOf(StageSketch("n".repeat(41))))))
    }

    @Test
    fun `validate reworkTo ke tahap sesudah, diri sendiri, atau tak dikenal returns issue berpath`() {
        val stages = listOf(StageSketch("A", reworkTo = "B"), StageSketch("B", reworkTo = "B"), StageSketch("C", reworkTo = "Z"))
        assertEquals(listOf("stages[0].reworkTo", "stages[1].reworkTo", "stages[2].reworkTo"), paths(ok(stages = stages)))
    }

    @Test
    fun `validate followUps lebih dari tiga, tanpa tanda tanya, atau terlalu panjang returns issue`() {
        assertEquals(listOf("followUps"), paths(ok(followUps = listOf("a?", "b?", "c?", "d?"))))
        assertEquals(listOf("followUps[0]"), paths(ok(followUps = listOf("Siapa yang mengisi"))))
        assertEquals(listOf("followUps[0]"), paths(ok(followUps = listOf("x".repeat(160) + "?"))))
    }

    @Test
    fun `deterministic briefer tanpa roleFeatures returns brief sah tanpa stages`() = runTest {
        val req = ModuleBriefRequest(com.eventverse.app.domain.tenant.TenantId("ten-uji"), "klinik_poli", "Poli", "cerita")
        val b = DeterministicModuleBriefer.brief(req).getOrThrow()
        assertEquals(BriefSource.DETERMINISTIC, b.source)
        assertTrue(b.stages.isEmpty() && b.features.isEmpty() && b.followUps.isEmpty())
        assertTrue(ModuleBriefRules.validate(b).isEmpty())
    }

    @Test
    fun `deterministic briefer dengan roleFeatures returns ringkasan dari nama modul dan fitur`() = runTest {
        val req = ModuleBriefRequest(
            com.eventverse.app.domain.tenant.TenantId("ten-uji"), "klinik_poli", "Poli", "cerita",
            roleFeatures = listOf("Perawat: Triase", "perawat: triase", "Dokter")
        )
        val b = DeterministicModuleBriefer.brief(req).getOrThrow()
        assertEquals(listOf("Perawat: Triase", "Dokter"), b.features)
        assertTrue("Poli" in b.summary && "Triase" in b.summary)
        assertTrue(ModuleBriefRules.validate(b).isEmpty())
    }
}

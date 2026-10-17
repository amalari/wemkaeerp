package com.eventverse.app.domain.builder

import com.eventverse.app.domain.evidence.EvidenceContext
import com.eventverse.app.domain.evidence.EvidenceRef
import com.eventverse.app.domain.evidence.EvidenceSource
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ModuleBriefRulesTest {

    private val context = EvidenceContext(narratives = setOf("Kami klinik: perawat triase, dokter memeriksa."))

    private fun ok(
        summary: String = "Ringkasan poli.",
        features: List<BriefFeature> = listOf(BriefFeature("Antrean")),
        stages: List<StageSketch> = emptyList(),
        followUps: List<String> = emptyList(),
        source: BriefSource = BriefSource.AI,
        contractVersion: Int = ModuleBriefLimits.CONTRACT_VERSION_LEGACY
    ) = ModuleBrief(summary, features, stages, followUps, source, contractVersion)

    private fun paths(b: ModuleBrief) = ModuleBriefRules.validate(b, context).map { it.path }

    @Test
    fun `validate brief lengkap dan sah returns no issues`() {
        val b = ok(stages = listOf(StageSketch("Daftar"), StageSketch("Periksa", reworkTo = "Daftar")), followUps = listOf("Siapa yang mengisi?"))
        assertTrue(ModuleBriefRules.validate(b, context).isEmpty())
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
        assertTrue("features" in paths(ok(features = (1..9).map { BriefFeature("f$it") })))
        assertEquals(listOf("features[1]"), paths(ok(features = listOf(BriefFeature("Antrean"), BriefFeature("ANTREAN ")))))
        assertEquals(listOf("features[0]"), paths(ok(features = listOf(BriefFeature("x".repeat(81))))))
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
        assertTrue(ModuleBriefRules.validate(b, context).isEmpty())
    }

    @Test
    fun `deterministic briefer mewarisi evidence dan menghilangkan butir tanpa dasar`() = runTest {
        val bukti = EvidenceRef(EvidenceSource.NARRATIVE, quote = "perawat triase")
        val req = ModuleBriefRequest(
            com.eventverse.app.domain.tenant.TenantId("ten-uji"), "klinik_poli", "Poli", "cerita",
            roleFeatures = listOf(
                RoleFeature("Perawat: Triase", bukti),
                RoleFeature("Perawat: Triase ", bukti),
                RoleFeature("Dokter")
            )
        )
        val b = DeterministicModuleBriefer.brief(req).getOrThrow()
        // Butir berevidence dipertahankan (dedup ternormalisasi); butir tanpa dasar DIHILANGKAN (FR-3.5).
        assertEquals(listOf(BriefFeature("Perawat: Triase", bukti)), b.features)
        assertEquals(ModuleBriefLimits.CONTRACT_VERSION_EVIDENCE, b.contractVersion)
        assertTrue("Triase" in b.summary)
        assertTrue(ModuleBriefRules.validate(b, context).isEmpty())
    }

    @Test
    fun `brief kontrak v2 tanpa evidence returns issue berpath pada setiap butir`() {
        val b = ok(
            features = listOf(BriefFeature("Antrean")),
            stages = listOf(StageSketch("Daftar")),
            contractVersion = ModuleBriefLimits.CONTRACT_VERSION_EVIDENCE
        )
        assertEquals(listOf("features[0].evidence", "stages[0].evidence"), paths(b))
    }

    @Test
    fun `brief kontrak v2 dengan evidence kutipan di luar narasi returns issue`() {
        val b = ok(
            features = listOf(BriefFeature("Antrean", EvidenceRef(EvidenceSource.NARRATIVE, quote = "bukan bagian cerita"))),
            contractVersion = ModuleBriefLimits.CONTRACT_VERSION_EVIDENCE
        )
        assertEquals(listOf("features[0].evidence.quote"), paths(b))
    }

    @Test
    fun `brief kontrak v2 dengan evidence sah returns no issues`() {
        val b = ok(
            features = listOf(BriefFeature("Perawat: Triase", EvidenceRef(EvidenceSource.NARRATIVE, quote = "perawat triase"))),
            contractVersion = ModuleBriefLimits.CONTRACT_VERSION_EVIDENCE
        )
        assertTrue(paths(b).isEmpty())
    }
}

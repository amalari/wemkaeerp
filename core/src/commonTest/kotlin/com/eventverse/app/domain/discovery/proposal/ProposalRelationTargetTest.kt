package com.eventverse.app.domain.discovery.proposal

import com.eventverse.app.domain.prototype.FieldType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * C7 (TRD-FIELD-001 FR-2/FR-6, Track A): validator usulan menolak `RELATION` tanpa `target`, target
 * berformat salah, dan target lintas modul yang tidak dapat diresolusi pack — minimal tiga bentuk
 * (kriteria terima §5). Konteks non-garment (`klinik`), Kontrak 6 variability.
 */
class ProposalRelationTargetTest {

    private fun proposalWith(vararg fields: FieldProposal): ScreenProposal {
        val base = ScreenProposalFixtures.kanbanAntrean()
        val entity = requireNotNull(base.entity)
        return base.copy(entity = entity.copy(fields = entity.fields + fields))
    }

    private fun issuePaths(p: ScreenProposal, packModuleIds: Set<String>? = null): List<String> =
        ScreenProposalValidator.validate(p, "$.proposal", null, packModuleIds).map { it.path }

    private fun targetIssue(vararg fields: FieldProposal, packModuleIds: Set<String>? = null): String? =
        issuePaths(proposalWith(*fields), packModuleIds).firstOrNull { it.endsWith(".target") }

    private fun relation(target: String? = null) = FieldProposal("rujukan", "Rujukan pasien", FieldType.RELATION, target = target)

    @Test
    fun relationWithoutTarget_isRejectedWithPath() {
        assertTrue(targetIssue(relation()) != null, "RELATION tanpa target wajib ditolak berpath")
    }

    @Test
    fun relationTarget_shapeIsValidated_atLeastThreeWays() {
        listOf("dua modul", "a:b:c", ":foo", "foo:").forEach { bad ->
            assertTrue(targetIssue(relation(bad)) != null, "target '$bad' harus ditolak")
        }
    }

    @Test
    fun relationTarget_crossModule_mustResolveInPack() {
        // Lintas modul yang tidak ada di pack = tidak bisa diresolusi → ditolak.
        assertTrue(targetIssue(relation("lain:entitas"), packModuleIds = setOf("klinik_antrean")) != null, "modul 'lain' tak ada di pack")
        // Modul sendiri pack atau target satu modul = sah.
        assertEquals(emptyList(), issuePaths(proposalWith(relation("klinik_antrean:pasien")), setOf("klinik_antrean")))
        assertEquals(emptyList(), issuePaths(proposalWith(relation("pasien")), setOf("klinik_antrean")))
        // Tanpa konteks pack (packModuleIds null) resolusi modul tak dapat diperiksa di sini.
        assertEquals(emptyList(), issuePaths(proposalWith(relation("lain:entitas")), null))
    }

    @Test
    fun nonRelationWithTarget_isRejected() {
        assertTrue(targetIssue(FieldProposal("catatan", "Catatan", FieldType.TEXT, target = "pasien")) != null)
    }

    @Test
    fun validRelation_passes_andKeepsTargetThroughConversion() {
        val proposal = proposalWith(relation("pasien"))
        assertEquals(emptyList(), issuePaths(proposal))
        val screen = proposal.toInteractiveScreen().getOrThrow()
        assertEquals("pasien", screen.spec.entity("pasien")!!.field("rujukan")!!.target)
    }
}

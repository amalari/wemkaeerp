package com.eventverse.app.domain.discovery.handoff

import com.eventverse.app.domain.discovery.HandoffScaffoldGenerator
import com.eventverse.app.domain.discovery.WidgetKind
import com.eventverse.app.domain.discovery.proposal.FieldProposal
import com.eventverse.app.domain.discovery.proposal.ScreenProposalFixtures
import com.eventverse.app.domain.discovery.proposal.ScreenProposalValidator
import com.eventverse.app.domain.pack.DomainPackRegistry
import com.eventverse.app.domain.pack.ModuleDefinition
import com.eventverse.app.domain.pack.ModuleId
import com.eventverse.app.domain.pack.ModuleSectionCode
import com.eventverse.app.domain.pack.SlotCode
import com.eventverse.app.domain.pack.tenant.layanan.LayananPilotPack
import com.eventverse.app.domain.prototype.EntitySpec
import com.eventverse.app.domain.prototype.FieldSpec
import com.eventverse.app.domain.prototype.FieldType
import com.eventverse.app.domain.prototype.PrototypeSpec
import com.eventverse.app.domain.prototype.ScreenSpec
import com.eventverse.app.domain.prototype.TableConfig
import com.eventverse.app.domain.rbac.DataScope
import com.eventverse.app.domain.rbac.ModuleKind
import com.eventverse.app.domain.rbac.ScopeCapability
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * TRD-FIELD-004 B2 (FR-2.1/2.2, Q3): rute hasil generate memancarkan validasi keberadaan RELATION di POST dan PUT,
 * spec tanpa RELATION tetap tanpa jejak relasi, dan target ke modul HIERARCHICAL ditolak sampai FR-3.x selesai.
 */
class SpecRoutesRelationEmissionTest {
    private val generator = HandoffScaffoldGenerator()
    private val packExpr = "com.eventverse.app.domain.pack.tenant.layanan.LayananPilotPack.pack"

    @AfterTest fun cleanRegistry() { DomainPackRegistry.unregister(LayananPilotPack.CODE) }

    private fun module(id: String) = ModuleDefinition(
        ModuleId(id), "Uji", "Modul uji", ModuleSectionCode("UTAMA"), ModuleKind.OPERATIONAL, "clipboard",
        ScopeCapability.GLOBAL_ONLY, setOf(DataScope.ALL_TENANT_DATA), slot = SlotCode("uji_slot")
    )

    private fun specWith(vararg fields: FieldSpec) = PrototypeSpec(
        listOf(EntitySpec("item", "Item", fields.toList())),
        listOf(ScreenSpec("s", "S", WidgetKind.TABLE, "item", table = TableConfig(listOf(fields.first().key))))
    )

    private fun routes(spec: PrototypeSpec, moduleId: String = "layanan_uji") =
        generator.generateFromSpec(spec, module(moduleId), 90, packExpr).files.single { it.path.endsWith("Routes.kt") }.content

    private val judul = FieldSpec("judul", "Judul", FieldType.TEXT, required = true)

    @Test fun `routes_relationField_emitsRelationProblemBeforeReducerInPostAndPut`() {
        val code = routes(specWith(judul, FieldSpec("rujukan", "Rujukan", FieldType.RELATION, target = "item")))
        assertTrue("relationResolver: RelationTargetResolver" in code, "rute menerima resolver non-null tanpa default")
        assertTrue("relationTargetProblem(MODULE.value, tenantId, values, resolver)" in code, "validasi memakai aturan tunggal core")
        val chains = code.lines().filter { "relationProblem(tenant.tenantId, values, relationResolver)" in it }
        assertEquals(2, chains.size, "POST dan PUT")
        // Sebelum reducer: pada POST, reducer ada di baris berikutnya; pada PUT, loop reducer sesudah rantai.
        val post = code.indexOf("relationProblem(tenant.tenantId, values, relationResolver)")
        assertTrue(post in 0 until code.indexOf("PrototypeReducer.reduce(SPEC, PrototypeStore(), PrototypeAction.Create"))
        val put = code.lastIndexOf("relationProblem(tenant.tenantId, values, relationResolver)")
        assertTrue(put in 0 until code.indexOf("PrototypeAction.SetField"))
    }

    @Test fun `routes_noRelationField_hasNoTraceOfRelation`() {
        val code = routes(specWith(judul, FieldSpec("catatan", "Catatan", FieldType.TEXT)))
        assertFalse("relation" in code.lowercase(), "spec tanpa RELATION = kode identik dengan sebelum B2 (FR-2.2)")
    }

    @Test fun `wiring_relationField_documentsResolverArgument`() {
        val scaffold = generator.generateFromSpec(specWith(judul, FieldSpec("rujukan", "Rujukan", FieldType.RELATION, target = "item")), module("layanan_uji"), 90, packExpr)
        val wiring = scaffold.files.single { it.path.endsWith("WIRING.md") }.content
        assertTrue("relationTargetResolver" in wiring, "titik pendaftaran harus menyebut resolver")
    }

    @Test fun `generate_relationTargetToHierarchicalModule_rejected`() {
        val spec = specWith(judul, FieldSpec("pelanggan", "Pelanggan", FieldType.RELATION, target = "crm_sales:lead"))
        val e = assertFailsWith<IllegalArgumentException> { routes(spec) }
        assertTrue("HIERARCHICAL" in e.message.orEmpty() && "crm_sales" in e.message.orEmpty(), e.message)
    }

    @Test fun `generate_relationTargetToGlobalOnlyModule_accepted`() {
        val code = routes(specWith(judul, FieldSpec("qc", "QC", FieldType.RELATION, target = "quality_control:inspeksi")))
        assertTrue("relationProblem" in code)
    }

    @Test fun `generate_relationTargetOwnModule_acceptedEvenWithoutPrefix`() {
        val code = routes(specWith(judul, FieldSpec("induk", "Induk", FieldType.RELATION, target = "layanan_uji:item")))
        assertTrue("relationProblem" in code)
    }

    @Test fun `proposal_relationTargetToHierarchicalModule_rejected`() {
        val base = ScreenProposalFixtures.kanbanAntrean()
        val entity = requireNotNull(base.entity)
        val proposal = base.copy(entity = entity.copy(fields = entity.fields + FieldProposal("pelanggan", "Pelanggan", FieldType.RELATION, target = "crm_sales:lead")))
        val issues = ScreenProposalValidator.validate(proposal, "$.proposal", null, setOf("crm_sales"))
        assertTrue(issues.any { it.path.endsWith(".target") && "HIERARCHICAL" in it.message }, issues.toString())
    }
}

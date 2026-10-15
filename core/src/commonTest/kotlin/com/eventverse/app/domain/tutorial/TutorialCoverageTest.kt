package com.eventverse.app.domain.tutorial

import com.eventverse.app.domain.pack.ElearningPack
import com.eventverse.app.domain.pack.GarmentDomainPack
import com.eventverse.app.domain.pack.GarmentModules
import com.eventverse.app.domain.pack.ModuleId
import com.eventverse.app.domain.pack.ShippedTutorialSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Kontrak cakupan tutorial per modul (PLAN-help-003): setiap modul pack wajib minimal satu
 * tutorial atau tercatat di [garmentExempt] dengan alasan. Ratchet dua arah — exempt dicabut
 * begitu tutorial ditulis, sehingga daftar ini menyusut, tidak pernah membesar.
 *
 * Baseline garment (2026-10-15): 5 modul Covered (org_chart, dynamic_rbac, crm_sales,
 * sampling_order, costing_hpp); 10 modul di bawah ini adalah utang HELP-001 yang dicicil.
 */
class TutorialCoverageTest {

    private val pack = GarmentDomainPack.pack
    private val tutorials = TutorialCatalog(ShippedTutorialSource).forPack(pack)

    /** Frozen exempt list — utang terdaftar; satu entri dicabut tiap kali sebuah modul ditambahi tutorial. */
    private val garmentExempt: Map<ModuleId, String> = listOf(
        GarmentModules.FACTORY_FLOW to "Kanvas monitoring; tutorial interaksinya belum ditulis (dicicil HELP-001)",
        GarmentModules.MASTER_DATA to "Data induk berupa tabel CRUD; tutorial belum ditulis (dicicil HELP-001)",
        GarmentModules.VENDOR_CONTACTS to "Tutorial belum ditulis (dicicil HELP-001)",
        GarmentModules.INVENTORY to "Tutorial belum ditulis (dicicil HELP-001)",
        GarmentModules.TECH_PACK_BOM to "Tutorial belum ditulis (dicicil HELP-001)",
        GarmentModules.PRODUCTION_MRP to "Tutorial belum ditulis (dicicil HELP-001)",
        GarmentModules.OPERATOR_EXEC to "Tutorial belum ditulis (dicicil HELP-001)",
        GarmentModules.QUALITY_CONTROL to "Tutorial belum ditulis (dicicil HELP-001)",
        GarmentModules.FULFILLMENT to "Tutorial belum ditulis (dicicil HELP-001)",
        GarmentModules.INVOICING to "Tutorial belum ditulis (dicicil HELP-001)",
    ).toMap()

    @Test
    fun garmentPack_satisfiesCoverageContract() {
        assertEquals(emptyList(), TutorialCoverage.violations(pack, tutorials, garmentExempt))
    }

    @Test
    fun coverageFor_reportsCoveredAndUncoveredModules() {
        val coverage = TutorialCoverage.coverageFor(pack, tutorials).associateBy { it.moduleId }
        assertTrue(coverage.getValue(GarmentModules.CRM_SALES).isCovered)
        assertNull(coverage.getValue(GarmentModules.CRM_SALES).exemptReason)
        assertTrue(!coverage.getValue(GarmentModules.INVENTORY).isCovered)
    }

    @Test
    fun uncoveredModuleWithoutExempt_isAViolation() {
        val missing = garmentExempt.keys.first()
        val problems = TutorialCoverage.violations(pack, tutorials, garmentExempt - missing)
        assertEquals(listOf("Modul ${missing.value}: wajib minimal satu tutorial atau Exempt(alasan)"), problems)
    }

    @Test
    fun coveredModuleWithExempt_mustRemoveExempt() {
        val problems = TutorialCoverage.violations(pack, tutorials, garmentExempt + (GarmentModules.CRM_SALES to "sudah ada"))
        assertTrue(problems.any { "cabut Exempt" in it && GarmentModules.CRM_SALES.value in it }, problems.toString())
    }

    @Test
    fun blankReasonAndUnknownModule_areViolations() {
        val blank = TutorialCoverage.violations(pack, tutorials, garmentExempt + (GarmentModules.INVENTORY to "  "))
        assertTrue(blank.any { "alasan Exempt kosong" in it }, blank.toString())

        val unknown = TutorialCoverage.violations(pack, tutorials, garmentExempt + (ModuleId("tidak_ada") to "x"))
        assertTrue(unknown.any { "tidak ada di pack" in it }, unknown.toString())
    }

    @Test
    fun nonDefaultPack_contractBitesForEveryUncoveredModule() = ElearningPack.registered {
        val elearning = ElearningPack.pack
        val elearningTutorials = TutorialCatalog(ShippedTutorialSource).forPack(elearning)
        val uncovered = elearning.modules.map { it.id }.toSet() -
            elearningTutorials.mapNotNull { it.moduleId }.toSet()
        // Tanpa exempt: setiap modul tanpa tutorial dilaporkan — kontrak menggigit di pack non-default.
        val problems = TutorialCoverage.violations(elearning, elearningTutorials, emptyMap())
        assertEquals(uncovered.size, problems.size)
        uncovered.forEach { id -> assertTrue(problems.any { id.value in it }, "$id tidak dilaporkan: $problems") }
        assertTrue(uncovered.isNotEmpty(), "Fixture e-learning harus punya minimal satu modul tanpa tutorial")
    }
}

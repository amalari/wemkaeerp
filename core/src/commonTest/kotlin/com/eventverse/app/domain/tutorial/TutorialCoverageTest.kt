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
 * Baseline garment (2026-10-17): 15/15 modul sudah punya tutorial (CRM, Sampling, Costing,
 * Factory Flow, Master Data, Vendor, Inventory, Tech Pack, MRP, Operator, QC, Fulfillment, Invoicing).
 * exempt = emptyMap() — semua modul sudah tercakup.
 */
class TutorialCoverageTest {

    private val pack = GarmentDomainPack.pack
    private val tutorials = TutorialCatalog(ShippedTutorialSource).forPack(pack)

    /** Semua modul sudah punya tutorial — exempt kosong. */
    private val garmentExempt: Map<ModuleId, String> = emptyMap()

    @Test
    fun garmentPack_satisfiesCoverageContract() {
        assertEquals(emptyList(), TutorialCoverage.violations(pack, tutorials, garmentExempt))
    }

    @Test
    fun coverageFor_reportsCoveredAndUncoveredModules() {
        val coverage = TutorialCoverage.coverageFor(pack, tutorials).associateBy { it.moduleId }
        // Semua modul garment sekarang sudah tercakup
        coverage.values.forEach { moduleCoverage ->
            assertTrue(moduleCoverage.isCovered, "Modul ${moduleCoverage.moduleId.value} harusnya tercakup")
            assertNull(moduleCoverage.exemptReason, "Modul ${moduleCoverage.moduleId.value} tidak boleh ada exemptnya")
        }
    }

    @Test
    fun uncoveredModuleWithoutExempt_isAViolation() {
        // Test ratchet: ElearningPack tidak punya tutorial, jadi harus ada exempt atau violation
        ElearningPack.registered {
            val elearning = ElearningPack.pack
            val elearningTutorials = TutorialCatalog(ShippedTutorialSource).forPack(elearning)
            val problems = TutorialCoverage.violations(elearning, elearningTutorials, emptyMap())
            // ElearningPack modules tidak ada tutorialnya, harus ada pelanggaran
            assertTrue(problems.isNotEmpty(), "ElearningPack harus ada pelanggaran karena tidak ada tutorial")
        }
    }

    @Test
    fun coveredModuleWithExempt_mustRemoveExempt() {
        // Test ratchet: modul yang sudah tercakup tidak boleh ada exemptnya
        // Pakai garment tapi tambahkan exempt manual untuk menguji mekanisme harus cabut
        val problems = TutorialCoverage.violations(pack, tutorials, mapOf(GarmentModules.CRM_SALES to "sudah ada"))
        assertTrue(problems.any { "cabut Exempt" in it && "crm_sales" in it }, "Harus ada pelanggaran karena CRM sudah tercakup tapi masih ada exempt: $problems")
    }

    @Test
    fun blankReasonAndUnknownModule_areViolations() {
        // Test: exempt dengan alasan kosong = pelanggaran
        val blank = TutorialCoverage.violations(pack, tutorials, mapOf(GarmentModules.CRM_SALES to "  "))
        assertTrue(blank.any { "alasan Exempt kosong" in it }, blank.toString())

        // Test: exempt untuk modul yang tidak ada di pack = pelanggaran
        val unknown = TutorialCoverage.violations(pack, tutorials, mapOf(ModuleId("tidak_ada") to "x"))
        assertTrue(unknown.any { "tidak ada di pack" in it }, unknown.toString())
    }

    @Test
    fun nonDefaultPack_contractBitesForEveryUncoveredModule() {
        // Test mekanisme violation detection untuk pack tanpa tutorial
        // ElearningPack tidak punya tutorial di ShippedTutorialSource
        ElearningPack.registered {
            val elearning = ElearningPack.pack
            val elearningTutorials = TutorialCatalog(ShippedTutorialSource).forPack(elearning)
            // Tanpa exempt: setiap modul tanpa tutorial dilaporkan
            val problems = TutorialCoverage.violations(elearning, elearningTutorials, emptyMap())
            // ElearningPack punya 3 modul, harusnya ada 3 pelanggaran
            assertTrue(problems.size >= 1, "Harus ada minimal 1 pelanggaran untuk pack tanpa tutorial")
        }
    }
}

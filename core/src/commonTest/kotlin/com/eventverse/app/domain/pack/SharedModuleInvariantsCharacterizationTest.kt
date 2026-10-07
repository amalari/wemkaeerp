package com.eventverse.app.domain.pack

import com.eventverse.app.domain.discovery.interview.Confirmation
import com.eventverse.app.domain.discovery.interview.DivisionCode
import com.eventverse.app.domain.discovery.interview.DivisionDraft
import com.eventverse.app.domain.discovery.interview.InterviewFixtures
import com.eventverse.app.domain.discovery.interview.InterviewSession
import com.eventverse.app.domain.discovery.interview.InterviewStep
import com.eventverse.app.domain.discovery.interview.InterviewValidator
import com.eventverse.app.domain.discovery.interview.ItemSource
import com.eventverse.app.domain.discovery.interview.ModuleOrigin
import com.eventverse.app.domain.discovery.interview.RoleDraft
import com.eventverse.app.domain.discovery.interview.RoleKey
import com.eventverse.app.domain.discovery.interview.RoleModuleLink
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * B6 langkah 1 — **karakterisasi** perilaku invarian modul platform *saat ini* (PROPOSAL-iv-B6). Tes ini hijau dan
 * mengunci fakta yang jadi dasar usulan; bila aturannya kelak diubah sengaja, tes yang berubah menunjukkan persis
 * invarian mana yang bergeser.
 */
class SharedModuleInvariantsCharacterizationTest {

    private val garment = GarmentDomainPack.pack
    private val klinik = InterviewFixtures.klinikPack

    @Test
    fun `modul baru tanpa prefiks pack ditolak registri, id platform tidak bisa direbut`() {
        val rebut = klinik.copy(modules = klinik.modules + garment.module(GarmentModules.CRM_SALES)!!.copy(displayName = "Versi saya", section = klinik.sections.last().code, slot = null, kind = com.eventverse.app.domain.rbac.ModuleKind.FOUNDATION))
        assertTrue(DomainPackRegistry.violations(rebut).any { it.contains("crm_sales") && it.contains("definisi berbeda") })
        val asing = klinik.modules.first { it.slot != null }.copy(id = ModuleId("modul_asing"))
        assertTrue(DomainPackRegistry.violations(klinik.copy(modules = klinik.modules + asing)).any { it.contains("berprefiks") })
    }

    @Test
    fun `modul tata kelola platform sudah bisa dipakai pack lain lewat salinan identik`() {
        assertEquals(emptyList(), DomainPackRegistry.violations(klinik))            // klinik memuat org_chart identik
        assertTrue(klinik.module(GarmentModules.ORG_CHART) == garment.module(GarmentModules.ORG_CHART))
    }

    /** Pack non-garment yang menyalin modul operasional platform terpaksa membawa slot, fase, dan port garment. */
    private fun packCopying(moduleId: ModuleId): DomainPack {
        val mod = garment.module(moduleId)!!
        val slot = garment.slot(mod.slot!!)!!
        return DomainPack(
            code = DomainPackCode("katering"), displayName = "Katering",
            phases = listOf(garment.phase(slot.phase)!!), slots = listOf(slot),
            portTypes = setOf(slot.defaultInput, slot.defaultOutput), wiredPortTypes = setOf(slot.defaultInput, slot.defaultOutput),
            sections = listOf(garment.sections.first { it.code == mod.section }), modules = listOf(mod)
        )
    }

    @Test
    fun `menyalin modul operasional platform sah di registri tetapi mencemari pack dengan kosakata garment`() {
        val katering = packCopying(GarmentModules.COSTING_HPP)
        assertEquals(emptyList(), DomainPackRegistry.violations(katering))
        assertEquals(setOf("TechPackAndYieldData", "CostingCalculationResult"), katering.portTypes.map { it.value }.toSet())
        // Fase kanvas pun ikut terbawa: pack katering memuat fase milik garment karena slot-nya menunjuk ke sana.
        assertEquals(garment.slot(GarmentSlots.COSTING_HPP)!!.phase, katering.phases.single().code)
    }

    @Test
    fun `wawancara pack lain menolak modul platform operasional, dan REUSE_PLATFORM khusus non-operasional`() {
        fun session(origin: ModuleOrigin) = InterviewSession(
            step = InterviewStep.G3_MODUL,
            divisions = listOf(DivisionDraft(DivisionCode("biaya"), "Biaya", ItemSource.ANSWER)),
            roles = listOf(RoleDraft(RoleKey("kasir"), "Kasir", DivisionCode("biaya"), ItemSource.ANSWER)),
            links = listOf(RoleModuleLink(RoleKey("kasir"), GarmentModules.COSTING_HPP, origin, confirmed = Confirmation.CONFIRMED))
        )
        val notInPack = InterviewValidator.validate(session(ModuleOrigin.REUSE_PLATFORM), klinik).map { it.path to it.message }
        assertTrue(notInPack.any { it.first == "$.interview.links[0].moduleId" }, notInPack.toString())
        // Walau modulnya disalin ke pack, REUSE_PLATFORM tetap ditolak untuk modul operasional.
        val copied = packCopying(GarmentModules.COSTING_HPP)
        val reuse = InterviewValidator.validate(session(ModuleOrigin.REUSE_PLATFORM), copied).map { it.path }
        assertTrue("$.interview.links[0].origin" in reuse, reuse.toString())
    }
}

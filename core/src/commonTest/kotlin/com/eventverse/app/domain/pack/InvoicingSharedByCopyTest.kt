package com.eventverse.app.domain.pack

import com.eventverse.app.domain.discovery.DiscoveryDraftValidator
import com.eventverse.app.domain.discovery.interview.Confirmation
import com.eventverse.app.domain.discovery.interview.DivisionCode
import com.eventverse.app.domain.discovery.interview.DivisionDraft
import com.eventverse.app.domain.discovery.interview.InterviewFixtures
import com.eventverse.app.domain.discovery.interview.InterviewFixtures.draftOf
import com.eventverse.app.domain.discovery.interview.InterviewSession
import com.eventverse.app.domain.discovery.interview.InterviewStep
import com.eventverse.app.domain.discovery.interview.ItemSource
import com.eventverse.app.domain.discovery.interview.ModuleOrigin
import com.eventverse.app.domain.discovery.interview.RoleDraft
import com.eventverse.app.domain.discovery.interview.RoleKey
import com.eventverse.app.domain.discovery.interview.RoleModuleLink
import com.eventverse.app.domain.discovery.proposal.VerticalPurity
import com.eventverse.app.domain.rbac.ModuleKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** `invoicing` tetap modul **fondasi** dan dipakai pack non-garment lewat salinan identik (keputusan 2026-10-07). */
class InvoicingSharedByCopyTest {

    private val garment = GarmentDomainPack.pack
    private val invoicing = garment.module(GarmentModules.INVOICING)!!

    private val klinikDenganInvoicing = InterviewFixtures.klinikPack.let { k ->
        k.copy(
            sections = k.sections + garment.sections.first { it.code == invoicing.section },
            modules = k.modules + invoicing
        )
    }

    @Test
    fun `invoicing tetap modul fondasi tanpa slot dan tidak ditawarkan sebagai modul bersama berslot`() {
        assertEquals(ModuleKind.FOUNDATION, invoicing.kind)
        assertNull(invoicing.slot)
        assertTrue(GarmentModules.INVOICING !in garment.sharedModules)
    }

    @Test
    fun `nama dan deskripsi invoicing netral, tanpa istilah cadangan garment`() {
        assertNull(VerticalPurity.leak(invoicing.displayName), invoicing.displayName)
        assertNull(VerticalPurity.leak(invoicing.description), invoicing.description)
        assertNull(VerticalPurity.leak(invoicing.description, GarmentReservedTerms.terms))
        assertTrue(!invoicing.description.contains("sample", ignoreCase = true) && !invoicing.description.contains("garmen", ignoreCase = true))
    }

    @Test
    fun `pack non-garment memakai invoicing lewat salinan identik tanpa pelanggaran registri`() {
        assertEquals(emptyList(), DomainPackRegistry.violations(klinikDenganInvoicing))
        assertEquals(emptyList(), DiscoveryDraftValidator.validate(draftOf(klinikDenganInvoicing, null)))
        assertEquals(invoicing, klinikDenganInvoicing.module(GarmentModules.INVOICING))
    }

    @Test
    fun `salinan yang diubah dianggap merebut id platform dan ditolak`() {
        val changed = klinikDenganInvoicing.copy(modules = klinikDenganInvoicing.modules.map {
            if (it.id == GarmentModules.INVOICING) it.copy(description = "Versi saya") else it
        })
        assertTrue(DomainPackRegistry.violations(changed).any { it.contains("invoicing") && it.contains("definisi berbeda") })
    }

    @Test
    fun `wawancara pack non-garment menautkan invoicing sebagai REUSE_PLATFORM`() {
        val s = InterviewSession(
            step = InterviewStep.G3_MODUL,
            divisions = listOf(DivisionDraft(DivisionCode("kasir"), "Kasir", ItemSource.ANSWER)),
            roles = listOf(RoleDraft(RoleKey("kasir"), "Kasir", DivisionCode("kasir"), ItemSource.ANSWER)),
            links = listOf(RoleModuleLink(RoleKey("kasir"), GarmentModules.INVOICING, ModuleOrigin.REUSE_PLATFORM, confirmed = Confirmation.CONFIRMED))
        )
        assertEquals(emptyList(), DiscoveryDraftValidator.validate(draftOf(klinikDenganInvoicing, s)))
    }
}

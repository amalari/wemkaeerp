package com.eventverse.app.domain.pack

import com.eventverse.app.domain.discovery.DiscoveryDraft
import com.eventverse.app.domain.discovery.DiscoveryDraftValidator
import com.eventverse.app.domain.discovery.interview.InterviewFixtures
import com.eventverse.app.domain.discovery.proposal.VerticalPurity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Modul tata kelola/fondasi yang dibagikan lewat salinan identik: teks netral, dan dokumen bertanggal lama tetap sah. */
class NeutralSharedModulesTest {

    private val garment = GarmentDomainPack.pack
    private val sharedByCopy = listOf(GarmentModules.ORG_CHART, GarmentModules.DYNAMIC_RBAC, GarmentModules.FACTORY_FLOW, GarmentModules.INVOICING, GarmentModules.VENDOR_CONTACTS)

    @Test
    fun `org_chart, vendor_contacts, invoicing bebas istilah cadangan garment dan kata pabrik, makloon, subkon`() {
        listOf(GarmentModules.ORG_CHART, GarmentModules.VENDOR_CONTACTS, GarmentModules.INVOICING).forEach { id ->
            val m = garment.module(id)!!
            listOf(m.displayName, m.description).forEach { text ->
                assertNull(VerticalPurity.leak(text), "${id.value}: $text")
                listOf("pabrik", "makloon", "maklon", "subkon", "garmen").forEach { w -> assertTrue(!text.contains(w, ignoreCase = true), "${id.value} masih memuat '$w': $text") }
            }
        }
        assertEquals("Kontak Vendor", garment.module(GarmentModules.VENDOR_CONTACTS)!!.displayName)
    }

    /** Pack non-garment tersimpan (mis. docs/packs/klinik-uji.pack.json) menyalin org_chart dengan teks LAMA. */
    private fun klinikWithOldCopy(id: ModuleId, old: ModuleDefinition.() -> ModuleDefinition): DomainPack {
        val k = InterviewFixtures.klinikPack
        val section = garment.sections.first { it.code == garment.module(id)!!.section }
        return k.copy(
            sections = (k.sections + section).distinctBy { it.code },
            modules = k.modules.filter { it.id != id } + garment.module(id)!!.old()
        )
    }

    @Test
    fun `pack tersimpan dengan teks lama org_chart dan vendor_contacts tetap lolos registri`() {
        val oldOrg = klinikWithOldCopy(GarmentModules.ORG_CHART) { copy(description = "Struktur divisi, jenjang jabatan, dan data karyawan pabrik.") }
        assertEquals(emptyList(), DomainPackRegistry.violations(oldOrg))
        val oldVendor = klinikWithOldCopy(GarmentModules.VENDOR_CONTACTS) {
            copy(displayName = "Kontak Vendor & Makloon", description = "Buku kontak vendor subkon, daftar harga layanan per vendor, dan penunjukan vendor ke proses Vendor Luar.")
        }
        assertEquals(emptyList(), DomainPackRegistry.violations(oldVendor))
        assertEquals(emptyList(), DiscoveryDraftValidator.validate(InterviewFixtures.draftOf(oldOrg, null)))
    }

    @Test
    fun `teks karangan pada salinan tetap ditolak sebagai definisi berbeda`() {
        val fake = klinikWithOldCopy(GarmentModules.ORG_CHART) { copy(description = "Versi saya") }
        assertTrue(DomainPackRegistry.violations(fake).any { it.contains("org_chart") && it.contains("definisi berbeda") })
        val fakeName = klinikWithOldCopy(GarmentModules.VENDOR_CONTACTS) { copy(displayName = "Kontak Lain") }
        assertTrue(DomainPackRegistry.violations(fakeName).any { it.contains("vendor_contacts") })
    }

    @Test
    fun `draf garment lama dengan teks lama org_chart dan vendor_contacts tetap sah`() {
        val old = garment.copy(modules = garment.modules.map {
            when (it.id) {
                GarmentModules.ORG_CHART -> it.copy(description = "Struktur divisi, jenjang jabatan, dan data karyawan pabrik.")
                GarmentModules.VENDOR_CONTACTS -> it.copy(displayName = "Kontak Vendor & Makloon", description = "Buku kontak vendor subkon, daftar harga layanan per vendor, dan penunjukan vendor ke proses Vendor Luar.")
                else -> it
            }
        })
        assertEquals(emptyList(), DiscoveryDraftValidator.validate(DiscoveryDraft(old, GarmentBlueprints.FOB_FULL_PACKAGE)))
    }

    @Test
    fun `semua modul yang dibagi lewat salinan identik tetap berdefinisi sama dan tanpa slot`() {
        sharedByCopy.forEach { id -> assertNull(garment.module(id)!!.slot, id.value) }
        assertEquals(emptyList(), DomainPackRegistry.violations(InterviewFixtures.klinikPack))
    }
}

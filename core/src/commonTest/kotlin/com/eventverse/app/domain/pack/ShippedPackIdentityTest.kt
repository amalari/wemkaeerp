package com.eventverse.app.domain.pack

import com.eventverse.app.domain.discovery.DiscoveryDraft
import com.eventverse.app.domain.discovery.DiscoveryDraftValidator
import com.eventverse.app.shared.discovery.DiscoveryDraftCodec
import com.eventverse.app.shared.json.JsonValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Regresi B2/B5: draf garment yang tersimpan sebelum kolom aditif ada tetap sah; penulisan ulang tetap ditolak. */
class ShippedPackIdentityTest {

    private val garment = GarmentDomainPack.pack
    private fun draftWith(pack: DomainPack) = DiscoveryDraft(pack, GarmentBlueprints.FOB_FULL_PACKAGE)

    private fun oldStoredDraft(vararg dropped: String): DiscoveryDraft {
        val root = DiscoveryDraftCodec.encode(draftWith(garment))
        val pack = root.entries["pack"] as JsonValue.Obj
        return DiscoveryDraftCodec.decode(JsonValue.Obj(root.entries + ("pack" to JsonValue.Obj(pack.entries - dropped.toSet()))).encode())
    }

    @Test
    fun `draf garment lama tanpa kolom aditif tetap sah`() {
        assertEquals(emptyList(), DiscoveryDraftValidator.validate(oldStoredDraft("roleHints", "reservedTerms", "sharedModules")))
        assertEquals(emptyList(), DiscoveryDraftValidator.validate(oldStoredDraft("roleHints")))
        assertEquals(emptyList(), DiscoveryDraftValidator.validate(draftWith(garment)))
    }

    @Test
    fun `kolom aditif yang berbeda dari pack bawaan tetap ditolak sebagai penulisan ulang`() {
        val tampered = garment.copy(roleHints = garment.roleHints.take(2))
        assertTrue(DiscoveryDraftValidator.validate(draftWith(tampered)).any { it.path == "$.pack" })
        val fakeTerms = garment.copy(reservedTerms = listOf("karangan"))
        assertTrue(DiscoveryDraftValidator.validate(draftWith(fakeTerms)).any { it.path == "$.pack" })
    }

    @Test
    fun `kolom non-aditif tetap harus identik`() {
        val extra = garment.copy(portLabels = garment.portLabels + ("ProductionOrderDraft" to "Label Lain"))
        assertTrue(DiscoveryDraftValidator.validate(draftWith(extra)).any { it.path == "$.pack" })
        assertTrue(garment.matchesShipped(garment))
    }

    @Test
    fun `modul bersama garment hanya costing_hpp dan round-trip codec`() {
        assertEquals(setOf(GarmentModules.COSTING_HPP), garment.sharedModules)
        assertEquals(garment, com.eventverse.app.shared.pack.DomainPackCodec.decode(com.eventverse.app.shared.pack.DomainPackCodec.encodeToString(garment)))
    }

    @Test
    fun `draf garment lama dengan deskripsi invoicing yang sudah diganti tetap sah, deskripsi lain tidak`() {
        val oldText = "Penerbitan faktur tagihan sample, termin DP, dan pelunasan garmen berkanvas."
        fun withInvoicingText(text: String) = garment.copy(modules = garment.modules.map { if (it.id == GarmentModules.INVOICING) it.copy(description = text) else it })
        assertEquals(emptyList(), DiscoveryDraftValidator.validate(draftWith(withInvoicingText(oldText))))
        assertTrue(DiscoveryDraftValidator.validate(draftWith(withInvoicingText("Teks karangan"))).any { it.path == "$.pack" })
        assertTrue(garment.module(GarmentModules.INVOICING)!!.description != oldText)
    }
}

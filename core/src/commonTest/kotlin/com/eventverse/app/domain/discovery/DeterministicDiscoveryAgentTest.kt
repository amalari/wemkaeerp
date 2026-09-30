package com.eventverse.app.domain.discovery

import com.eventverse.app.domain.pack.GarmentBlueprints
import com.eventverse.app.domain.pack.GarmentDomainPack
import com.eventverse.app.shared.discovery.DiscoveryDraftCodec
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Plan §2 A1/A3: narasi emas → draf sah tanpa jaringan. Termasuk **tenant kedua** (Kontrak 6): narasi klinik
 * menghasilkan pack berprefiks, bukan kosakata garment.
 */
class DeterministicDiscoveryAgentTest {

    private val agent = DeterministicDiscoveryAgent()

    @Test
    fun `narasi garment menghasilkan pack bawaan dan starter yang cocok`() = runTest {
        val cmt = agent.draft(DiscoveryRequest("Kami konveksi makloon, kain dari buyer, kami cukup jahit.")).getOrThrow()
        assertEquals(GarmentDomainPack.pack, cmt.pack)
        assertEquals(GarmentBlueprints.CMT_MAKLOON, cmt.blueprint)

        val fob = agent.draft(DiscoveryRequest("Pabrik konveksi ekspor, kami urus kain sampai kirim.")).getOrThrow()
        assertEquals(GarmentBlueprints.FOB_FULL_PACKAGE, fob.blueprint)
    }

    @Test
    fun `narasi klinik menghasilkan pack berprefiks yang sah tanpa jaringan`() = runTest {
        val draft = agent.draft(
            DiscoveryRequest(
                "Kami klinik gigi: pasien mendaftar antrean per poli, ada stok obat, dan tagihan pembayaran kasir.",
                industryHint = "Klinik Gigi"
            )
        ).getOrThrow()

        assertEquals("klinik_gigi", draft.pack.code.value)
        assertTrue(draft.pack.modules.map { it.id.value }.all { it.startsWith("klinik_gigi_") })
        assertTrue(draft.pack.slots.map { it.code.value }.all { it.startsWith("klinik_gigi_") })
        assertEquals(DiscoveryDraftValidator.validate(draft), emptyList())
        // Blueprint hanya menyebut modul pack-nya sendiri.
        val ids = draft.pack.modules.map { it.id.value }.toSet()
        assertTrue(draft.blueprint.modules.all { it.moduleCode in ids })
    }

    @Test
    fun `round trip codec identik untuk kedua jalur`() = runTest {
        val garment = agent.draft(DiscoveryRequest("Konveksi brand sendiri untuk distro retail.")).getOrThrow()
        val klinik = agent.draft(DiscoveryRequest("Klinik dengan jadwal dokter dan tagihan.", industryHint = "klinik")).getOrThrow()

        for (draft in listOf(garment, klinik)) {
            val decoded = DiscoveryDraftCodec.decode(DiscoveryDraftCodec.encodeToString(draft))
            assertEquals(draft, decoded)
        }
    }

    @Test
    fun `kode pack dari narasi tanpa petunjuk tetap sah dan bukan garment`() = runTest {
        val draft = agent.draft(DiscoveryRequest("Usaha jasa bengkel servis motor dengan pesanan booking.")).getOrThrow()
        assertEquals("bengkel", draft.pack.code.value)
    }
}

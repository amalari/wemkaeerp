package com.eventverse.app.routes

import com.eventverse.app.domain.auth.UserId
import com.eventverse.app.domain.discovery.DeterministicDiscoveryAgent
import com.eventverse.app.domain.discovery.DiscoveryDraftId
import com.eventverse.app.domain.discovery.DiscoveryRequest
import com.eventverse.app.domain.discovery.PrototypeScreen
import com.eventverse.app.domain.discovery.StoredDiscoveryDraft
import com.eventverse.app.shared.json.JsonValue
import com.eventverse.app.shared.pack.InteractiveScreenCodec
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Ringkasan draf untuk klien harus membawa proposal (SP-B/C): tanpa ini, klien menggambar penanda generik
 * "contoh 1" walau draf tersimpan benar. Tenant non-garment (klinik) — bukan data konveksi.
 */
class DiscoverySummaryProposalTest {

    private fun stored(narrative: String, hint: String) = runBlocking {
        val draft = DeterministicDiscoveryAgent().draft(DiscoveryRequest(narrative, hint)).getOrThrow()
        StoredDiscoveryDraft(DiscoveryDraftId("d-1"), UserId("u-1"), draft)
    }

    private fun screens(summary: JsonValue.Obj) = summary.objectArray("screens")

    @Test
    fun `layar berproposal membawa alasan sumber proposal dan versi bisa dimainkan`() {
        val s = screens(summaryObj(stored("Klinik gigi: antrean per poli, stok obat, tagihan, laporan harian.", "klinik")))
        assertTrue(s.isNotEmpty())
        s.forEach { screen ->
            assertTrue(screen.string("rationale").orEmpty().startsWith("Dipilih karena"), screen.string("screenId"))
            assertEquals("DETERMINISTIC", screen.obj("source")?.string("kind"))
            assertNotNull(screen.obj("proposal"))
        }
        val antrean = s.first { it.string("moduleId") == "klinik_antrean" }
        assertEquals("KANBAN", antrean.string("widget"))
        assertNotNull(antrean.obj("interactive"), "papan klinik harus bisa dimainkan dari proposal")
        assertEquals(listOf("Contoh Kunjungan 1", "Contoh Kunjungan 2", "Contoh Kunjungan 3"), antrean.objectArray("sampleRows").map { it.string("judul") })
        // bukan penanda generik lama
        assertTrue(s.none { row -> row.objectArray("sampleRows").any { it.entries.values.any { v -> (v as? JsonValue.Str)?.value?.contains("contoh 1") == true } } })
    }

    @Test
    fun `dasbor klinik bisa dimainkan dan kolom papan berasal dari status slot`() {
        val s = screens(summaryObj(stored("Klinik: antrean, tagihan, laporan.", "klinik")))
        assertNotNull(s.first { it.string("moduleId") == "klinik_laporan" }.obj("interactive"))
        val board = InteractiveScreenCodec.decode(s.first { it.string("moduleId") == "klinik_antrean" }.obj("interactive")!!)
        assertEquals(listOf("Menunggu", "Dikerjakan", "Selesai"), board.spec.screens.single().kanban?.columns)
    }

    @Test
    fun `layar tanpa proposal tetap lewat jalur lama tanpa kunci baru`() {
        val base = stored("Klinik: antrean.", "klinik")
        val legacy = base.copy(draft = base.draft.copy(screens = listOf(PrototypeScreen("s1", base.draft.pack.modules.first().id, "Antrean", "KANBAN"))))
        val screen = screens(summaryObj(legacy)).single()
        assertTrue(!screen.has("proposal") && !screen.has("rationale") && !screen.has("source"))
        assertTrue(screen.objectArray("sampleRows").isNotEmpty(), "penanda struktural lama tetap ada")
    }
}

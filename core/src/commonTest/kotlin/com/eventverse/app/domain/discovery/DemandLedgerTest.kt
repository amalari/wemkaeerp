package com.eventverse.app.domain.discovery

import com.eventverse.app.domain.auth.UserId
import com.eventverse.app.domain.discovery.DiscoveryDraftId
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Buku demand (plan §6 E2/E3): penyaringan istilah yang belum terwakili pack, dan gerbang
 * Rule of Three — widget/modul baru hanya layak bila ≥ 3 demand **berbeda** menuntut hal sama.
 */
class DemandLedgerTest {

    private val agent = DeterministicDiscoveryAgent()

    private fun klinikDraft(narrative: String) = kotlinx.coroutines.runBlocking {
        agent.draft(DiscoveryRequest(narrative, industryHint = "klinik")).getOrThrow()
    }

    private fun demand(id: String, terms: List<String>, narrative: String = "narasi $id") = DiscoveryDemand(
        id = id, draftId = DiscoveryDraftId("draft-$id"), ownerUserId = UserId("u-1"),
        narrative = narrative, industryHint = null, agentRef = "uji/1",
        matchedModuleIds = emptyList(), unmatchedTerms = terms
    )

    @Test
    fun `istilah yang sudah jadi modul tidak kembali sebagai sinyal`() {
        val draft = klinikDraft("Kami klinik gigi dengan antrean pasien per poli dan tagihan.")
        val terms = DemandLedger.unmatchedTerms(
            "Kami klinik gigi dengan antrean pasien per poli dan tagihan.", draft
        )
        // "antrean" & "tagihan" sudah menjadi nama modul; "klinik" adalah kode pack.
        assertTrue("antrean" !in terms, "kata yang sudah jadi modul tidak boleh unmatched: $terms")
        assertTrue("tagihan" !in terms, "kata yang sudah jadi modul tidak boleh unmatched: $terms")
        assertTrue("klinik" !in terms, "kode pack bukan sinyal: $terms")
        // "gigi" tidak punya modul — inilah demand yang layak dicatat.
        assertTrue("gigi" in terms, "istilah tanpa modul harus tercatat: $terms")
        // Kata fungsi dibuang.
        assertTrue("dengan" !in terms && "kami" !in terms, "stopword bocor: $terms")
    }

    @Test
    fun `kandidat menuntut tiga demand berbeda dan menghitung demand sekali`() {
        val tiga = listOf(
            demand("d1", listOf("gigi", "rekam")),
            demand("d2", listOf("gigi")),
            demand("d3", listOf("gigi", "gigi", "rekam")) // istilah dobel di satu demand = sekali
        )
        val result = DemandLedger.candidates(tiga)
        // "rekam" hanya di 2 demand → di bawah ambang; "gigi" saja yang lolos.
        assertEquals(1, result.size, "hanya gigi lolos ambang: $result")
        val gigi = result.single()
        assertEquals("gigi", gigi.term)
        assertEquals(3, gigi.demandCount)
        assertTrue(gigi.samples.isNotEmpty() && gigi.samples.first().contains("narasi d1"))

        // Dua demand saja → di bawah ambang, bukan kandidat.
        assertTrue(DemandLedger.candidates(tiga.take(2)).isEmpty())
    }

    @Test
    fun `kandidat diurutkan dari yang paling sering dan batas aman narasi kosong`() {
        val result = DemandLedger.candidates(
            listOf(
                demand("a", listOf("gigi")),
                demand("b", listOf("gigi")),
                demand("c", listOf("gigi")),
                demand("d", listOf("poli")), demand("e", listOf("poli")), demand("f", listOf("poli"))
            )
        )
        assertEquals("gigi", result.first().term)
        assertEquals(2, result.size)

        assertTrue(DemandLedger.candidates(emptyList()).isEmpty())
    }
}

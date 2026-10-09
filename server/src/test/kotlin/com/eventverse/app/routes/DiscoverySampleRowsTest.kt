package com.eventverse.app.routes

import com.eventverse.app.domain.auth.UserId
import com.eventverse.app.domain.discovery.DeterministicDiscoveryAgent
import com.eventverse.app.domain.discovery.DiscoveryDraft
import com.eventverse.app.domain.discovery.DiscoveryDraftId
import com.eventverse.app.domain.discovery.DiscoveryRequest
import com.eventverse.app.domain.discovery.PrototypeScreen
import com.eventverse.app.domain.discovery.StoredDiscoveryDraft
import com.eventverse.app.domain.discovery.WidgetKind
import com.eventverse.app.domain.discovery.proposal.ProposalSource
import com.eventverse.app.domain.discovery.proposal.ScreenProposal
import com.eventverse.app.domain.discovery.proposal.SkeletonBlock
import com.eventverse.app.domain.discovery.proposal.SkeletonHint
import com.eventverse.app.domain.discovery.proposal.SkeletonWidth
import com.eventverse.app.domain.discovery.proposal.ViewProposal
import com.eventverse.app.shared.json.JsonValue
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Kompatibilitas Irisan 3b (C10): `CUSTOM_SCREEN` ber-proposal tanpa blok digambar sama dengan jalur lama (tiga
 * kotak generik), bukan kartu "belum lengkap". Murni (tanpa DB); pack klinik dan bordir = non-garment.
 */
class DiscoverySampleRowsTest {

    private fun draft(hint: String, narrative: String): DiscoveryDraft = runBlocking {
        DeterministicDiscoveryAgent().draft(DiscoveryRequest(narrative, hint)).getOrThrow()
    }

    private fun proposal(d: DiscoveryDraft, widget: WidgetKind, view: ViewProposal, seed: List<Map<String, String>> = emptyList()) =
        ScreenProposal("uji", d.pack.modules.first().id, "Uji", widget, "Dipilih karena uji.", null, view, seed)

    private fun send(d: DiscoveryDraft, widget: WidgetKind, p: ScreenProposal?): List<JsonValue.Obj> {
        val screen = PrototypeScreen("uji", d.pack.modules.first().id, "Uji", widget.code, p, p?.let { ProposalSource.Agent("uji") })
        val stored = StoredDiscoveryDraft(DiscoveryDraftId("d-uji"), UserId("u-uji"), d.copy(screens = listOf(screen)))
        return summaryObj(stored).objectArray("screens").single().objectArray("sampleRows")
    }

    private fun assertGeneric(rows: List<JsonValue.Obj>, moduleName: String) {
        assertEquals(3, rows.size)
        assertEquals(listOf("penuh", "separuh", "separuh"), rows.map { it.string("Lebar") })
        assertTrue(rows.all { !it.has("Petunjuk") })
        assertTrue(rows.first().string("Blok").orEmpty().contains(moduleName))
    }

    @Test
    fun `custom screen berproposal tanpa blok dan seed kosong jatuh ke tiga kotak generik`() {
        val d = draft("klinik", "Klinik gigi: antrean pasien.")
        val rows = send(d, WidgetKind.CUSTOM_SCREEN, proposal(d, WidgetKind.CUSTOM_SCREEN, ViewProposal.None))
        assertGeneric(rows, d.pack.modules.first().displayName)
    }

    @Test
    fun `custom screen berproposal tanpa blok pada pack bordir tetap tiga kotak generik`() {
        val d = draft("bordir", "Usaha bordir komputer: pesanan, digitizing, produksi.")
        val rows = send(d, WidgetKind.CUSTOM_SCREEN, proposal(d, WidgetKind.CUSTOM_SCREEN, ViewProposal.None))
        assertGeneric(rows, d.pack.modules.first().displayName)
    }

    @Test
    fun `custom screen tanpa proposal tetap tiga kotak generik jalur lama`() {
        val d = draft("klinik", "Klinik gigi: antrean pasien.")
        assertGeneric(send(d, WidgetKind.CUSTOM_SCREEN, null), d.pack.modules.first().displayName)
    }

    @Test
    fun `custom screen berskeleton mengirim baris blok berpetunjuk`() {
        val d = draft("klinik", "Klinik gigi: antrean pasien.")
        val view = ViewProposal.Skeleton(listOf(SkeletonBlock("Keranjang", SkeletonWidth.FULL, SkeletonHint.TABLE)))
        val rows = send(d, WidgetKind.CUSTOM_SCREEN, proposal(d, WidgetKind.CUSTOM_SCREEN, view))
        assertEquals(listOf("Keranjang"), rows.map { it.string("Blok") })
        assertEquals(listOf("tabel"), rows.map { it.string("Petunjuk") })
    }

    @Test
    fun `widget non custom screen berproposal dengan seed kosong tetap kosong`() {
        val d = draft("klinik", "Klinik gigi: antrean pasien.")
        assertEquals(emptyList(), send(d, WidgetKind.TABLE, proposal(d, WidgetKind.TABLE, ViewProposal.None)))
    }

    @Test
    fun `custom screen tanpa blok dengan seed tak kosong mengirim seed apa adanya`() {
        val d = draft("klinik", "Klinik gigi: antrean pasien.")
        val seed = listOf(mapOf("judul" to "Satu"), mapOf("judul" to "Dua"))
        val rows = send(d, WidgetKind.CUSTOM_SCREEN, proposal(d, WidgetKind.CUSTOM_SCREEN, ViewProposal.None, seed))
        assertEquals(listOf("Satu", "Dua"), rows.map { it.string("judul") })
    }
}

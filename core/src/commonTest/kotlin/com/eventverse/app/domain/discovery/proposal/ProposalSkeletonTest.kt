package com.eventverse.app.domain.discovery.proposal

import com.eventverse.app.domain.discovery.DiscoveryDraft
import com.eventverse.app.domain.discovery.PrototypeScreen
import com.eventverse.app.domain.discovery.WidgetKind
import com.eventverse.app.domain.discovery.WidgetRegistry
import com.eventverse.app.domain.discovery.proposal.ScreenProposalFixtures.draft
import com.eventverse.app.domain.discovery.proposal.ScreenProposalFixtures.layarKustom
import com.eventverse.app.domain.discovery.proposal.ScreenProposalFixtures.screenOf
import com.eventverse.app.domain.discovery.proposal.ScreenProposalFixtures.tabelTagihan
import com.eventverse.app.shared.discovery.DiscoveryDraftCodec
import com.eventverse.app.shared.discovery.DiscoveryDraftDecodeException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Kerangka CUSTOM_SCREEN (Irisan 3b): validator, kemurnian, codec, dan sampel — di pack klinik (non-garment). */
class ProposalSkeletonTest {

    private fun kerangka(vararg blocks: SkeletonBlock) = layarKustom().copy(view = ViewProposal.Skeleton(blocks.toList()))

    private val keranjang = SkeletonBlock("Keranjang", SkeletonWidth.FULL, SkeletonHint.TABLE)
    private val bayar = SkeletonBlock("Pembayaran", SkeletonWidth.HALF, SkeletonHint.FORM)
    private val aksi = SkeletonBlock("Tombol kasir", SkeletonWidth.HALF, SkeletonHint.ACTIONS)

    private fun issues(p: ScreenProposal, purity: Boolean = false) = ScreenProposalValidator.validate(p, verticalPurity = purity)

    private fun failurePath(raw: String) = assertFailsWith<DiscoveryDraftDecodeException> { DiscoveryDraftCodec.decode(raw) }.path

    private fun roundTrip(d: DiscoveryDraft) = DiscoveryDraftCodec.decode(DiscoveryDraftCodec.encodeToString(d))

    // --- validator ------------------------------------------------------------------------------
    @Test
    fun `validate custom screen when skeleton sah should lolos dan draf lama none tetap lolos`() {
        assertEquals(emptyList(), issues(kerangka(keranjang, bayar, aksi)))
        assertEquals(emptyList(), issues(layarKustom()))
    }

    @Test
    fun `validate skeleton when dipakai pada widget non custom screen should ditolak`() {
        val p = tabelTagihan().copy(view = ViewProposal.Skeleton(listOf(keranjang)))
        assertTrue(issues(p).any { it.path == "$.view" && it.message.contains("cocok") }, "${issues(p)}")
        val salah = layarKustom().copy(view = ViewProposal.Print(emptyList()))
        assertTrue(issues(salah).any { it.path == "$.view" && it.message.contains("None atau Skeleton") })
    }

    @Test
    fun `validate skeleton when blok kosong atau melebihi batas should ditolak`() {
        assertTrue(issues(kerangka()).any { it.path == "$.view.blocks" && it.message.contains("minimal 1") })
        val banyak = (1..ProposalLimits.BLOCKS + 1).map { SkeletonBlock("Blok $it") }
        assertTrue(issues(kerangka(*banyak.toTypedArray())).any { it.path == "$.view.blocks" && it.message.contains("maksimum") })
        val pas = (1..ProposalLimits.BLOCKS).map { SkeletonBlock("Blok $it") }
        assertEquals(emptyList(), issues(kerangka(*pas.toTypedArray())))
    }

    @Test
    fun `validate skeleton when label kosong terlalu panjang atau kembar should ditolak`() {
        assertTrue(issues(kerangka(SkeletonBlock(" "))).any { it.path == "$.view.blocks[0].label" })
        val panjang = SkeletonBlock("x".repeat(ProposalLimits.TEXT + 1))
        assertTrue(issues(kerangka(keranjang, panjang)).any { it.path == "$.view.blocks[1].label" })
        assertTrue(issues(kerangka(keranjang, keranjang.copy(label = " keranjang "))).any { it.message.contains("kembar") })
    }

    @Test
    fun `validate skeleton when label memuat istilah konveksi pada pack non garment should ditolak`() {
        val p = kerangka(keranjang, SkeletonBlock("Daftar SPK"))
        assertEquals(emptyList(), issues(p, purity = false))
        assertTrue(issues(p, purity = true).any { it.path == "$.view.blocks[1].label" && it.message.contains("konveksi") })
    }

    // --- codec ----------------------------------------------------------------------------------
    @Test
    fun `codec skeleton when round trip lewat draf should identik`() {
        val d = draft(screenOf(kerangka(keranjang, bayar, aksi, SkeletonBlock("Angka", hint = SkeletonHint.METRIC_CARDS))))
        assertEquals(d, roundTrip(d))
    }

    @Test
    fun `codec custom screen when tanpa kunci view atau view null should menjadi none`() {
        val d = draft(screenOf(layarKustom()))
        val json = DiscoveryDraftCodec.encodeToString(d)
        assertTrue(json.contains("\"view\":null"), json)
        assertEquals(ViewProposal.None, roundTrip(d).screens.single().proposal?.view)
        val tanpaKunci = json.replace("\"view\":null,", "")
        assertTrue(!tanpaKunci.contains("\"view\""), tanpaKunci)
        assertEquals(ViewProposal.None, DiscoveryDraftCodec.decode(tanpaKunci).screens.single().proposal?.view)
    }

    @Test
    fun `codec skeleton when hint atau width tak dikenal should ditolak dengan path`() {
        val raw = DiscoveryDraftCodec.encodeToString(draft(screenOf(kerangka(keranjang, bayar))))
        assertEquals("$.screens[0].proposal.view.blocks[0].hint", failurePath(raw.replace("\"hint\":\"TABLE\"", "\"hint\":\"GRAFIK\"")))
        assertEquals("$.screens[0].proposal.view.blocks[1].width", failurePath(raw.replace("\"width\":\"HALF\"", "\"width\":\"SETENGAH\"")))
        assertEquals("$.screens[0].proposal.view.blocks[0].label", failurePath(raw.replace("\"label\":\"Keranjang\"", "\"label\":7")))
    }

    @Test
    fun `codec skeleton when blocks hilang should ditolak`() {
        val raw = DiscoveryDraftCodec.encodeToString(draft(screenOf(layarKustom()))).replace("\"view\":null", "\"view\":{}")
        assertEquals("$.screens[0].proposal.view.blocks", failurePath(raw))
    }

    // --- sampel ---------------------------------------------------------------------------------
    @Test
    fun `sampleRowsFor when custom screen punya skeleton should memakai blok sendiri`() {
        val d = draft(screenOf(kerangka(keranjang, bayar, aksi)))
        val rows = WidgetRegistry.sampleRowsFor(d.screens.single(), d.pack)
        assertEquals(listOf("Keranjang", "Pembayaran", "Tombol kasir"), rows.map { it["Blok"] })
        assertEquals(listOf("penuh", "separuh", "separuh"), rows.map { it["Lebar"] })
        assertEquals(listOf("tabel", "formulir", "aksi"), rows.map { it["Petunjuk"] })
    }

    @Test
    fun `sampleRowsFor when custom screen tanpa skeleton should tetap tiga blok generik`() {
        val d = draft(screenOf(layarKustom()))
        val none = WidgetRegistry.sampleRowsFor(d.screens.single(), d.pack)
        val tanpaProposal = WidgetRegistry.sampleRowsFor(
            PrototypeScreen("s", ScreenProposalFixtures.moduleId, "Kustom", WidgetKind.CUSTOM_SCREEN.code), d.pack
        )
        assertEquals(3, none.size)
        assertEquals(none, tanpaProposal)
        assertEquals(listOf("penuh", "separuh", "separuh"), none.map { it["Lebar"] })
    }

    @Test
    fun `toInteractiveScreen when custom screen punya skeleton should tetap gagal tanpa bentuk interaktif`() {
        val e = kerangka(keranjang).toInteractiveScreen().exceptionOrNull() as ProposalConversionException
        assertTrue(e.message.orEmpty().contains("tidak punya bentuk interaktif"), e.message)
    }
}

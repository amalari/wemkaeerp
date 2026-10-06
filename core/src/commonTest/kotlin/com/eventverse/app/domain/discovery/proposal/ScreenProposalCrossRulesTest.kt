package com.eventverse.app.domain.discovery.proposal

import com.eventverse.app.domain.discovery.DiscoveryDraft
import com.eventverse.app.domain.discovery.DiscoveryDraftValidator
import com.eventverse.app.domain.discovery.PrototypeScreen
import com.eventverse.app.domain.discovery.WidgetKind
import com.eventverse.app.domain.discovery.proposal.ScreenProposalFixtures.draft
import com.eventverse.app.domain.discovery.proposal.ScreenProposalFixtures.kanbanAntrean
import com.eventverse.app.domain.discovery.proposal.ScreenProposalFixtures.screenOf
import com.eventverse.app.domain.discovery.proposal.ScreenProposalFixtures.tabelTagihan
import com.eventverse.app.domain.pack.GarmentBlueprints
import com.eventverse.app.domain.pack.GarmentDomainPack
import com.eventverse.app.domain.prototype.CardElement
import com.eventverse.app.domain.prototype.FieldType
import com.eventverse.app.domain.prototype.TileSpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** B4 — kemurnian vertikal, batas ukuran, seed vs skema, dan konsistensi lintas-layar. Pack non-garment di setiap kasus. */
class ScreenProposalCrossRulesTest {

    private fun purity(p: ScreenProposal) = ScreenProposalValidator.validate(p, verticalPurity = true)
    private fun assertAt(path: String, issues: List<ProposalIssue>, contains: String? = null) =
        assertTrue(issues.any { it.path == path && (contains == null || it.message.contains(contains)) }, "tak ada $path${contains?.let { " '$it'" }.orEmpty()}: $issues")

    // --- kemurnian vertikal ---------------------------------------------------------------------
    @Test
    fun `fixture klinik bersih dari istilah konveksi`() {
        ScreenProposalFixtures.semua().forEach { assertEquals(emptyList(), purity(it), it.screenId) }
    }

    @Test
    fun `istilah konveksi di setiap bagian teks ditolak dengan path bagian itu`() {
        val k = kanbanAntrean()
        assertAt("$.title", purity(k.copy(title = "Antrean jahit")), "jahit")
        assertAt("$.rationale", purity(k.copy(rationale = "Dipilih karena jahit berjalan bertahap.")), "jahit")
        assertAt("$.entity.label", purity(k.copy(entity = k.entity?.copy(label = "SPK"))), "SPK")
        val labelField = k.copy(entity = k.entity?.copy(fields = k.entity!!.fields.mapIndexed { i, f -> if (i == 1) f.copy(label = "Buyer") else f }))
        assertAt("$.entity.fields[1].label", purity(labelField), "Buyer")
        val option = k.copy(entity = k.entity?.copy(fields = k.entity!!.fields.map { f -> if (f.key == "status") f.copy(options = listOf("Menunggu", "Diperiksa", "Buyer")) else f }, transitions = emptyMap()))
        assertAt("$.entity.fields[4].options[2]", purity(option), "Buyer")
        assertAt("$.seed[0].keluhan", purity(k.copy(seed = listOf(mapOf("nama" to "A", "keluhan" to "pesan 100 pcs", "status" to "Menunggu")))), "pcs")
        val tile = ScreenProposalFixtures.dasborHarian().copy(view = ViewProposal.Dashboard(listOf(TileSpec("Order PO aktif", value = "2"))))
        assertAt("$.view.tiles[0].label", purity(tile), "PO")
        val submit = ScreenProposalFixtures.formPendaftaran().copy(view = ViewProposal.Form(listOf("nama", "keluhan"), "Simpan BOM"))
        assertAt("$.view.submitLabel", purity(submit), "BOM")
    }

    @Test
    fun `pencocokan per kata utuh dan tak peka huruf besar`() {
        assertEquals("Jahit", VerticalPurity.leak("Stok Jahit rol"))
        assertEquals("po", VerticalPurity.leak("nomor po 12"))
        assertNull(VerticalPurity.leak("Poli gigi, polimer, tempo, jahitnya"))   // 'jahitnya' bukan kata 'jahit'
        assertNull(VerticalPurity.leak("Pasien poliklinik antre"))
    }

    @Test
    fun `istilah tekstil umum bukan kebocoran karena sablon dan bordir tidak punya pack baku`() {
        listOf("sablon", "bordir", "kain", "tekstil", "potong").forEach { assertNull(VerticalPurity.leak("Order $it harian"), it) }
    }

    @Test
    fun `tanpa flag kemurnian garment bebas memakai kosakatanya`() {
        assertEquals(emptyList(), ScreenProposalValidator.validate(kanbanAntrean().copy(title = "Papan SPK jahit")))
    }

    @Test
    fun `validator draf menegakkan kemurnian untuk pack non-garment dengan path layar`() {
        val bocor = kanbanAntrean().copy(title = "Papan jahit")
        val issues = DiscoveryDraftValidator.validate(draft(screenOf(bocor)))
        assertEquals(listOf("$.screens[0].proposal.title"), issues.map { it.path })
    }

    // --- batas ukuran ---------------------------------------------------------------------------
    @Test
    fun `elemen kartu dan layar per draf berbatas`() {
        val kartu = kanbanAntrean().copy(view = ViewProposal.Kanban(card = List(13) { CardElement("nama") }))
        assertAt("$.view.card", ScreenProposalValidator.validate(kartu), "maksimum 12")
        val many = (1..41).map { screenOf(kanbanAntrean().copy(screenId = "s$it")) }
        val issues = DiscoveryDraftValidator.validate(draft(*many.toTypedArray()))
        assertTrue(issues.any { it.path == "$.screens" && it.message.contains("maksimum 40") }, "$issues")
    }

    // --- seed vs skema --------------------------------------------------------------------------
    @Test
    fun `kartu papan tanpa status tidak punya kolom sehingga ditolak`() {
        val p = kanbanAntrean().copy(seed = listOf(mapOf("nama" to "A")))
        assertAt("$.seed[0].status", ScreenProposalValidator.validate(p), "wajib punya 'status'")
    }

    // --- konsistensi lintas-layar ---------------------------------------------------------------
    private fun located(vararg p: ScreenProposal) = ScreenProposalValidator.validateAll(p.toList(), basePath = "$.p")

    @Test
    fun `entity sama dengan field identik atau himpunan bagian lolos`() {
        assertEquals(emptyList(), located(kanbanAntrean(), ScreenProposalFixtures.formPendaftaran().copy(
            entity = ScreenProposalFixtures.pasien.copy(fields = ScreenProposalFixtures.pasien.fields.take(2), statusField = null, transitions = emptyMap()),
            view = ViewProposal.Form(listOf("nama", "keluhan"))
        )))
    }

    @Test
    fun `entity sama dengan definisi field berbeda ditolak menyebut layar acuan`() {
        val b = ScreenProposalFixtures.formPendaftaran().let { f ->
            f.copy(entity = f.entity?.copy(fields = f.entity!!.fields.map { if (it.key == "keluhan") it.copy(type = FieldType.NUMBER) else it }))
        }
        val issues = located(kanbanAntrean(), b)
        assertAt("$.p[1].entity.fields[1]", issues, "$.p[0]")
        assertAt("$.p[1].entity.fields[1]", issues, "NUMBER")
        // `required` bukan identitas: formulir boleh mewajibkan field yang di layar lain opsional.
        val lebihKetat = ScreenProposalFixtures.formPendaftaran().copy(
            entity = ScreenProposalFixtures.pasien.copy(fields = ScreenProposalFixtures.pasien.fields.map { if (it.key == "keluhan") it.copy(required = true) else it }, statusField = null, transitions = emptyMap()),
            view = ViewProposal.Form(listOf("nama", "keluhan"))
        )
        assertEquals(emptyList(), located(kanbanAntrean(), lebihKetat))
    }

    @Test
    fun `statusField dan transisi harus sama bila keduanya menyebutnya`() {
        val lain = kanbanAntrean().copy(screenId = "lain", entity = ScreenProposalFixtures.pasien.copy(transitions = mapOf("Menunggu" to listOf("Selesai"))))
        assertAt("$.p[1].entity.transitions", located(kanbanAntrean(), lain), "transitions")
        val tanpa = kanbanAntrean().copy(screenId = "lain2", entity = ScreenProposalFixtures.pasien.copy(statusField = "keluhan", transitions = emptyMap()))
        assertAt("$.p[1].entity.statusField", located(kanbanAntrean(), tanpa), "statusField")
    }

    @Test
    fun `screenId kembar ditolak di himpunan usulan dan di draf`() {
        assertAt("$.p[1].screenId", located(kanbanAntrean(), tabelTagihan().copy(screenId = kanbanAntrean().screenId)), "unik")
        val dup = PrototypeScreen("sama", ScreenProposalFixtures.moduleId, "A", "TABLE")
        val d = draft(dup, dup.copy(title = "B"))
        assertEquals(listOf("$.screens[1].screenId"), DiscoveryDraftValidator.validate(d).map { it.path })
    }

    @Test
    fun `validator draf menandai layar yang menyimpang dari entity acuan`() {
        val b = ScreenProposalFixtures.formPendaftaran().let { f ->
            f.copy(entity = f.entity?.copy(fields = f.entity!!.fields.map { if (it.key == "keluhan") it.copy(label = "Gejala") else it }))
        }
        val issues = DiscoveryDraftValidator.validate(draft(screenOf(kanbanAntrean()), screenOf(b)))
        assertEquals(listOf("$.screens[1].proposal.entity.fields[1]"), issues.map { it.path })
    }

    // --- acuan garment: aturan baru tidak boleh menolak pack yang ditulis manusia ---------------
    @Test
    fun `usulan pack garment lolos seluruh aturan termasuk lintas-layar`() {
        val proposals = PackScreenProposer.proposalsForAll(GarmentDomainPack.pack).getOrThrow()
        val screens = proposals.map { PrototypeScreen(it.screenId, it.moduleId, it.title, it.widget.code, it, ProposalSource.Pack) }
        val d = DiscoveryDraft(GarmentDomainPack.pack, GarmentBlueprints.FOB_FULL_PACKAGE, screens)
        assertEquals(emptyList(), DiscoveryDraftValidator.validate(d))
    }

    // --- mutu pesan galat (dikembalikan ke LLM) -------------------------------------------------
    @Test
    fun `setiap pesan galat jelas berpath dan tidak bocor jejak teknis`() {
        val k = kanbanAntrean()
        val bad = listOf(
            k.copy(rationale = ""), k.copy(widget = WidgetKind.TABLE), k.copy(entity = null),
            k.copy(seed = listOf(mapOf("nama" to "A", "status" to "Pulang", "tanggal_kunjungan" to "kemarin"))),
            k.copy(entity = k.entity?.copy(statusField = "gaib")), k.copy(title = "Papan jahit"),
            tabelTagihan().copy(seed = listOf(mapOf("pasien" to "A", "jumlah" to "banyak")))
        )
        val issues = bad.flatMap { ScreenProposalValidator.validate(it, verticalPurity = true) }
        assertTrue(issues.size >= bad.size)
        issues.forEach {
            assertTrue(it.path.startsWith("$"), it.toString())
            assertTrue(it.message.length in 10..300, it.toString())
            assertTrue(listOf("Exception", "null", "kotlin", "NoSuchElement").none { t -> it.message.contains(t) }, it.toString())
        }
    }
}

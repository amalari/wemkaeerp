package com.eventverse.app.domain.discovery.proposal

import com.eventverse.app.domain.discovery.DeterministicDiscoveryAgent
import com.eventverse.app.domain.discovery.DiscoveryDraft
import com.eventverse.app.domain.discovery.DiscoveryDraftValidator
import com.eventverse.app.domain.discovery.DiscoveryRequest
import com.eventverse.app.domain.discovery.WidgetKind
import com.eventverse.app.domain.pack.GarmentDomainPack
import com.eventverse.app.domain.pack.ModuleSectionCode
import com.eventverse.app.domain.pack.SlotCode
import com.eventverse.app.domain.pack.SlotDefinition
import com.eventverse.app.domain.prototype.FieldType
import com.eventverse.app.shared.discovery.DiscoveryDraftCodec
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** B3 — layar deterministik dari pemetaan peran → tampilan. Semua kasus **non-garment** (Kontrak 6). */
class DeterministicScreenProposerTest {

    private val agent = DeterministicDiscoveryAgent()

    private val narratives = mapOf(
        "klinik" to "Kami klinik gigi: pendaftaran pasien, antrean per poli, stok obat, tagihan pembayaran kasir, dan laporan harian.",
        "bengkel" to "Bengkel servis motor: pesanan servis, antrean mekanik, stok suku cadang, tagihan, laporan.",
        "katering" to "Katering harian: pesanan langganan, stok bahan, tagihan pembayaran, dan laporan pengiriman bulanan.",
        "retail" to "Toko kelontong: penjualan lewat kasir, stok barang, dan rekap laporan mingguan.",
        "gudang" to "Gudang distribusi: pesanan keluar, antrean muat, stok barang, laporan."
    )

    private suspend fun draftOf(narrative: String): DiscoveryDraft = agent.draft(DiscoveryRequest(narrative)).getOrThrow()

    private fun DiscoveryDraft.widgetBySuffix(suffix: String): WidgetKind? =
        screens.firstOrNull { it.moduleId.value.endsWith("_$suffix") }?.proposal?.widget

    @Test
    fun `setiap vertikal mendapat layar valid dengan jenis sesuai peran`() = runTest {
        narratives.forEach { (name, text) ->
            val draft = draftOf(text)
            assertEquals(emptyList(), DiscoveryDraftValidator.validate(draft), name)
            assertTrue(draft.screens.isNotEmpty(), name)
            assertEquals(draft.pack.modules.size, draft.screens.size, "satu layar per modul berpendapat: $name")
            draft.widgetBySuffix("antrean")?.let { assertEquals(WidgetKind.KANBAN, it, name) }
            draft.widgetBySuffix("pesanan")?.let { assertEquals(WidgetKind.TABLE, it, name) }
            draft.widgetBySuffix("stok")?.let { assertEquals(WidgetKind.TABLE, it, name) }
            draft.widgetBySuffix("tagihan")?.let { assertEquals(WidgetKind.TABLE, it, name) }
            draft.widgetBySuffix("laporan")?.let { assertEquals(WidgetKind.DASHBOARD, it, name) }
        }
    }

    @Test
    fun `layar menyertakan source deterministik dan alasan bahasa pemilik usaha dengan istilah vertikal`() = runTest {
        val draft = draftOf(narratives.getValue("klinik"))
        assertTrue(draft.screens.all { it.source == ProposalSource.Deterministic })
        val antrean = draft.screens.first { it.moduleId.value.endsWith("_antrean") }.proposal
        assertNotNull(antrean)
        assertTrue(antrean.rationale.startsWith("Dipilih karena"), antrean.rationale)
        assertTrue(antrean.rationale.contains("klinik"), antrean.rationale)          // tempat kerja dari vocabulary pack
        assertEquals("Kunjungan", antrean.entity?.fields?.first()?.label)             // dokumen dari vocabulary pack
        val bengkel = draftOf(narratives.getValue("bengkel")).screens.first { it.moduleId.value.endsWith("_antrean") }.proposal
        assertEquals("Servis", bengkel?.entity?.fields?.first()?.label)
        assertTrue(bengkel?.rationale?.contains("bengkel") == true)
    }

    @Test
    fun `antrean berupa papan berstatus dan tagihan punya jumlah serta belum bayar dan lunas`() = runTest {
        val draft = draftOf(narratives.getValue("klinik"))
        val antrean = draft.screens.first { it.moduleId.value.endsWith("_antrean") }.proposal!!
        assertEquals(listOf("Menunggu", "Dikerjakan", "Selesai"), antrean.entity?.fields?.first { it.key == "status" }?.options)
        assertTrue(antrean.entity?.fields?.any { it.key == "tanggal" && it.type.name == "DATE" } == true)
        val tagihan = draft.screens.first { it.moduleId.value.endsWith("_tagihan") }.proposal!!
        assertEquals("NUMBER", tagihan.entity?.fields?.first { it.key == "jumlah" }?.type?.name)
        assertEquals(listOf("Belum bayar", "Lunas"), tagihan.entity?.fields?.first { it.key == "status" }?.options)
        val view = tagihan.view as ViewProposal.Table
        assertTrue("jumlah" in view.columns && "status" in view.columns)
    }

    @Test
    fun `dasbor menghitung dari layar modul lain lewat status awalnya`() = runTest {
        val draft = draftOf(narratives.getValue("klinik"))
        val tiles = (draft.screens.first { it.moduleId.value.endsWith("_laporan") }.proposal!!.view as ViewProposal.Dashboard).tiles
        assertTrue(tiles.isNotEmpty() && tiles.all { it.count != null })
        val antrean = draft.pack.modules.first { it.id.value.endsWith("_antrean") }
        assertTrue(tiles.any { it.count?.moduleId == antrean.id.value && it.count?.equals == "Menunggu" })
    }

    @Test
    fun `semua layar non-cetak bisa dikonversi menjadi layar interaktif`() = runTest {
        narratives.forEach { (name, text) ->
            draftOf(text).screens.forEach { s ->
                val result = s.proposal!!.toInteractiveScreen(ProposalSource.Deterministic)
                assertTrue(result.isSuccess, "$name/${s.screenId}: ${result.exceptionOrNull()?.message}")
            }
        }
    }

    @Test
    fun `tidak ada kosakata konveksi di keluaran vertikal lain`() = runTest {
        val forbidden = listOf("konveksi", "jahit", "kain", "garmen", "garment", "buyer", "tekstil", "sablon", "bordir", "makloon", "potong", "pcs")
        val whole = Regex("\\b(po|spk|bom|hpp|fob|cmt)\\b")
        narratives.forEach { (name, text) ->
            val json = DiscoveryDraftCodec.encodeToString(draftOf(text)).lowercase()
            forbidden.forEach { assertTrue(!json.contains(it), "$name memuat '$it'") }
            assertTrue(whole.find(json) == null, "$name memuat istilah konveksi: ${whole.find(json)?.value}")
        }
    }

    @Test
    fun `teks yang tampil ke prospek hanya berisi karakter yang ada di font aplikasi`() = runTest {
        // Nunito tidak punya panah/simbol non-ASCII: tampil sebagai kotak (ditemukan saat cek visual).
        narratives.values.forEach { text ->
            draftOf(text).screens.forEach { s ->
                val p = s.proposal!!
                val shown = listOf(p.title, p.rationale) + (p.entity?.fields?.flatMap { listOf(it.label) + it.options }.orEmpty()) + p.seed.flatMap { it.values }
                shown.forEach { t -> assertTrue(t.all { it.code < 0x100 }, "karakter non-Latin1 di '$t'") }
            }
        }
    }

    @Test
    fun `keluaran deterministik byte-per-byte`() = runTest {
        narratives.values.forEach { text ->
            assertEquals(DiscoveryDraftCodec.encodeToString(draftOf(text)), DiscoveryDraftCodec.encodeToString(draftOf(text)))
        }
    }

    @Test
    fun `round-trip codec draf berlayar`() = runTest {
        val draft = draftOf(narratives.getValue("katering"))
        assertEquals(draft, DiscoveryDraftCodec.decode(DiscoveryDraftCodec.encodeToString(draft)))
    }

    @Test
    fun `narasi garment tetap tanpa layar dan kemampuan cadangan tanpa pendapat tidak dibuatkan layar`() = runTest {
        assertEquals(emptyList(), draftOf("Kami konveksi makloon, kain dari buyer.").screens)
        assertEquals(emptyList(), draftOf("Usaha jasa serba guna").screens)
    }

    // --- bentuk widget lain & aturan tanpa tebakan (pack buatan test) --------------------------
    private val base = draftOf0("Klinik: antrean dan tagihan")
    private fun draftOf0(text: String): DiscoveryDraft = kotlinx.coroutines.runBlocking { agent.draft(DiscoveryRequest(text)).getOrThrow() }

    private fun packWith(widget: WidgetKind?, statuses: List<String> = emptyList()) = base.pack.let { pack ->
        val slot = pack.slots.first()
        pack.copy(slots = listOf(SlotDefinition(slot.code, slot.displayName, slot.phase, slot.defaultInput, slot.defaultOutput, widget, statuses)) + pack.slots.drop(1))
    }

    @Test
    fun `form checklist cetak dan layar kustom terbentuk dan lolos validator`() {
        listOf(WidgetKind.FORM, WidgetKind.CHECKLIST, WidgetKind.PRINT, WidgetKind.CUSTOM_SCREEN).forEach { widget ->
            val pack = packWith(widget)
            val module = pack.modules.first { it.slot == pack.slots.first().code }
            val p = DeterministicScreenProposer.proposalsFor(pack, module).getOrThrow().single()
            assertEquals(widget, p.widget)
            assertEquals(emptyList(), ScreenProposalValidator.validate(p, source = ProposalSource.Deterministic, packModuleIds = pack.modules.map { it.id.value }.toSet()), widget.name)
        }
    }

    @Test
    fun `slot tanpa widget tidak menghasilkan layar dan tidak meminjam dari garment`() {
        val pack = packWith(null)
        val module = pack.modules.first { it.slot == pack.slots.first().code }
        assertEquals(emptyList(), DeterministicScreenProposer.proposalsFor(pack, module).getOrThrow())
        // modul tanpa slot sama sekali (governance)
        val tanpaSlot = module.copy(kind = com.eventverse.app.domain.rbac.ModuleKind.GOVERNANCE, slot = null)
        assertEquals(emptyList(), DeterministicScreenProposer.proposalsFor(pack, tanpaSlot).getOrThrow())
        // pack garment: slot tanpa pendapat (finishing) tetap kosong
        val garment = GarmentDomainPack.pack
        val finishing = garment.modules.first { it.slot == com.eventverse.app.domain.pack.GarmentSlots.SEWING }
            .copy(slot = com.eventverse.app.domain.pack.GarmentSlots.FINISHING) // tak ada modul pengisi finishing; slotnya yang diuji
        assertEquals(emptyList(), DeterministicScreenProposer.proposalsFor(garment, finishing).getOrThrow())
    }

    @Test
    fun `papan tanpa status gagal bermesej bukan menjadi tabel diam-diam`() {
        val pack = packWith(WidgetKind.KANBAN)
        val module = pack.modules.first { it.slot == pack.slots.first().code }
        val failure = DeterministicScreenProposer.proposalsFor(pack, module)
        assertTrue(failure.isFailure)
        assertTrue(failure.exceptionOrNull()?.message.orEmpty().contains("defaultStatuses"))
    }

    /**
     * C8 (TRD-FIELD-002 Track A): pembuat deterministik **tidak pernah mengusulkan FILE** — kontrol
     * unggah baru ada di Track C, jadi tidak ada FieldProposal FILE di layar, tidak ada petunjuk FILE
     * di usulan pack deterministik, dan FILE tidak pernah menjadi elemen kartu (`cardOf(FILE) == null`).
     */
    @Test
    fun `proposer tidak mengusulkan FILE dan FILE tidak pernah jadi elemen kartu`() = runTest {
        val file = FieldProposal("lampiran", "Lampiran", FieldType.FILE)
        assertEquals(null, DeterministicScreenProposer.cardOf(file), "FILE tidak punya gaya kartu")
        assertNotNull(DeterministicScreenProposer.cardOf(FieldProposal("tanggal", "Tanggal", FieldType.DATE)), "kontras: tipe lain punya gaya kartu")
        narratives.values.forEach { text ->
            val draft = draftOf(text)
            draft.screens.forEach { s ->
                val p = s.proposal!!
                assertTrue(p.entity?.fields?.any { it.type == FieldType.FILE } != true, "${s.screenId}: usulan FILE")
                val card = (p.view as? ViewProposal.Kanban)?.card.orEmpty()
                val fileKeys = p.entity?.fields?.filter { it.type == FieldType.FILE }?.map { it.key }.orEmpty()
                assertTrue(card.none { it.field in fileKeys }, "${s.screenId}: FILE di kartu")
            }
            val hints = draft.pack.screenSuggestions.flatMap { it.kanbanHints?.fields.orEmpty() + it.tableHints?.fields.orEmpty() }
            assertTrue(hints.none { it.type == FieldType.FILE }, "${draft.pack.code.value}: petunjuk FILE")
        }
    }

    /**
     * A0 (TRD-FIELD-003 FR-5): pembuat deterministik tidak pernah mengusulkan MULTI_SELECT sebagai **status**
     * (status papan selalu ENUM), dan MULTI_SELECT tidak pernah menjadi elemen kartu (`cardOf` = null) — gaya
     * tampil daftar label ditetapkan Track C.
     */
    @Test
    fun `proposer tidak menjadikan MULTI_SELECT status atau elemen kartu`() = runTest {
        val multi = FieldProposal("layanan", "Layanan", FieldType.MULTI_SELECT, options = listOf("a", "b"), maxSelections = 1)
        assertEquals(null, DeterministicScreenProposer.cardOf(multi), "MULTI_SELECT tidak punya gaya kartu")
        assertNotNull(DeterministicScreenProposer.cardOf(FieldProposal("tanggal", "Tanggal", FieldType.DATE)), "kontras: tipe lain punya gaya kartu")
        narratives.values.forEach { text ->
            draftOf(text).screens.forEach { s ->
                val p = s.proposal!!
                val entity = p.entity
                val status = entity?.statusField?.let { k -> entity.fields.first { it.key == k }.type }
                assertTrue(status != FieldType.MULTI_SELECT, "${s.screenId}: MULTI_SELECT jadi status")
            }
        }
    }
}

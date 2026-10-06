package com.eventverse.app.domain.prototype

import com.eventverse.app.domain.discovery.WidgetKind
import com.eventverse.app.domain.discovery.proposal.ScreenProposalFixtures
import com.eventverse.app.domain.discovery.proposal.toInteractiveScreen
import com.eventverse.app.shared.json.JsonValue
import com.eventverse.app.shared.pack.SpecOpCodec
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * SP-B5 — `SpecOp.ChangeWidget`. Semua fixture **non-garment** (tiket servis, klinik); aturan kelayakan diuji
 * satu per satu karena keluarannya dikembalikan ke pengguna/LLM sebagai alasan penolakan.
 */
class ChangeWidgetOpTest {
    private val ticket = PrototypeContractSamples.ticketScreen                        // papan + form, entitas tiket
    private val antrean = ScreenProposalFixtures.kanbanAntrean().toInteractiveScreen().getOrThrow()
    private val tagihan = ScreenProposalFixtures.tabelTagihan().toInteractiveScreen().getOrThrow()
    private val persiapan = ScreenProposalFixtures.checklistPersiapan().toInteractiveScreen().getOrThrow()

    private fun change(s: InteractiveScreen, id: String, w: WidgetKind) = SpecOpApplier.apply(s, SpecOp.ChangeWidget(id, w))
    private fun failure(r: Result<InteractiveScreen>) = r.exceptionOrNull()?.message.orEmpty()

    // ---- papan → tabel ---------------------------------------------------------------------
    @Test
    fun `papan menjadi tabel dengan kolom dari field papan dan status bisa diubah di baris`() {
        val next = change(ticket, "papan", WidgetKind.TABLE).getOrThrow()
        val table = assertNotNull(next.spec.screens.first { it.screenId == "papan" }.table)
        assertEquals(listOf("Judul", "Peminta", "Status"), table.columns)
        assertEquals("Status", table.statusField)
        assertEquals(null, next.spec.screens.first { it.screenId == "papan" }.kanban)
        // entitas, seed, dan layar lain tak berubah
        assertEquals(ticket.spec.entities, next.spec.entities)
        assertEquals(ticket.seed, next.seed)
        assertEquals(ticket.spec.screens.first { it.screenId == "form" }, next.spec.screens.first { it.screenId == "form" })
        next.newStore()
    }

    @Test
    fun `elemen kartu menentukan kolom tabel bila ada`() {
        val next = change(antrean, ScreenProposalFixtures.kanbanAntrean().screenId, WidgetKind.TABLE).getOrThrow()
        val cols = next.spec.screens.single().table!!.columns
        assertEquals(listOf("nama", "keluhan", "tanggal_kunjungan", "prioritas", "status"), cols)
    }

    // ---- tabel → papan ---------------------------------------------------------------------
    @Test
    fun `tabel dengan satu field status menjadi papan berkolom opsi status dan kartu bergaya sesuai tipe`() {
        val next = change(tagihan, ScreenProposalFixtures.tabelTagihan().screenId, WidgetKind.KANBAN).getOrThrow()
        val k = assertNotNull(next.spec.screens.single().kanban)
        assertEquals("status", k.groupField)
        assertEquals(listOf("Belum bayar", "Lunas"), k.columns)
        assertEquals("pasien", k.titleField)
        assertEquals(listOf(CardElement("pasien", CardStyle.TITLE), CardElement("jumlah", CardStyle.NUMBER)), k.card)
        assertEquals(tagihan.seed, next.seed)
        next.newStore()
    }

    @Test
    fun `papan ke tabel ke papan menjaga data dan memberi papan yang setara`() {
        val id = "papan"
        val back = change(change(ticket, id, WidgetKind.TABLE).getOrThrow(), id, WidgetKind.KANBAN).getOrThrow()
        assertEquals(ticket.spec.entities, back.spec.entities)
        assertEquals(ticket.seed, back.seed)
        val k = back.spec.screens.first { it.screenId == id }.kanban!!
        assertEquals(listOf("Baru", "Diproses", "Selesai"), k.columns)
        assertEquals("Judul", k.titleField)
    }

    @Test
    fun `dengan dua field pilihan petunjuk mesin status memutuskan kolom papan`() {
        val prioritas = FieldSpec("Prioritas", "Prioritas", FieldType.ENUM, listOf("Rendah", "Tinggi"))
        val e = ticket.spec.entities.single().copy(fields = ticket.spec.entities.single().fields + prioritas)
        val table = ScreenSpec("tbl", "Tabel Tiket", WidgetKind.TABLE, "tiket", table = TableConfig(listOf("Judul", "Status", "Prioritas")))
        val two = InteractiveScreen(PrototypeSpec(listOf(e), listOf(table)), ticket.seed)
        val k = change(two, "tbl", WidgetKind.KANBAN).getOrThrow().spec.screens.single().kanban!!
        assertEquals("Status", k.groupField)                 // dari mesin status
        val ambiguous = two.copy(spec = PrototypeSpec(listOf(e.copy(stateMachine = null)), listOf(table)))
        assertTrue(failure(change(ambiguous, "tbl", WidgetKind.KANBAN)).contains("2 pilihan status"))
    }

    // ---- penolakan bermesej ----------------------------------------------------------------
    @Test
    fun `papan ditolak bila tidak ada field pilihan status`() {
        val tanpaEnum = InteractiveScreen(
            PrototypeSpec(
                listOf(EntitySpec("cat", "Catatan", listOf(FieldSpec("judul", "Judul", FieldType.TEXT)))),
                listOf(ScreenSpec("t", "Catatan", WidgetKind.TABLE, "cat", table = TableConfig(listOf("judul"))))
            ),
            emptyMap()
        )
        assertTrue(failure(change(tanpaEnum, "t", WidgetKind.KANBAN)).contains("belum punya field pilihan status"))
        // tetapi tabel selalu mungkin: papan tak bisa, kembali ke tabel dari layar tabel ditolak karena sudah tabel
        assertTrue(failure(change(tanpaEnum, "t", WidgetKind.TABLE)).contains("sudah berupa tabel"))
    }

    @Test
    fun `papan ditolak bila data tak punya status karena akan hilang dari papan`() {
        val tanpaStatus = tagihan.copy(seed = mapOf("tagihan" to listOf(PrototypeRow("x", mapOf("pasien" to "A")))))
        assertTrue(failure(change(tanpaStatus, ScreenProposalFixtures.tabelTagihan().screenId, WidgetKind.KANBAN)).contains("hilang dari papan"))
    }

    @Test
    fun `papan ditolak bila hanya ada field status tanpa judul kartu`() {
        val e = EntitySpec("s", "Status saja", listOf(FieldSpec("st", "Status", FieldType.ENUM, listOf("a", "b"))))
        val only = InteractiveScreen(PrototypeSpec(listOf(e), listOf(ScreenSpec("t", "Status saja", WidgetKind.TABLE, "s", table = TableConfig(listOf("st"))))), emptyMap())
        assertTrue(failure(change(only, "t", WidgetKind.KANBAN)).contains("judul kartu"))
    }

    @Test
    fun `daftar periksa menjadi tabel tapi tidak menjadi papan`() {
        val id = ScreenProposalFixtures.checklistPersiapan().screenId
        val table = change(persiapan, id, WidgetKind.TABLE).getOrThrow().spec.screens.single().table!!
        assertEquals(listOf("butir", "selesai"), table.columns)
        assertEquals(null, table.statusField)
        assertTrue(failure(change(persiapan, id, WidgetKind.KANBAN)).contains("pilihan status"))
    }

    @Test
    fun `dasbor formulir dan cetak tidak bisa diubah dan jenis target lain tidak dikenal`() {
        assertTrue(failure(change(ticket, "form", WidgetKind.TABLE)).contains("hanya tabel, papan, atau daftar periksa"))
        val dasbor = ScreenProposalFixtures.dasborHarian().toInteractiveScreen().getOrThrow()
        assertTrue(failure(change(dasbor, ScreenProposalFixtures.dasborHarian().screenId, WidgetKind.TABLE)).contains("tidak punya data"))
        listOf(WidgetKind.FORM, WidgetKind.DASHBOARD, WidgetKind.CHECKLIST, WidgetKind.PRINT, WidgetKind.CUSTOM_SCREEN).forEach {
            assertTrue(failure(change(ticket, "papan", it)).contains("yang dikenal: tabel dan papan"), it.name)
        }
        assertTrue(failure(change(ticket, "hantu", WidgetKind.TABLE)).contains("tidak ada"))
        assertTrue(failure(change(ticket, "papan", WidgetKind.KANBAN)).contains("sudah berupa papan"))
    }

    @Test
    fun `permintaan gagal tidak membatalkan yang sah dan tercatat di log`() {
        val applied = SpecOpApplier.applyAll(
            ticket,
            listOf(SpecOp.ChangeWidget("form", WidgetKind.TABLE), SpecOp.ChangeWidget("papan", WidgetKind.TABLE)),
            at = "t"
        )
        assertEquals(listOf(false, true), applied.log.map { it.ok })
        assertEquals(WidgetKind.TABLE, applied.screen.spec.screens.first { it.screenId == "papan" }.widget)
    }

    // ---- kawat -----------------------------------------------------------------------------
    @Test
    fun `codec round-trip dan menolak jenis tampilan tak dikenal`() {
        val op = SpecOp.ChangeWidget("papan", WidgetKind.TABLE)
        assertEquals(op, SpecOpCodec.decode(SpecOpCodec.encode(op)).getOrThrow())
        val bad = JsonValue.Obj(mapOf("type" to JsonValue.Str("ChangeWidget"), "screenId" to JsonValue.Str("p"), "widget" to JsonValue.Str("PETA")))
        assertTrue(SpecOpCodec.decode(bad).exceptionOrNull()?.message.orEmpty().contains("PETA"))
    }

    // ---- pengusul deterministik ------------------------------------------------------------
    private val proposer = DeterministicSpecOpProposer()

    @Test
    fun `kalimat ubah jadi tabel dan papan diusulkan untuk satu-satunya layar data`() = runTest {
        assertEquals(listOf(SpecOp.ChangeWidget("papan", WidgetKind.TABLE)), proposer.propose("ubah jadi tabel", ticket).getOrThrow())
        assertEquals(listOf(SpecOp.ChangeWidget("papan", WidgetKind.TABLE)), proposer.propose("Jadikan Tabel", ticket).getOrThrow())
        val table = change(ticket, "papan", WidgetKind.TABLE).getOrThrow()
        listOf("ubah tampilan jadi papan", "ubah menjadi kanban", "ganti jadi papan").forEach {
            assertEquals(listOf(SpecOp.ChangeWidget("papan", WidgetKind.KANBAN)), proposer.propose(it, table).getOrThrow(), it)
        }
    }

    @Test
    fun `tampilan data ambigu atau tak ada ditolak tanpa tebakan`() = runTest {
        val two = ticket.copy(spec = PrototypeSpec(ticket.spec.entities, ticket.spec.screens + ScreenSpec("tbl", "Tabel", WidgetKind.TABLE, "tiket", table = TableConfig(listOf("Judul")))))
        assertTrue(proposer.propose("ubah jadi tabel", two).exceptionOrNull()?.message.orEmpty().contains("sebutkan yang mana"))
        val dasbor = ScreenProposalFixtures.dasborHarian().toInteractiveScreen().getOrThrow()
        // dasbor tak punya entitas: ditolak oleh pengusul sebelum operasi dibentuk
        assertTrue(proposer.propose("ubah jadi tabel", dasbor).isFailure)
    }

    @Test
    fun `kalimat ganti nama tetap menjadi rename bukan ubah tampilan`() = runTest {
        assertEquals(
            listOf(SpecOp.RenameEnumOption("tiket", "Status", "Selesai", "Ditutup")),
            proposer.propose("ganti nama Selesai jadi Ditutup", ticket).getOrThrow()
        )
    }

    @Test
    fun `tabel tanpa field pilihan masih bisa dapat kolom baru dari pengusul`() = runTest {
        val tanpaEnum = InteractiveScreen(
            PrototypeSpec(
                listOf(EntitySpec("cat", "Catatan", listOf(FieldSpec("judul", "Judul", FieldType.TEXT)))),
                listOf(ScreenSpec("t", "Catatan", WidgetKind.TABLE, "cat", table = TableConfig(listOf("judul"))))
            ),
            emptyMap()
        )
        assertEquals(listOf(SpecOp.AddField("cat", FieldSpec("Prioritas", "Prioritas", FieldType.TEXT))), proposer.propose("tambah kolom Prioritas", tanpaEnum).getOrThrow())
        assertTrue(proposer.propose("tambah status Baru", tanpaEnum).isFailure)   // status tetap butuh field ENUM
    }
}

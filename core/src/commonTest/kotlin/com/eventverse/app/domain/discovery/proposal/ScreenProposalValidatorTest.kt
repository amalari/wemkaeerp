package com.eventverse.app.domain.discovery.proposal

import com.eventverse.app.domain.discovery.WidgetKind
import com.eventverse.app.domain.discovery.proposal.ScreenProposalFixtures.kanbanAntrean
import com.eventverse.app.domain.discovery.proposal.ScreenProposalFixtures.tabelTagihan
import com.eventverse.app.domain.prototype.CardElement
import com.eventverse.app.domain.prototype.ColumnMeta
import com.eventverse.app.domain.prototype.CountSpec
import com.eventverse.app.domain.prototype.DataBinding
import com.eventverse.app.domain.prototype.FieldType
import com.eventverse.app.domain.prototype.TileSpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Satu tes per aturan §2.2, masing-masing menegaskan **path** galatnya (galat dikembalikan ke LLM). */
class ScreenProposalValidatorTest {

    private fun issues(p: ScreenProposal, source: ProposalSource? = null, modules: Set<String>? = null) =
        ScreenProposalValidator.validate(p, source = source, packModuleIds = modules)

    private fun assertIssueAt(path: String, list: List<ProposalIssue>, contains: String? = null) {
        val hit = list.firstOrNull { it.path == path && (contains == null || it.message.contains(contains)) }
        assertTrue(hit != null, "Tidak ada galat di $path${contains?.let { " memuat '$it'" }.orEmpty()}; ada: $list")
    }

    private fun ScreenProposal.withEntity(f: (EntityProposal) -> EntityProposal) = copy(entity = entity?.let(f))
    private fun ScreenProposal.withFields(f: (List<FieldProposal>) -> List<FieldProposal>) = withEntity { it.copy(fields = f(it.fields)) }

    @Test
    fun `semua fixture non-garment lolos`() {
        ScreenProposalFixtures.semua().forEach { assertEquals(emptyList(), issues(it), it.screenId) }
    }

    // --- teks & kunci ---------------------------------------------------------------------------
    @Test
    fun `rationale kosong atau kepanjangan ditolak`() {
        assertIssueAt("$.rationale", issues(kanbanAntrean().copy(rationale = " ")), "wajib")
        assertIssueAt("$.rationale", issues(kanbanAntrean().copy(rationale = "x".repeat(201))), "terlalu panjang")
    }

    @Test
    fun `judul dan label kepanjangan ditolak dengan path`() {
        assertIssueAt("$.title", issues(kanbanAntrean().copy(title = "x".repeat(201))))
        assertIssueAt("$.entity.fields[0].label", issues(kanbanAntrean().withFields { f -> listOf(f[0].copy(label = "x".repeat(201))) + f.drop(1) }))
    }

    @Test
    fun `kunci field dan entity wajib berpola`() {
        assertIssueAt("$.entity.id", issues(kanbanAntrean().withEntity { it.copy(id = "Pasien Baru") }), "tidak sah")
        val p = kanbanAntrean().withFields { f -> listOf(f[0].copy(key = "Nama")) + f.drop(1) }
        assertIssueAt("$.entity.fields[0].key", issues(p), "tidak sah")
    }

    @Test
    fun `kunci field kembar ditolak`() {
        val p = kanbanAntrean().withFields { f -> f + f[0] }
        assertIssueAt("$.entity.fields[5].key", issues(p), "dua kali")
    }

    // --- batas ----------------------------------------------------------------------------------
    @Test
    fun `field lebih dari 12 ditolak`() {
        val p = kanbanAntrean().withFields { f -> f + (1..8).map { FieldProposal("f$it", "F$it", FieldType.TEXT) } }
        assertIssueAt("$.entity.fields", issues(p), "maksimum 12")
    }

    @Test
    fun `opsi ENUM lebih dari 8 ditolak`() {
        val many = (1..9).map { "S$it" }
        val p = kanbanAntrean().withFields { f -> f.dropLast(1) + f.last().copy(options = many) }.withEntity { it.copy(transitions = emptyMap()) }
        assertIssueAt("$.entity.fields[4].options", issues(p), "maksimum 8")
        assertIssueAt("$.entity.statusField", issues(p), "maksimum 8")
    }

    @Test
    fun `seed lebih dari 8 baris ditolak`() {
        val row = mapOf("nama" to "A")
        assertIssueAt("$.seed", issues(kanbanAntrean().copy(seed = List(9) { row })), "maksimum 8")
    }

    // --- koherensi entitas ----------------------------------------------------------------------
    @Test
    fun `non-ENUM tidak boleh punya opsi dan ENUM wajib punya`() {
        val a = kanbanAntrean().withFields { f -> listOf(f[0].copy(options = listOf("x"))) + f.drop(1) }
        assertIssueAt("$.entity.fields[0].options", issues(a), "bukan ENUM")
        val b = kanbanAntrean().withFields { f -> f.dropLast(1) + f.last().copy(options = emptyList()) }
        assertIssueAt("$.entity.fields[4].options", issues(b), "wajib punya")
    }

    @Test
    fun `statusField harus field ENUM yang ada`() {
        assertIssueAt("$.entity.statusField", issues(kanbanAntrean().withEntity { it.copy(statusField = "hilang") }), "tidak ada")
        assertIssueAt("$.entity.statusField", issues(kanbanAntrean().withEntity { it.copy(statusField = "nama") }), "ENUM")
    }

    @Test
    fun `transisi hanya antar opsi status yang ada`() {
        val asal = kanbanAntrean().withEntity { it.copy(transitions = mapOf("Hantu" to listOf("Selesai"))) }
        assertIssueAt("$.entity.transitions.Hantu", issues(asal), "Status asal")
        val tujuan = kanbanAntrean().withEntity { it.copy(transitions = mapOf("Menunggu" to listOf("Pulang"))) }
        assertIssueAt("$.entity.transitions.Menunggu[0]", issues(tujuan), "Status tujuan")
        val tanpaStatus = tabelTagihan().withEntity { it.copy(statusField = null, transitions = mapOf("Lunas" to listOf("Lunas"))) }
        assertIssueAt("$.entity.transitions", issues(tanpaStatus), "statusField")
    }

    @Test
    fun `widget data wajib punya entity dan dasbor tidak boleh`() {
        assertIssueAt("$.entity", issues(kanbanAntrean().copy(entity = null)), "wajib punya entity")
        assertIssueAt("$.entity", issues(ScreenProposalFixtures.dasborHarian().copy(entity = ScreenProposalFixtures.pasien)), "tidak punya entity")
    }

    // --- tampilan -------------------------------------------------------------------------------
    @Test
    fun `varian view harus cocok dengan widget`() {
        assertIssueAt("$.view", issues(kanbanAntrean().copy(widget = WidgetKind.TABLE)), "tidak cocok")
        assertIssueAt("$.view", issues(ScreenProposalFixtures.layarKustom().copy(view = ViewProposal.Print(emptyList()))), "tidak cocok")
    }

    @Test
    fun `kanban wajib statusField dan rujukan field-nya ada`() {
        assertIssueAt("$.entity.statusField", issues(kanbanAntrean().withEntity { it.copy(statusField = null, transitions = emptyMap()) }), "Kanban wajib")
        val kartu = kanbanAntrean().copy(view = ViewProposal.Kanban(card = listOf(CardElement("gaib"))))
        assertIssueAt("$.view.card[0].field", issues(kartu), "gaib")
        val form = kanbanAntrean().copy(view = ViewProposal.Kanban(detailFormFields = listOf("nama", "gaib")))
        assertIssueAt("$.view.detailFormFields[1]", issues(form), "gaib")
    }

    @Test
    fun `kolom kanban adalah opsi status sehingga metadata kolom lain ditolak`() {
        val p = kanbanAntrean().copy(view = ViewProposal.Kanban(columnMeta = mapOf("Pulang" to ColumnMeta(wipLimit = 2))))
        assertIssueAt("$.view.columnMeta.Pulang", issues(p), "bukan pilihan status")
    }

    @Test
    fun `kolom tabel dan field sunting wajib ada`() {
        val kolom = tabelTagihan().copy(view = ViewProposal.Table(columns = listOf("pasien", "gaib")))
        assertIssueAt("$.view.columns[1]", issues(kolom), "gaib")
        val sunting = tabelTagihan().copy(view = ViewProposal.Table(columns = listOf("pasien"), editableFields = listOf("gaib")))
        assertIssueAt("$.view.editableFields[0]", issues(sunting), "gaib")
        assertIssueAt("$.view.columns", issues(tabelTagihan().copy(view = ViewProposal.Table(columns = emptyList()))), "minimal 1")
    }

    @Test
    fun `status bermesin tidak boleh disunting sebagai sel teks`() {
        val entity = tabelTagihan().entity?.copy(transitions = mapOf("Belum bayar" to listOf("Lunas")))
        val p = tabelTagihan().copy(entity = entity, view = ViewProposal.Table(listOf("pasien", "status"), editableFields = listOf("status")))
        assertIssueAt("$.view.editableFields[0]", issues(p), "pilihan status")
    }

    @Test
    fun `form wajib memuat field wajib entity dan merujuk field yang ada`() {
        val p = ScreenProposalFixtures.formPendaftaran().copy(view = ViewProposal.Form(listOf("keluhan", "gaib")))
        val found = issues(p)
        assertIssueAt("$.view.fields[1]", found, "gaib")
        assertIssueAt("$.view.fields", found, "'nama' harus ada di form")
    }

    @Test
    fun `checklist doneField wajib BOOL`() {
        val p = ScreenProposalFixtures.checklistPersiapan().copy(view = ViewProposal.Checklist("butir", "butir"))
        assertIssueAt("$.view.doneField", issues(p), "BOOL")
    }

    @Test
    fun `dasbor wajib punya ubin berbatas dan hitungan merujuk modul pack`() {
        val kosong = ScreenProposalFixtures.dasborHarian().copy(view = ViewProposal.Dashboard(emptyList()))
        assertIssueAt("$.view.tiles", issues(kosong), "minimal 1")
        val banyak = ScreenProposalFixtures.dasborHarian().copy(view = ViewProposal.Dashboard(List(9) { TileSpec("U$it", value = "1") }))
        assertIssueAt("$.view.tiles", issues(banyak), "maksimum 8")
        val asing = ScreenProposalFixtures.dasborHarian().copy(view = ViewProposal.Dashboard(listOf(TileSpec("X", count = CountSpec("modul_asing")))))
        assertIssueAt("$.view.tiles[0].count.moduleId", issues(asing, modules = setOf("klinik_antrean")), "modul_asing")
    }

    @Test
    fun `cetak dengan field wajib punya entity`() {
        val p = ScreenProposalFixtures.cetakKuitansi().copy(entity = null)
        assertIssueAt("$.entity", issues(p), "Print")
    }

    // --- seed -----------------------------------------------------------------------------------
    @Test
    fun `seed wajib cocok skema`() {
        fun seed(vararg kv: Pair<String, String>) = kanbanAntrean().copy(seed = listOf(mapOf("nama" to "A") + kv))
        assertIssueAt("$.seed[0].status", issues(seed("status" to "Pulang")), "bukan pilihan")
        assertIssueAt("$.seed[0].tanggal_kunjungan", issues(seed("tanggal_kunjungan" to "5 Oktober")), "ISO")
        assertIssueAt("$.seed[0].tanggal_kunjungan", issues(seed("tanggal_kunjungan" to "2026-02-31")), "ISO")
        assertIssueAt("$.seed[0].prioritas", issues(seed("prioritas" to "true")), "ya")
        assertIssueAt("$.seed[0].gaib", issues(seed("gaib" to "x")), "tidak ada")
        val angka = tabelTagihan().copy(seed = listOf(mapOf("pasien" to "A", "jumlah" to "seratus")))
        assertIssueAt("$.seed[0].jumlah", issues(angka), "bukan angka")
    }

    @Test
    fun `seed wajib mengisi field wajib dan hanya untuk layar ber-entity`() {
        val tanpaNama = kanbanAntrean().copy(seed = listOf(mapOf("keluhan" to "Batuk")))
        assertIssueAt("$.seed[0].nama", issues(tanpaNama), "wajib")
        val dasbor = ScreenProposalFixtures.dasborHarian().copy(seed = listOf(mapOf("a" to "b")))
        assertIssueAt("$.seed", issues(dasbor), "tanpa entity")
    }

    /** C8 (TRD-FIELD-002 Track A): seed FILE wajib kosong — referensi berbentuk FileRef sah pun ditolak,
     *  karena baris contoh tidak boleh menunjuk objek yang tidak ada; kosong tetap sah (belum diisi). */
    @Test
    fun `seed FILE wajib kosong walau bentuk referensinya sah`() {
        fun denganLampiran(nilai: String) = kanbanAntrean()
            .withFields { f -> f + FieldProposal("lampiran", "Lampiran", FieldType.FILE) }
            .copy(seed = listOf(mapOf("nama" to "A", "status" to "Menunggu", "lampiran" to nilai)))
        assertIssueAt(
            "$.seed[0].lampiran",
            issues(denganLampiran("fields/klinik/antrean/r-1/scan-a1b2c3-scan.pdf")),
            "wajib kosong"
        )
        assertEquals(emptyList(), issues(denganLampiran("")), "seed FILE kosong sah")
    }

    // --- binding & path draf --------------------------------------------------------------------
    @Test
    fun `binding api hanya untuk usulan pack`() {
        val api = tabelTagihan().copy(binding = DataBinding.Api("/api/tagihan"))
        assertIssueAt("$.binding", issues(api, ProposalSource.Agent("koog/x")), "memori")
        assertIssueAt("$.binding", issues(api, ProposalSource.Deterministic), "memori")
        assertEquals(emptyList(), issues(api, ProposalSource.Pack))
    }

    @Test
    fun `awalan path mengikuti lokasi di dokumen draf`() {
        val found = ScreenProposalValidator.validate(kanbanAntrean().copy(rationale = ""), path = "$.screens[3].proposal")
        assertIssueAt("$.screens[3].proposal.rationale", found)
    }
}

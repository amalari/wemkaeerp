package com.eventverse.app.domain.discovery.proposal

import com.eventverse.app.domain.discovery.WidgetKind
import com.eventverse.app.domain.pack.ModuleId
import com.eventverse.app.domain.prototype.FieldType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProposalEditTest {

    private fun f(key: String, type: FieldType = FieldType.TEXT, required: Boolean = false) = FieldProposal(key, key.replaceFirstChar { it.uppercase() }, type, required)

    private val table = ScreenProposal(
        "s1", ModuleId("klinik_poli"), "Antrean Poli", WidgetKind.TABLE, "karena uji",
        EntityProposal("pasien", "Pasien", listOf(f("nama", required = true), f("keluhan"), f("tgl", FieldType.DATE))),
        ViewProposal.Table(columns = listOf("nama", "keluhan", "tgl"), editableFields = listOf("keluhan")),
        seed = listOf(mapOf("nama" to "Budi", "keluhan" to "ngilu", "tgl" to "2026-10-01"))
    )

    @Test
    fun `tambah field ikut tampil di tabel, dan lolos validator`() {
        val out = table.applyEdits(listOf(ProposalEdit.AddField(f("tanggal_kirim", FieldType.DATE)))).getOrThrow()
        assertEquals(listOf("nama", "keluhan", "tgl", "tanggal_kirim"), out.entity!!.fields.map { it.key })
        assertEquals(listOf("nama", "keluhan", "tgl", "tanggal_kirim"), (out.view as ViewProposal.Table).columns)
        assertEquals(listOf("keluhan", "tanggal_kirim"), (out.view as ViewProposal.Table).editableFields)
    }

    @Test
    fun `kurangi field dibersihkan dari tabel dan seed, objek asal tidak berubah`() {
        val out = table.applyEdits(listOf(ProposalEdit.RemoveField("keluhan"))).getOrThrow()
        assertEquals(listOf("nama", "tgl"), out.entity!!.fields.map { it.key })
        assertEquals(listOf("nama", "tgl"), (out.view as ViewProposal.Table).columns)
        assertEquals(emptyList(), (out.view as ViewProposal.Table).editableFields)
        assertEquals(setOf("nama", "tgl"), out.seed.single().keys)
        assertEquals(3, table.entity!!.fields.size, "usulan asal tidak dimutasi")
    }

    @Test
    fun `ganti field mengubah tipe dan wajib tanpa mengubah kunci`() {
        val enum = FieldProposal("keluhan", "Keluhan", FieldType.ENUM, true, listOf("ngilu", "bengkak"))
        val out = table.applyEdits(listOf(ProposalEdit.ReplaceField("keluhan", enum))).getOrThrow()
        assertEquals(FieldType.ENUM, out.entity!!.fields.first { it.key == "keluhan" }.type)
        assertTrue(out.entity!!.fields.first { it.key == "keluhan" }.required)
    }

    @Test
    fun `sunting tak sah ditolak dengan pesan jelas, bukan disaring diam-diam`() {
        fun msg(e: ProposalEdit) = table.applyEdits(listOf(e)).exceptionOrNull()?.message.orEmpty()
        assertTrue(msg(ProposalEdit.AddField(f("nama"))).contains("sudah ada"))
        assertTrue(msg(ProposalEdit.RemoveField("hantu")).contains("tidak ada"))
        assertTrue(msg(ProposalEdit.ReplaceField("nama", f("lain"))).contains("tidak boleh mengubah kuncinya"))
        assertTrue(msg(ProposalEdit.AddField(f("Kunci Salah"))).contains("tidak sah"), "kunci mengikuti aturan validator tunggal")
        val noEntity = table.copy(widget = WidgetKind.CUSTOM_SCREEN, entity = null, view = ViewProposal.None, seed = emptyList())
        assertTrue(noEntity.applyEdits(listOf(ProposalEdit.AddField(f("x")))).exceptionOrNull()!!.message!!.contains("tidak punya isian"))
    }

    @Test
    fun `field status tidak boleh dibuang atau diganti tipenya`() {
        val status = FieldProposal("status", "Status", FieldType.ENUM, true, listOf("baru", "selesai"))
        val kanban = ScreenProposal(
            "s2", ModuleId("klinik_poli"), "Papan", WidgetKind.KANBAN, "karena uji",
            EntityProposal("pasien", "Pasien", listOf(f("nama", required = true), status), statusField = "status"),
            ViewProposal.Kanban()
        )
        assertTrue(kanban.applyEdits(listOf(ProposalEdit.RemoveField("status"))).exceptionOrNull()!!.message!!.contains("tidak boleh dibuang"))
        assertTrue(kanban.applyEdits(listOf(ProposalEdit.ReplaceField("status", status.copy(type = FieldType.TEXT, options = emptyList())))).isFailure)
    }

    @Test
    fun `tambah field wajib mengisi baris contoh sesuai tipenya sehingga lolos validator`() {
        val out = table.applyEdits(listOf(ProposalEdit.AddField(f("tanggal_kirim", FieldType.DATE, required = true)))).getOrThrow()
        assertEquals("2026-01-01", out.seed.single()["tanggal_kirim"])
        val enum = FieldProposal("prioritas", "Prioritas", FieldType.ENUM, true, listOf("tinggi", "rendah"))
        assertEquals("tinggi", table.applyEdits(listOf(ProposalEdit.AddField(enum))).getOrThrow().seed.single()["prioritas"])
        val noRequired = table.applyEdits(listOf(ProposalEdit.AddField(f("catatan")))).getOrThrow()
        assertEquals(setOf("nama", "keluhan", "tgl"), noRequired.seed.single().keys, "field tak wajib tidak mengisi baris contoh")
    }

    @Test
    fun `ganti tipe menjaga baris contoh sah, nilai yang tak sesuai dibuang atau diganti contoh bila wajib`() {
        val toDate = FieldProposal("keluhan", "Keluhan", FieldType.DATE, required = false)
        val out = table.applyEdits(listOf(ProposalEdit.ReplaceField("keluhan", toDate))).getOrThrow()
        assertEquals(setOf("nama", "tgl"), out.seed.single().keys, "'ngilu' bukan tanggal dan field tak wajib → nilai dibuang")
        val toRequiredNumber = FieldProposal("keluhan", "Keluhan", FieldType.NUMBER, required = true)
        assertEquals("0", table.applyEdits(listOf(ProposalEdit.ReplaceField("keluhan", toRequiredNumber))).getOrThrow().seed.single()["keluhan"])
        val sameType = FieldProposal("keluhan", "Keluhan Utama", FieldType.TEXT, required = true)
        assertEquals("ngilu", table.applyEdits(listOf(ProposalEdit.ReplaceField("keluhan", sameType))).getOrThrow().seed.single()["keluhan"], "nilai yang masih sah dipertahankan")
    }

    /** C3 Irisan 2: LONG_TEXT mengalir lewat suntingsan usulan dan lolos validator penuh. */
    @Test
    fun `LONG_TEXT ikut sunting usulan - tak wajib tak mengisi seed, wajib menjaga nilai bebas tetap sah`() {
        val added = table.applyEdits(listOf(ProposalEdit.AddField(FieldProposal("riwayat", "Riwayat", FieldType.LONG_TEXT)))).getOrThrow()
        assertEquals(FieldType.LONG_TEXT, added.entity!!.fields.first { it.key == "riwayat" }.type)
        assertEquals(setOf("nama", "keluhan", "tgl"), added.seed.single().keys, "LONG_TEXT tak wajib tidak mengisi baris contoh")
        val toLongRequired = FieldProposal("keluhan", "Keluhan", FieldType.LONG_TEXT, required = true)
        val replaced = table.applyEdits(listOf(ProposalEdit.ReplaceField("keluhan", toLongRequired))).getOrThrow()
        assertEquals("ngilu", replaced.seed.single()["keluhan"], "teks bebas tetap sah sebagai LONG_TEXT — validator lolos")
    }
}

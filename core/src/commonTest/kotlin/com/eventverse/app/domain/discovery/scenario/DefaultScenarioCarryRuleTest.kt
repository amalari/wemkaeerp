package com.eventverse.app.domain.discovery.scenario

import com.eventverse.app.domain.prototype.EntitySpec
import com.eventverse.app.domain.prototype.FieldSpec
import com.eventverse.app.domain.prototype.FieldType
import com.eventverse.app.domain.prototype.SelectOption
import com.eventverse.app.domain.prototype.TextValidation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DefaultScenarioCarryRuleTest {
    private fun f(key: String, type: FieldType, options: List<String> = emptyList(), withTime: Boolean = false, validation: TextValidation = TextValidation.NONE) =
        FieldSpec(key, key.replaceFirstChar { it.uppercase() }, type, options.map { SelectOption(it) }, withTime = withTime, validation = validation)

    private val source = EntitySpec(
        "pasien", "Pasien",
        listOf(
            f("nama", FieldType.TEXT), f("umur", FieldType.NUMBER), f("lahir", FieldType.DATE), f("jenis", FieldType.ENUM, listOf("umum", "bpjs")),
            f("aktif", FieldType.BOOL), f("catatan", FieldType.LONG_TEXT)
        )
    )
    private val target = EntitySpec(
        "kunjungan", "Kunjungan",
        listOf(
            f("pasien", FieldType.TEXT), f("usia", FieldType.TEXT), f("tgl", FieldType.DATE), f("kategori", FieldType.ENUM, listOf("umum", "asuransi")),
            f("status", FieldType.TEXT), f("jumlah", FieldType.NUMBER), f("email", FieldType.TEXT, validation = TextValidation.EMAIL),
            f("jenis_tgl", FieldType.DATE, withTime = true), f("keterangan", FieldType.LONG_TEXT)
        )
    )
    private fun run(values: Map<String, String>, vararg c: Pair<String, String>) =
        DefaultScenarioCarryRule.apply(values, c.map { FieldCarry(it.first, it.second) }, source, target)

    @Test
    fun `text to text should carry the value`() {
        val out = run(mapOf("nama" to "Siti"), "nama" to "pasien")
        assertEquals(mapOf("pasien" to "Siti"), out.values)
        assertEquals(emptyList(), out.rejected)
    }

    @Test
    fun `number to text and bool to text should carry as stored string`() {
        val out = run(mapOf("umur" to "34", "aktif" to "ya"), "umur" to "usia", "aktif" to "status")
        assertEquals(mapOf("usia" to "34", "status" to "ya"), out.values)
    }

    @Test
    fun `enum label should carry to text and to enum only when the option exists`() {
        assertEquals(mapOf("status" to "bpjs"), run(mapOf("jenis" to "bpjs"), "jenis" to "status").values)
        assertEquals(mapOf("kategori" to "umum"), run(mapOf("jenis" to "umum"), "jenis" to "kategori").values)
        val out = run(mapOf("jenis" to "bpjs"), "jenis" to "kategori")
        assertEquals(emptyMap(), out.values)
        assertEquals(listOf("carries[0].toField"), out.rejected.map { it.path })
    }

    @Test
    fun `date to date should carry and date to date with time should be rejected not guessed`() {
        assertEquals(mapOf("tgl" to "2026-10-15"), run(mapOf("lahir" to "2026-10-15"), "lahir" to "tgl").values)
        val out = run(mapOf("lahir" to "2026-10-15"), "lahir" to "jenis_tgl")
        assertEquals(emptyMap(), out.values)
        assertEquals(1, out.rejected.size)
    }

    @Test
    fun `incompatible types should be rejected with path`() {
        val toNumber = run(mapOf("nama" to "Siti"), "nama" to "jumlah")
        assertEquals(emptyMap(), toNumber.values)
        assertEquals(listOf("carries[0].toField"), toNumber.rejected.map { it.path })
        assertTrue(run(mapOf("lahir" to "2026-10-15"), "lahir" to "jumlah").rejected.isNotEmpty())
        assertTrue(run(mapOf("umur" to "3"), "umur" to "kategori").rejected.isNotEmpty())
    }

    @Test
    fun `value invalid for target validation should be rejected`() {
        val out = run(mapOf("nama" to "bukan email"), "nama" to "email")
        assertEquals(emptyMap(), out.values)
        assertEquals(1, out.rejected.size)
        assertEquals(mapOf("email" to "a@b.id"), run(mapOf("nama" to "a@b.id"), "nama" to "email").values)
    }

    @Test
    fun `missing field on either side should be rejected not dropped silently`() {
        val out = run(mapOf("nama" to "Siti"), "hantu" to "pasien", "nama" to "hantu")
        assertEquals(emptyMap(), out.values)
        assertEquals(listOf("carries[0].fromField", "carries[1].toField"), out.rejected.map { it.path })
    }

    @Test
    fun `empty or absent source value should not be carried and not be an error`() {
        val out = run(mapOf("nama" to ""), "nama" to "pasien", "umur" to "usia")
        assertEquals(emptyMap(), out.values)
        assertEquals(emptyList(), out.rejected)
    }

    @Test
    fun `second carry into the same target should be rejected`() {
        val out = run(mapOf("nama" to "Siti", "catatan" to "x"), "nama" to "pasien", "catatan" to "pasien")
        assertEquals(mapOf("pasien" to "Siti"), out.values)
        assertEquals(listOf("carries[1].toField"), out.rejected.map { it.path })
    }

    @Test
    fun `long text to text should carry`() {
        assertEquals(mapOf("keterangan" to "baris"), run(mapOf("catatan" to "baris"), "catatan" to "keterangan").values)
    }

    @Test
    fun `carry types should treat file and cross reference types as incompatible`() {
        assertTrue(!ScenarioCarryTypes.compatible(FieldType.FILE, FieldType.FILE))
        assertTrue(!ScenarioCarryTypes.compatible(FieldType.TEXT, FieldType.RELATION))
        assertTrue(!ScenarioCarryTypes.compatible(FieldType.RELATION, FieldType.TEXT))
        assertTrue(ScenarioCarryTypes.compatible(FieldType.RELATION, FieldType.RELATION))
    }
}

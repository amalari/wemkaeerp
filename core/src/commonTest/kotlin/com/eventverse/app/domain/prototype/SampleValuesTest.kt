package com.eventverse.app.domain.prototype

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Nilai contoh simulasi: selalu lolos aturan field, dan tipe yang butuh data nyata tidak dikarang. */
class SampleValuesTest {
    private fun opts(vararg labels: String) = labels.mapIndexed { i, l -> SelectOption(SelectOptionId("o$i"), l) }

    private fun spec(type: FieldType, required: Boolean = true) = when (type) {
        FieldType.ENUM, FieldType.MULTI_SELECT -> FieldSpec("k", "Kolom", type, options = opts("Menunggu", "Dipanggil"), required = required)
        FieldType.RELATION -> FieldSpec("k", "Kolom", type, required = required, target = "modul:entitas")
        else -> FieldSpec("k", "Kolom", type, required = required)
    }

    /** Tipe yang nilainya hanya bermakna bila nyata (menunjuk sesuatu yang harus ada): tak boleh dikarang. */
    private val needsRealData = setOf(FieldType.RELATION, FieldType.USER_REF, FieldType.FILE)

    @Test
    fun `setiap tipe field should punya keputusan eksplisit - nilai contoh sah atau null`() {
        FieldType.entries.forEach { t ->
            val f = spec(t)
            val v = SampleValues.forField(f)
            if (t in needsRealData) assertNull(v, "$t tak boleh dikarang")
            else {
                assertNotNull(v, "$t harus punya nilai contoh")
                assertTrue(v.isNotEmpty() && f.accepts(v), "$t: nilai contoh '$v' harus lolos accepts")
            }
        }
    }

    @Test
    fun `enum should memakai label opsi pertama`() {
        assertEquals("Menunggu", SampleValues.forField(spec(FieldType.ENUM)))
    }

    @Test
    fun `teks dengan validasi email atau telepon should mendapat nilai yang lolos validasi`() {
        listOf(TextValidation.EMAIL, TextValidation.PHONE).forEach { v ->
            val f = FieldSpec("k", "Kolom", FieldType.TEXT, required = true, validation = v)
            val s = SampleValues.forField(f)
            assertNotNull(s, "$v")
            assertTrue(f.accepts(s), "$v: '$s'")
        }
    }

    @Test
    fun `tanggal dengan jam should mendapat nilai bergaya tanggal-jam`() {
        val f = FieldSpec("k", "Kolom", FieldType.DATE, required = true, withTime = true)
        val s = SampleValues.forField(f)
        assertNotNull(s)
        assertTrue('T' in s && f.accepts(s))
    }

    @Test
    fun `missingRequired should hanya memuat field wajib yang kosong atau tak ada`() {
        val e = EntitySpec("e", "E", listOf(
            FieldSpec("a", "A", FieldType.TEXT, required = true),
            FieldSpec("b", "B", FieldType.TEXT, required = true),
            FieldSpec("c", "C", FieldType.TEXT, required = true),
            FieldSpec("d", "D", FieldType.TEXT)
        ))
        assertEquals(listOf("b", "c"), SampleValues.missingRequired(e, mapOf("a" to "x", "b" to " ")).map { it.key })
    }
}

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

    // ── status awal ────────────────────────────────────────────────────────────

    private fun machineEntity(options: List<String>, transitions: Map<String, Set<String>>) = EntitySpec(
        "antrean", "Antrean",
        listOf(FieldSpec("pasien", "Pasien", FieldType.TEXT), FieldSpec("status", "Status", FieldType.ENUM, options = opts(*options.toTypedArray()))),
        StateMachine("status", transitions)
    )

    @Test
    fun `status awal should keadaan sumber-saja dan bukan opsi pertama`() {
        // Opsi pertama "Selesai" adalah tujuan akhir; yang benar-benar awal adalah "Menunggu".
        val e = machineEntity(listOf("Selesai", "Menunggu", "Dipanggil"), mapOf("Menunggu" to setOf("Dipanggil"), "Dipanggil" to setOf("Selesai")))
        assertEquals("Menunggu", SampleValues.initialState(e))
    }

    @Test
    fun `status awal should kembali ke opsi pertama bila setiap keadaan adalah tujuan`() {
        val e = machineEntity(listOf("A", "B"), mapOf("A" to setOf("B"), "B" to setOf("A")))
        assertEquals("A", SampleValues.initialState(e))
    }

    @Test
    fun `entitas tanpa mesin status should tidak punya status awal dan nilai tak berubah`() {
        val e = EntitySpec("e", "E", listOf(FieldSpec("a", "A", FieldType.TEXT)))
        assertNull(SampleValues.initialState(e))
        assertEquals(mapOf("a" to "x"), SampleValues.withInitialState(e, mapOf("a" to "x")))
    }

    @Test
    fun `withInitialState should mengisi status kosong tetapi tidak menimpa yang sudah ada`() {
        val e = machineEntity(listOf("Menunggu", "Dipanggil"), mapOf("Menunggu" to setOf("Dipanggil")))
        assertEquals("Menunggu", SampleValues.withInitialState(e, mapOf("pasien" to "Sari"))["status"])
        assertEquals("Menunggu", SampleValues.withInitialState(e, mapOf("status" to " "))["status"])
        assertEquals("Dipanggil", SampleValues.withInitialState(e, mapOf("status" to "Dipanggil"))["status"])
    }
}

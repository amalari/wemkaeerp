package com.eventverse.app.presentation.discovery

import com.eventverse.app.domain.prototype.FieldSpec
import com.eventverse.app.domain.prototype.FieldType
import com.eventverse.app.presentation.discovery.fields.displayValue
import com.eventverse.app.presentation.discovery.fields.multiSelectToggleValue
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Logika murni tipe `MULTI_SELECT` di lapisan UI (TRD-FIELD-003 Track C, FR-9) — tanpa Compose:
 * bentuk tampil baca [displayValue] dan penyusunan nilai kanonik [multiSelectToggleValue].
 */
class MultiSelectFieldSupportTest {

    private val alergi = FieldSpec(
        key = "alergi",
        label = "Alergi",
        type = FieldType.MULTI_SELECT,
        options = listOf("Gigi", "Jantung", "Kulit")
    )

    // ── displayValue: tampilan baca = daftar label dipisah ", " ──────────────────────────────

    @Test
    fun displayValue_joinsLabelsWithComma() {
        assertEquals("Gigi, Jantung", alergi.displayValue("""["Gigi","Jantung"]"""))
        assertEquals("Kulit", alergi.displayValue("""["Kulit"]"""))
    }

    @Test
    fun displayValue_emptyShowsDash() {
        assertEquals("-", alergi.displayValue(""))
        assertEquals("-", alergi.displayValue("[]"), "array kosong ditolak codec; tampil sebagai belum ada pilihan")
    }

    @Test
    fun displayValue_invalidRawShownAsIs_notHidden() {
        // Nilai tak sah (bukan array JSON) tidak disembunyikan — pola DATE/LONG_TEXT.
        assertEquals("nilai-lama", alergi.displayValue("nilai-lama"))
    }

    // ── multiSelectToggleValue: penyusunan nilai kanonik (urut menurut options) ──────────────

    @Test
    fun toggle_addsInOptionsOrder_notClickOrder() {
        var value = multiSelectToggleValue("", "Kulit", alergi.options)
        assertEquals("""["Kulit"]""", value)
        value = multiSelectToggleValue(value, "Gigi", alergi.options)
        assertEquals("""["Gigi","Kulit"]""", value, "urutan mengikuti options, bukan urutan klik")
    }

    @Test
    fun toggle_removesSelectedAndReturnsEmptyWhenNone() {
        var value = multiSelectToggleValue("""["Gigi","Kulit"]""", "Gigi", alergi.options)
        assertEquals("""["Kulit"]""", value)
        value = multiSelectToggleValue(value, "Kulit", alergi.options)
        assertEquals("", value, "tanpa pilihan = string kosong, bukan []")
    }

    @Test
    fun toggle_dropsUnknownAndKeepsCanonicalForm() {
        // Nilai masuk berisi opsi tak dikenal & tak berurut; encode membuang yang tak sah & menata ulang.
        val value = multiSelectToggleValue("""["Kulit","Asing"]""", "Jantung", alergi.options)
        assertEquals("""["Jantung","Kulit"]""", value)
    }
}

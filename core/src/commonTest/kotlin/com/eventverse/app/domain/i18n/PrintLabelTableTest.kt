package com.eventverse.app.domain.i18n

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * B4 (PLAN-i18n Fase 1, Track B): tabel label cetak punya padanan id/en untuk **setiap** kunci,
 * dan string `id` identik dengan literal lama (paritas byte-per-byte untuk tes renderer).
 */
class PrintLabelTableTest {

    @Test
    fun `setiap kunci punya padanan id dan en`() {
        PrintLabelKey.entries.forEach { key ->
            assertTrue(
                PrintLabelTable.isTranslated(key),
                "Kunci label cetak $key belum diterjemahkan ke 'en'"
            )
            val id = PrintLabelTable.label(key, LanguageCode.ID)
            val en = PrintLabelTable.label(key, LanguageCode.EN)
            assertTrue(id.isNotBlank(), "Label id untuk $key kosong")
            assertTrue(en.isNotBlank(), "Label en untuk $key kosong")
            assertNotEquals(key.name, id, "Label id untuk $key harus lebih dari nama kunci")
            assertNotEquals(key.name, en, "Label en untuk $key harus lebih dari nama kunci")
        }
    }

    @Test
    fun `string id identik dengan literal lama`() {
        assertEquals("REV", PrintLabelTable.label(PrintLabelKey.SPK_REV, LanguageCode.ID))
        assertEquals(
            "UKURAN JADI — SPEK CLIENT (POM)",
            PrintLabelTable.label(PrintLabelKey.SPK_POM_TITLE, LanguageCode.ID)
        )
        assertEquals(
            "HASIL UKURAN TIM SAMPLING",
            PrintLabelTable.label(PrintLabelKey.SPK_SAMPLING_TITLE, LanguageCode.ID)
        )
        assertEquals("JUMLAH", PrintLabelTable.label(PrintLabelKey.SPK_QTY, LanguageCode.ID))
        assertEquals(
            "LEMBAR KERJA RAJUT — SIZE",
            PrintLabelTable.label(PrintLabelKey.KNIT_TITLE, LanguageCode.ID)
        )
        assertEquals("Shift / Tanggal", PrintLabelTable.label(PrintLabelKey.WL_SHIFT_DATE, LanguageCode.ID))
    }

    @Test
    fun `terjemahan en berbeda untuk label yang memang berbeda`() {
        assertEquals("QTY", PrintLabelTable.label(PrintLabelKey.SPK_QTY, LanguageCode.EN))
        assertNotEquals(
            PrintLabelTable.label(PrintLabelKey.SPK_POM_TITLE, LanguageCode.ID),
            PrintLabelTable.label(PrintLabelKey.SPK_POM_TITLE, LanguageCode.EN)
        )
        assertEquals("Quantity (pcs)", PrintLabelTable.label(PrintLabelKey.WL_QTY_PCS, LanguageCode.EN))
    }
}

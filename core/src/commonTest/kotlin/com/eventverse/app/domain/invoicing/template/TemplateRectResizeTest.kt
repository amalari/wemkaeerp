package com.eventverse.app.domain.invoicing.template

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Perubahan lebar elemen.
 *
 * Terpisah dari [TemplateRectMovementTest] karena artinya berbeda: perpindahan menggeser, fungsi ini
 * mengubah ukuran. Elemen teks hanya boleh diubah lebarnya — tingginya turunan dari isi teks — sehingga
 * kedua aturan tidak boleh berbagi satu fungsi yang sama.
 */
class TemplateRectResizeTest {

    private val a4 = PaperSize.A4
    private val element = TemplateRect(Mm10(500), Mm10(1000), Mm10(600), Mm10(80))
    private val minWidth = InvoiceTemplateDefaults.MIN_TEXT_WIDTH_MM10

    @Test
    fun `wider than the paper edge is clamped to the remaining space`() {
        val resized = element.resizedWidth(Mm10(99_999), minWidthMm10 = minWidth, paperWidth = a4.width)

        assertEquals(
            a4.width.value - element.x.value,
            resized.width.value,
            "Lebar tidak boleh melewati tepi kanan kertas"
        )
        assertEquals(element.x, resized.x, "Mengubah lebar tidak boleh menggeser elemen")
        assertEquals(element.y, resized.y)
        assertEquals(element.height, resized.height, "Tinggi bukan urusan pengubahan lebar")
    }

    @Test
    fun `narrower than the readable minimum is clamped up`() {
        val resized = element.resizedWidth(Mm10(1), minWidthMm10 = minWidth, paperWidth = a4.width)

        assertEquals(minWidth, resized.width.value)
    }

    @Test
    fun `a width that fits is applied exactly`() {
        val resized = element.resizedWidth(Mm10(1234), minWidthMm10 = minWidth, paperWidth = a4.width)

        assertEquals(1234, resized.width.value)
    }

    @Test
    fun `element flush against the right edge cannot shrink below the minimum`() {
        val flush = TemplateRect(Mm10(a4.width.value), Mm10(1000), Mm10(10), Mm10(80))

        val resized = flush.resizedWidth(Mm10(500), minWidthMm10 = minWidth, paperWidth = a4.width)

        // Sisa ruang ke tepi = 0, tetapi lebar minimum tetap dihormati supaya elemennya tidak lenyap
        // menjadi garis tak terlihat yang tidak bisa dipilih lagi di kanvas.
        assertEquals(minWidth, resized.width.value)
    }
}

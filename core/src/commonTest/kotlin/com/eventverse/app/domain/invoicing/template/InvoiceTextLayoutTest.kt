package com.eventverse.app.domain.invoicing.template

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pemecahan baris teks faktur.
 *
 * Kelas ini adalah satu-satunya sumber kebenaran untuk titik potong baris: kanvas Compose dan renderer
 * PDFBox sama-sama menggambar daftar baris hasilnya apa adanya. Salah di sini berarti dokumen di layar
 * dan di kertas berbeda struktur — bug yang tidak bisa ditangkap tes UI mana pun.
 */
class InvoiceTextLayoutTest {

    private val body = TextStyleSpec(fontSizePt = 10)

    @Test
    fun `short text fits in one line`() {
        val lines = InvoiceTextLayout.wrap("Terima kasih", widthMm10 = 1000, style = body)

        assertEquals(listOf("Terima kasih"), lines)
    }

    @Test
    fun `long text wraps into multiple lines that each fit the box`() {
        val text = "Pembayaran dilakukan melalui transfer bank ke rekening perusahaan " +
            "paling lambat empat belas hari setelah faktur diterbitkan"

        val lines = InvoiceTextLayout.wrap(text, widthMm10 = 600, style = body)

        assertTrue(lines.size > 2, "Teks panjang pada kotak sempit harus terpecah, bukan meluber")
        lines.forEach { line ->
            assertTrue(
                InvoiceTextLayout.estimateWidthMm10(line, body) <= 600,
                "Baris '$line' lebih lebar dari kotaknya"
            )
        }
    }

    @Test
    fun `explicit newline always starts a new line`() {
        val lines = InvoiceTextLayout.wrap("Baris satu\nBaris dua\nBaris tiga", widthMm10 = 2000, style = body)

        assertEquals(listOf("Baris satu", "Baris dua", "Baris tiga"), lines)
    }

    @Test
    fun `empty line between paragraphs is preserved`() {
        val lines = InvoiceTextLayout.wrap("Paragraf satu\n\nParagraf dua", widthMm10 = 2000, style = body)

        assertEquals(listOf("Paragraf satu", "", "Paragraf dua"), lines)
    }

    @Test
    fun `blank text still produces one measurable line`() {
        // Kotak teks kosong harus tetap punya tinggi, kalau tidak elemennya tidak bisa dipilih
        // maupun ditarik di kanvas.
        val lines = InvoiceTextLayout.wrap("", widthMm10 = 800, style = body)

        assertEquals(listOf(""), lines)
        assertTrue(InvoiceTextLayout.measureHeightMm10("", 800, body) > 0)
    }

    @Test
    fun `single unbroken token longer than the box is split by character`() {
        // Nomor faktur seperti INV/2026/03/0001 tidak punya spasi sama sekali. Tanpa pemecahan per
        // karakter, teks ini akan menjulur keluar tepi kanan kertas dan terpotong saat dicetak.
        val token = "INV/2026/03/0001/EXTREMELY/LONG/REFERENCE"

        val lines = InvoiceTextLayout.wrap(token, widthMm10 = 300, style = body)

        assertTrue(lines.size > 1, "Token panjang tanpa spasi wajib dipecah")
        assertEquals(token, lines.joinToString(""), "Isi teks tidak boleh hilang saat dipecah")
    }

    @Test
    fun `wider box never produces more lines than a narrower one`() {
        val text = "Rincian pekerjaan jahit dan bordir untuk pesanan seragam lapangan"

        val narrow = InvoiceTextLayout.wrap(text, widthMm10 = 500, style = body)
        val wide = InvoiceTextLayout.wrap(text, widthMm10 = 2000, style = body)

        assertTrue(
            wide.size <= narrow.size,
            "Melebarkan kotak tidak boleh menambah baris (sempit=${narrow.size}, lebar=${wide.size})"
        )
    }

    @Test
    fun `bigger font needs at least as many lines`() {
        val text = "Syarat dan ketentuan pembayaran faktur ini berlaku sejak tanggal penerbitan"
        val small = InvoiceTextLayout.wrap(text, widthMm10 = 900, style = TextStyleSpec(fontSizePt = 8))
        val large = InvoiceTextLayout.wrap(text, widthMm10 = 900, style = TextStyleSpec(fontSizePt = 16))

        assertTrue(large.size >= small.size, "Font lebih besar tidak boleh menghasilkan baris lebih sedikit")
    }

    @Test
    fun `line height follows the shared ratio`() {
        assertEquals(48, InvoiceTextLayout.lineHeightMm10(TextStyleSpec(fontSizePt = 10)))
        assertEquals(95, InvoiceTextLayout.lineHeightMm10(TextStyleSpec(fontSizePt = 20)))
    }

    @Test
    fun `measure height equals line count times line height`() {
        val text = "Baris satu\nBaris dua"
        val expected = 2 * InvoiceTextLayout.lineHeightMm10(body)

        assertEquals(expected, InvoiceTextLayout.measureHeightMm10(text, widthMm10 = 1200, style = body))
    }
}

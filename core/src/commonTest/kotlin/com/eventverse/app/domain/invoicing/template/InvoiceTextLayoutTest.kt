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
                InvoiceTextLayout.measureWidthMm10(line, body) <= 600,
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
    fun `currency string that nearly fills its box is not declared to fit`() {
        // Regresi atas taksiran lebar lama. Digit Nunito lebarnya 0.600 em, tetapi ditaksir 0.560 em —
        // 6.7% terlalu sempit, dan digit adalah isi utama kolom uang. Pada kotak 233 Mm10 taksiran lama
        // menyatakan teks ini muat satu baris, padahal nyatanya melampaui kotaknya. Di PDF luapan itu
        // tidak terlihat sebagai teks terpotong melainkan sebagai angka yang kehilangan rata kanannya,
        // karena penjepit `(widthPt - textWidth).coerceAtLeast(0f)` memaksa offsetnya ke nol.
        val money = "Rp 12.500.000"
        val boxMm10 = 233

        val lines = InvoiceTextLayout.wrap(money, widthMm10 = boxMm10, style = body)

        // Inilah perubahan perilakunya: taksiran lama menghasilkan 6.510 em (229.7 Mm10) dan
        // menyatakan satu baris cukup; metrik Nunito yang sebenarnya 6.864 em (242.1 Mm10) dan
        // tidak cukup. Tanpa penegasan ini, tes hanya memeriksa dirinya sendiri — pengukur dan
        // pemecah baris memakai tabel yang sama, jadi keduanya akan selalu "sepakat" walau salah.
        assertEquals(listOf("Rp", "12.500.000"), lines)

        lines.forEach { line ->
            assertTrue(
                InvoiceTextLayout.measureWidthMm10(line, body) <= boxMm10,
                "Baris '$line' (${InvoiceTextLayout.measureWidthMm10(line, body)} Mm10) meluber dari kotak $boxMm10 Mm10"
            )
        }
    }

    @Test
    fun `digit width comes from the font, not from a character class`() {
        // Sepuluh digit Nunito Regular = 10 × 0.600 em. Pada 10pt itu 6.0 em × 35.278 Mm10/em.
        // Taksiran lama menghasilkan 0.560 em per digit dan akan gagal di sini.
        val tenDigits = "0123456789"
        val expected = (6.0 * 10 * InvoiceTextLayout.MM10_PER_PT).toInt()

        val actual = InvoiceTextLayout.measureWidthMm10(tenDigits, body)

        assertTrue(
            actual in (expected - 1)..(expected + 1),
            "Lebar sepuluh digit seharusnya ~$expected Mm10, bukan $actual Mm10"
        )
    }

    @Test
    fun `heading sized text is measured with Fredoka, not Nunito`() {
        // Ambang 14pt memindahkan teks ke Fredoka di renderer PDF. Kalau pemecah baris tidak ikut
        // berpindah, ia mengukur Nunito untuk teks yang digambar Fredoka — dan `W` di Nunito 16%
        // lebih lebar daripada di Fredoka, cukup untuk menggeser titik potong baris judul.
        val heading = TextStyleSpec(fontSizePt = 14)
        val bodyAtSameSize = TextStyleSpec(fontSizePt = 13)

        assertEquals(InvoiceFont.FREDOKA_MEDIUM, InvoiceFontResolver.resolve(heading))
        assertEquals(InvoiceFont.NUNITO_REGULAR, InvoiceFontResolver.resolve(bodyAtSameSize))

        val fredokaW = InvoiceFontMetrics.advanceEm(InvoiceFont.FREDOKA_MEDIUM, 'W')
        val nunitoW = InvoiceFontMetrics.advanceEm(InvoiceFont.NUNITO_REGULAR, 'W')
        assertTrue(fredokaW < nunitoW, "Prasyarat tes: W Fredoka memang lebih sempit dari W Nunito")
    }

    @Test
    fun `unknown characters are measured wide rather than narrow`() {
        // Arah kesalahan yang aman: taksiran kelebaran hanya memecah baris lebih awal, taksiran
        // kesempitan membuat teks meluber keluar kotak tanpa terlihat sampai faktur dicetak.
        val exotic = '中' // aksara Han, pasti di luar tabel
        val widest = InvoiceFontMetrics.advanceEm(InvoiceFont.NUNITO_REGULAR, 'M')

        assertEquals(widest, InvoiceFontMetrics.advanceEm(InvoiceFont.NUNITO_REGULAR, exotic))
    }

    @Test
    fun `measure height equals line count times line height`() {
        val text = "Baris satu\nBaris dua"
        val expected = 2 * InvoiceTextLayout.lineHeightMm10(body)

        assertEquals(expected, InvoiceTextLayout.measureHeightMm10(text, widthMm10 = 1200, style = body))
    }
}

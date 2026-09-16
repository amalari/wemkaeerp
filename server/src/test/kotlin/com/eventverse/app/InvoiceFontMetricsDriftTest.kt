package com.eventverse.app

import com.eventverse.app.domain.invoicing.template.InvoiceFont
import com.eventverse.app.domain.invoicing.template.InvoiceFontMetrics
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.font.PDFont
import org.apache.pdfbox.pdmodel.font.PDType0Font
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Menjaga [InvoiceFontMetrics] tetap jujur terhadap berkas font yang sebenarnya dipaketkan.
 *
 * ## Kenapa tes ini wajib ada
 *
 * [InvoiceFontMetrics] adalah tabel angka yang di-*check in*, bukan dibangkitkan saat build —
 * `core` berjalan juga di JS dan Wasm yang tidak bisa membaca TTF. Konsekuensinya, tabel itu bisa
 * membusuk tanpa suara: siapa pun yang mengganti `nunito_regular.ttf` (memutakhirkan versi font,
 * menukar subset, menambah glyph) akan membuat seluruh perhitungan pemecahan baris meleset, dan
 * gejalanya baru muncul sebagai teks meluber di PDF yang sudah dikirim ke klien.
 *
 * Tes ini menutup celah itu dengan membandingkan setiap entri tabel terhadap `PDFont.getStringWidth`
 * — **jalur yang persis dipakai [com.eventverse.app.infrastructure.pdf.InvoicePdfRenderer]
 * menggambar**. Bukan terhadap pustaka metrik lain, karena yang harus cocok bukanlah "kebenaran
 * tipografi" melainkan angka yang dipakai mesin gambarnya sendiri.
 */
class InvoiceFontMetricsDriftTest {

    private val asciiRange = 32..126

    private fun resourcePath(font: InvoiceFont) = "/fonts/${font.resourceName}.ttf"

    /** Titik kode sebagai `U+XXXX`. Dirakit tangan karena `String.format` akan tersedak pada '%'. */
    private fun hex(code: Int) = "U+" + code.toString(16).uppercase().padStart(4, '0')

    private fun <T> withFont(font: InvoiceFont, block: (PDFont) -> T): T =
        PDDocument().use { doc ->
            val stream = javaClass.getResourceAsStream(resourcePath(font))
            assertNotNull(stream, "Berkas font ${resourcePath(font)} tidak ada di resources server")
            block(PDType0Font.load(doc, stream))
        }

    @Test
    fun `tabel ASCII cocok dengan metrik PDFBox untuk seluruh font`() {
        InvoiceFont.entries.forEach { font ->
            withFont(font) { pdFont ->
                asciiRange.forEach { code ->
                    val char = code.toChar()
                    val expected = pdFont.getStringWidth(char.toString()).roundToInt()
                    val actual = InvoiceFontMetrics.advanceUnits(font, char)

                    assertEquals(
                        expected,
                        actual,
                        "Lebar '$char' (${hex(code)}) pada $font meleset. " +
                            "Berkas font berubah? Bangkitkan ulang tabel di InvoiceFontMetrics."
                    )
                }
            }
        }
    }

    @Test
    fun `entri non-ASCII tambahan cocok dengan metrik PDFBox`() {
        // Karakter yang sengaja diberi entri eksplisit karena sempit dan sering muncul di teks
        // tempelan (tanda kutip melengkung, en/em dash, elipsis). Kalau salah satunya hilang dari
        // tabel, ia jatuh ke lebar 'M' dan baris terpecah jauh lebih awal dari seharusnya.
        val extras = listOf(
            ' ', '°', '×', '£', '€', '–', '—',
            '‘', '’', '“', '”', '…', 'é', 'ñ'
        )

        InvoiceFont.entries.forEach { font ->
            withFont(font) { pdFont ->
                extras.forEach { char ->
                    val expected = pdFont.getStringWidth(char.toString()).roundToInt()
                    val actual = InvoiceFontMetrics.advanceUnits(font, char)

                    assertEquals(
                        expected,
                        actual,
                        "Lebar ${hex(char.code)} pada $font meleset dari PDFBox"
                    )
                }
            }
        }
    }

    @Test
    fun `lebar sederet karakter sama dengan lebar PDFBox untuk seluruh string`() {
        // Menguji penjumlahannya, bukan hanya entri satuannya: kalau suatu saat kerning ikut
        // diperhitungkan di salah satu sisi, ketidakcocokannya muncul di sini lebih dulu.
        val samples = listOf(
            "Rp 12.500.000",
            "INV/2026/03/0001",
            "PT Sinar Jaya Konveksi Nusantara",
            "Kaos Polo Lengan Panjang Premium",
            "TOTAL TAGIHAN"
        )

        InvoiceFont.entries.forEach { font ->
            withFont(font) { pdFont ->
                samples.forEach { text ->
                    val expected = pdFont.getStringWidth(text) / InvoiceFontMetrics.UNITS_PER_EM
                    val actual = InvoiceFontMetrics.advanceEm(font, text)

                    assertTrue(
                        kotlin.math.abs(expected - actual) < 0.05,
                        "Lebar \"$text\" pada $font: PDFBox=$expected tabel=$actual"
                    )
                }
            }
        }
    }

    @Test
    fun `setiap berkas font yang dideklarasikan benar-benar terpaket`() {
        InvoiceFont.entries.forEach { font ->
            assertNotNull(
                javaClass.getResourceAsStream(resourcePath(font)),
                "${font.resourceName}.ttf hilang — renderer akan diam-diam jatuh ke Helvetica " +
                    "sementara pemecah baris tetap mengukur ${font.name}"
            )
        }
    }
}

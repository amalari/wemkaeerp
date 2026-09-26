package com.eventverse.app.infrastructure.pdf

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.font.PDFont
import org.apache.pdfbox.pdmodel.font.PDType0Font
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import java.io.InputStream

/**
 * Pemuatan font untuk seluruh dokumen cetak.
 *
 * Diangkat dari [InvoicePdfRenderer] begitu ada dokumen kedua yang mencetak teks. Font yang gagal
 * dimuat jatuh ke Helvetica, bukan melempar: lembar kerja tanpa Nunito masih bisa dipakai operator,
 * sedangkan lembar kerja yang gagal tercetak sama sekali menghentikan satu shift.
 */
object PdfFonts {

    fun loadTtf(doc: PDDocument, resourcePath: String): PDFont? = try {
        val stream: InputStream? = PdfFonts::class.java.getResourceAsStream(resourcePath)
        stream?.let { PDType0Font.load(doc, it) }
    } catch (_: Exception) {
        null
    }

    fun bodyRegular(doc: PDDocument): PDFont =
        loadTtf(doc, "/fonts/nunito_regular.ttf") ?: PDType1Font(Standard14Fonts.FontName.HELVETICA)

    fun bodyBold(doc: PDDocument): PDFont =
        loadTtf(doc, "/fonts/nunito_bold.ttf") ?: PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD)
}

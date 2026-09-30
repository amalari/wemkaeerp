package com.eventverse.app.infrastructure.pdf

import com.eventverse.app.domain.discovery.print.BlueprintLine
import com.eventverse.app.domain.discovery.print.BlueprintLineRole
import com.eventverse.app.domain.discovery.print.BlueprintSheet
import com.eventverse.app.domain.discovery.print.BlueprintWatermark
import com.eventverse.app.infrastructure.pdf.PdfUnits.toPoints
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDFont
import org.apache.pdfbox.util.Matrix
import java.io.ByteArrayOutputStream

/**
 * Mencetak blueprint sistem ber-watermark ke A4 (plan §5, Fase D).
 *
 * Renderer ini **tidak** menghitung apa pun: seluruh posisi, pemotongan baris, dan pemenggalan
 * halaman sudah diputuskan `BlueprintSheet`. Itu disengaja — dokumen yang sama kelak digambar ulang di
 * kanvas Compose untuk pratinjau, dan satu-satunya cara keduanya identik adalah bila tidak ada mesin
 * pengukur kedua di sisi PDF.
 *
 * ## Kenapa watermarknya besar, miring, dan di tengah
 *
 * Watermark kecil di kaki halaman terbaca sebagai catatan kaki, bukan sebagai status dokumen — dan
 * berkas ini beredar lewat WhatsApp prospek, tempat ia dibaca dari layar ponsel tanpa zoom. Teks
 * diagonal besar di tengah kertas tidak bisa tidak terlihat, dan tetap tidak menutupi isi karena
 * dicetak abu-abu sangat muda (0,88) **sebelum** teks lain digambar.
 */
class BlueprintPdfRenderer {

    private companion object {
        /** Abu-abu sangat muda: terbaca sebagai penanda, tapi tidak mengganggu teks di atasnya. */
        const val WATERMARK_GRAY = 0.88f
        const val RULE_GRAY = 0.70f
        const val BODY_GRAY = 0.15f
        const val MUTED_GRAY = 0.45f
        const val BADGE_PADDING_PT = 4f

        /**
         * Padanan ASCII untuk karakter yang **terbukti** tidak ada di font cetak yang dibundel.
         *
         * Daftarnya sengaja pendek. `PDFBox.showText` melempar `IllegalStateException` untuk glyph yang
         * absen, dan isi PDF ini sebagian berasal dari kosakata pack tenant — satu karakter aneh tidak
         * boleh menggagalkan seluruh dokumen. Tapi memetakan karakter yang **ada** juga merugikan:
         * lebar teks yang dihitung domain (lewat `InvoiceTextLayout`) memakai karakter aslinya,
         * sedangkan yang tercetak jadi lebih pendek — baris bisa terlihat berbeda dari rencananya.
         *
         * Hasil penyelidikan `nunito_regular.ttf`/`nunito_bold.ttf`: panah `→` dan centang `✓` absen;
         * em dash, en dash, titik tengah, bullet, kutip lengkung, elipsis, `×`, dan huruf beraksen
         * (é, ü) semua ada. Karena itu hanya keduanya yang dipetakan; sisanya diserahkan ke `?`.
         */
        val ASCII_FALLBACK: Map<Char, String> = mapOf('→' to "->", '✓' to "v")
    }

    fun render(sheet: BlueprintSheet): ByteArray {
        PDDocument().use { doc ->
            val regular = PdfFonts.bodyRegular(doc)
            val bold = PdfFonts.bodyBold(doc)
            val pageHeightPt = PDRectangle.A4.height

            sheet.pages.forEach { page ->
                val pdfPage = PDPage(PDRectangle.A4)
                doc.addPage(pdfPage)
                PDPageContentStream(doc, pdfPage).use { cs ->
                    drawWatermark(cs, page.watermark, bold, pageHeightPt)
                    page.body.forEach { line -> drawLine(cs, line, regular, bold, pageHeightPt) }
                    page.footer.firstOrNull()?.let { drawFooterRule(cs, it, pageHeightPt) }
                    page.footer.forEach { line -> drawLine(cs, line, regular, bold, pageHeightPt) }
                }
            }

            val out = ByteArrayOutputStream()
            doc.save(out)
            return out.toByteArray()
        }
    }

    private fun drawWatermark(
        cs: PDPageContentStream,
        watermark: BlueprintWatermark,
        font: PDFont,
        pageHeightPt: Float
    ) {
        if (watermark.text.isBlank()) return
        val printable = encodeSafe(font, watermark.text)
        val centerXPt = toPoints(watermark.center.x.value)
        // Pusat kertas dalam koordinat PDF (asal kiri-bawah, sedangkan layout memakai asal kiri-atas).
        val centerYPt = pageHeightPt - toPoints(watermark.center.y.value)
        val size = watermark.fontSizePt.toFloat()
        val widthPt = textWidthPt(font, printable, size)

        cs.saveGraphicsState()
        cs.setNonStrokingColor(WATERMARK_GRAY, WATERMARK_GRAY, WATERMARK_GRAY)
        cs.transform(
            Matrix.getRotateInstance(
                Math.toRadians(watermark.angleDeg.toDouble()),
                centerXPt,
                centerYPt
            )
        )
        cs.beginText()
        cs.setFont(font, size)
        // Titik asal gambar sudah diputar, jadi teks digeser setengah lebar & setengah tinggi supaya
        // pusatnya jatuh di pusat kertas — bukan sudut kirinya.
        cs.newLineAtOffset(-widthPt / 2f, -size / 2f)
        cs.showText(printable)
        cs.endText()
        cs.restoreGraphicsState()
    }

    private fun drawLine(
        cs: PDPageContentStream,
        line: BlueprintLine,
        regular: PDFont,
        bold: PDFont,
        pageHeightPt: Float
    ) {
        if (line.text.isBlank()) return
        val font = if (line.role.isBold) bold else regular
        val size = line.role.sizePt.toFloat()
        val printable = encodeSafe(font, line.text)
        val xPt = toPoints(line.rect.x.value)
        val topPt = pageHeightPt - toPoints(line.rect.y.value)

        if (line.role == BlueprintLineRole.BADGE) drawBadge(cs, printable, font, size, xPt, topPt)

        val gray = when (line.role) {
            BlueprintLineRole.TITLE, BlueprintLineRole.HEADING, BlueprintLineRole.BADGE -> 0f
            BlueprintLineRole.BODY, BlueprintLineRole.ROW, BlueprintLineRole.DETAIL -> BODY_GRAY
            BlueprintLineRole.SUBTITLE -> 0.30f
            BlueprintLineRole.NOTE, BlueprintLineRole.FOOTER -> MUTED_GRAY
        }
        cs.setNonStrokingColor(gray, gray, gray)
        cs.beginText()
        cs.setFont(font, size)
        cs.newLineAtOffset(xPt, topPt - size)
        cs.showText(printable)
        cs.endText()
    }

    /** Kotak tipis di sekeliling baris badge: menandai "ini nama paketnya", bukan isi paragraf. */
    private fun drawBadge(
        cs: PDPageContentStream,
        text: String,
        font: PDFont,
        size: Float,
        xPt: Float,
        topPt: Float
    ) {
        val widthPt = textWidthPt(font, text, size)
        cs.setStrokingColor(RULE_GRAY, RULE_GRAY, RULE_GRAY)
        cs.setLineWidth(0.7f)
        cs.addRect(
            xPt - BADGE_PADDING_PT,
            topPt - size - BADGE_PADDING_PT,
            widthPt + 2 * BADGE_PADDING_PT,
            size + 2 * BADGE_PADDING_PT
        )
        cs.stroke()
    }

    /** Garis pemisah badan dokumen dan catatan kaki. */
    private fun drawFooterRule(cs: PDPageContentStream, firstFooterLine: BlueprintLine, pageHeightPt: Float) {
        cs.setStrokingColor(RULE_GRAY, RULE_GRAY, RULE_GRAY)
        cs.setLineWidth(0.5f)
        val y = pageHeightPt - toPoints(firstFooterLine.rect.y.value) + 4f
        cs.moveTo(toPoints(firstFooterLine.rect.x.value), y)
        cs.lineTo(toPoints(firstFooterLine.rect.right.value), y)
        cs.stroke()
    }

    /** Lebar teks dalam titik; teks yang tak bisa diukur fontnya jatuh ke 0, bukan melempar. */
    private fun textWidthPt(font: PDFont, text: String, size: Float): Float =
        runCatching { font.getStringWidth(text) / 1000f * size }.getOrDefault(0f)

    /**
     * Teks yang dijamin bisa dicetak font ini.
     *
     * Dipanggil untuk **setiap** teks, termasuk watermark: satu karakter tanpa glyph membuat
     * `showText` melempar, dan PDF yang gagal digambar berarti prospek tidak menerima apa pun —
     * jauh lebih buruk daripada satu tanda `?` di namanya.
     */
    private fun encodeSafe(font: PDFont, text: String): String = buildString {
        text.forEach { ch ->
            val candidate = ASCII_FALLBACK[ch] ?: ch.toString()
            append(if (canEncode(font, candidate)) candidate else "?")
        }
    }

    private fun canEncode(font: PDFont, text: String): Boolean =
        runCatching { font.getStringWidth(text) }.isSuccess
}

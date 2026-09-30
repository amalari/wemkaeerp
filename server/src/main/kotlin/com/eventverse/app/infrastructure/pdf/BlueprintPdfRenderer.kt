package com.eventverse.app.infrastructure.pdf

import com.eventverse.app.domain.discovery.print.BlueprintLine
import com.eventverse.app.domain.discovery.print.BlueprintLineRole
import com.eventverse.app.domain.discovery.print.BlueprintSheet
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
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
 *
 * ## Pembagian kerja
 *
 * Menggambar koordinat, memetakan glyph yang absen, dan memutar watermark kini milik
 * [PdfSheetPainter] — dipakai bersama dokumen platform lain (tagihan langganan). Yang tinggal di sini
 * hanya keputusan yang khas blueprint: peran baris → ukuran, bobot, dan kepekatan abu-abu.
 */
class BlueprintPdfRenderer {

    private companion object {
        /**
         * Abu-abu per peran. Sama seperti sebelumnya, tapi kini nilainya datang dari
         * [PdfSheetPainter] — dua dokumen platform tidak boleh punya dua skala abu-abu.
         */
        const val HEADING_GRAY = 0f
        const val SUBTITLE_GRAY = 0.30f
        const val BODY_GRAY = PdfSheetPainter.BODY_GRAY
        const val MUTED_GRAY = PdfSheetPainter.MUTED_GRAY
        const val RULE_GRAY = PdfSheetPainter.RULE_GRAY
    }

    fun render(sheet: BlueprintSheet): ByteArray {
        PDDocument().use { doc ->
            val painter = PdfSheetPainter(doc)

            sheet.pages.forEach { page ->
                val pdfPage = PDPage(PDRectangle.A4)
                doc.addPage(pdfPage)
                PDPageContentStream(doc, pdfPage).use { cs ->
                    painter.watermark(
                        cs = cs,
                        value = page.watermark.text,
                        centerXMm10 = page.watermark.center.x.value,
                        centerYMm10 = page.watermark.center.y.value,
                        sizePt = page.watermark.fontSizePt,
                        angleDeg = page.watermark.angleDeg
                    )
                    page.body.forEach { line -> drawLine(painter, cs, line) }
                    page.footer.firstOrNull()?.let { first ->
                        painter.rule(
                            cs = cs,
                            leftMm10 = first.rect.x.value,
                            rightMm10 = first.rect.right.value,
                            topMm10 = first.rect.y.value,
                            gray = RULE_GRAY,
                            offsetPt = 4f
                        )
                    }
                    page.footer.forEach { line -> drawLine(painter, cs, line) }
                }
            }

            val out = ByteArrayOutputStream()
            doc.save(out)
            return out.toByteArray()
        }
    }

    private fun drawLine(painter: PdfSheetPainter, cs: PDPageContentStream, line: BlueprintLine) {
        if (line.text.isBlank()) return
        val size = line.role.sizePt.toFloat()

        if (line.role == BlueprintLineRole.BADGE) {
            painter.box(
                cs = cs,
                value = line.text,
                xMm10 = line.rect.x.value,
                topMm10 = line.rect.y.value,
                sizePt = size,
                isBold = line.role.isBold,
                gray = RULE_GRAY
            )
        }

        painter.text(
            cs = cs,
            value = line.text,
            xMm10 = line.rect.x.value,
            topMm10 = line.rect.y.value,
            sizePt = size,
            isBold = line.role.isBold,
            gray = grayFor(line.role)
        )
    }

    private fun grayFor(role: BlueprintLineRole): Float = when (role) {
        BlueprintLineRole.TITLE, BlueprintLineRole.HEADING, BlueprintLineRole.BADGE -> HEADING_GRAY
        BlueprintLineRole.BODY, BlueprintLineRole.ROW, BlueprintLineRole.DETAIL -> BODY_GRAY
        BlueprintLineRole.SUBTITLE -> SUBTITLE_GRAY
        BlueprintLineRole.NOTE, BlueprintLineRole.FOOTER -> MUTED_GRAY
    }
}

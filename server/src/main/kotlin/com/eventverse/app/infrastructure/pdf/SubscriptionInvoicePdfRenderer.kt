package com.eventverse.app.infrastructure.pdf

import com.eventverse.app.domain.builder.print.SubscriptionInvoiceLine
import com.eventverse.app.domain.builder.print.SubscriptionInvoiceLineRole
import com.eventverse.app.domain.builder.print.SubscriptionInvoicePdfDocument
import com.eventverse.app.domain.builder.print.SubscriptionInvoiceSheet
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import java.io.ByteArrayOutputStream

/**
 * Mencetak tagihan langganan platform ke A4 (FR-M2-5b).
 *
 * Renderer ini **tidak** menghitung apa pun: posisi, pemotongan baris, perataan kolom angka, dan
 * pemenggalan halaman sudah diputuskan [SubscriptionInvoiceSheetLayout]. Yang tersisa di sini hanya
 * satu keputusan yang memang milik renderer: **rupa** — ukuran, bobot, dan skala abu-abu per peran.
 *
 * Watermark dicetak lebih dulu di setiap halaman dengan abu-abu sangat muda (0,88). Ia harus terbaca
 * dari layar ponsel — dokumen ini beredar lewat WhatsApp/email — tetapi tidak boleh menutupi angka
 * tagihan yang justru sedang diperiksa orang.
 */
class SubscriptionInvoicePdfRenderer {

    private companion object {
        /** Status yang "belum lunas" dicetak sedikit lebih gelap: itu yang paling perlu dilihat. */
        const val UNPAID_WATERMARK_GRAY = 0.80f
        const val PAID_WATERMARK_GRAY = 0.88f
        const val VOID_WATERMARK_GRAY = 0.72f

        /** Garis pemisah yang digambar di posisi baris peran [SubscriptionInvoiceLineRole.RULE]. */
        const val RULE_GRAY = PdfSheetPainter.RULE_GRAY
        const val BODY_GRAY = PdfSheetPainter.BODY_GRAY
        const val MUTED_GRAY = PdfSheetPainter.MUTED_GRAY
        const val HEADING_GRAY = 0.0f
    }

    fun render(sheet: SubscriptionInvoiceSheet): ByteArray {
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
                        angleDeg = page.watermark.angleDeg,
                        gray = watermarkGray(page.watermark.text)
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

    private fun drawLine(
        painter: PdfSheetPainter,
        cs: PDPageContentStream,
        line: SubscriptionInvoiceLine
    ) {
        // Baris garis: bukan teks, melainkan pemisah selebar kolom badan dokumen.
        if (line.role.isRule) {
            painter.rule(
                cs = cs,
                leftMm10 = line.rect.x.value,
                rightMm10 = line.rect.right.value,
                topMm10 = line.rect.y.value,
                gray = RULE_GRAY
            )
            return
        }
        if (line.text.isBlank()) return

        painter.text(
            cs = cs,
            value = line.text,
            xMm10 = line.rect.x.value,
            topMm10 = line.rect.y.value,
            sizePt = line.role.sizePt.toFloat(),
            isBold = line.role.isBold,
            gray = grayFor(line.role)
        )
    }

    private fun grayFor(role: SubscriptionInvoiceLineRole): Float = when (role) {
        SubscriptionInvoiceLineRole.TITLE,
        SubscriptionInvoiceLineRole.HEADING,
        SubscriptionInvoiceLineRole.COLUMN_LEFT,
        SubscriptionInvoiceLineRole.COLUMN_RIGHT,
        SubscriptionInvoiceLineRole.TOTAL_LABEL,
        SubscriptionInvoiceLineRole.TOTAL_AMOUNT -> HEADING_GRAY

        SubscriptionInvoiceLineRole.SUBTITLE -> 0.30f

        SubscriptionInvoiceLineRole.NOTE,
        SubscriptionInvoiceLineRole.FOOTER -> MUTED_GRAY

        else -> BODY_GRAY
    }

    /**
     * Status menentukan kepekatan watermark: `BELUM DIBAYAR` dan `DIBATALKAN` harus lebih terlihat
     * daripada `LUNAS`, karena keduanya yang mencegah orang mentransfer ke tagihan yang salah.
     *
     * Dibandingkan dengan konstanta dokumen, bukan literal baru: kalau kalimat watermarknya kelak
     * diubah di domain, di sini tidak ada salinan yang diam-diam jadi tidak cocok.
     */
    private fun watermarkGray(watermark: String): Float = when (watermark) {
        SubscriptionInvoicePdfDocument.WATERMARK_PAID -> PAID_WATERMARK_GRAY
        SubscriptionInvoicePdfDocument.WATERMARK_VOID -> VOID_WATERMARK_GRAY
        else -> UNPAID_WATERMARK_GRAY
    }
}

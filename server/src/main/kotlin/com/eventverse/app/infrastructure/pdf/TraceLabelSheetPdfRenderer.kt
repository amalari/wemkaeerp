package com.eventverse.app.infrastructure.pdf

import com.eventverse.app.domain.printing.Mm10
import com.eventverse.app.domain.printing.TemplateRect
import com.eventverse.app.domain.traceability.TraceCodec
import com.eventverse.app.domain.traceability.TraceTier
import com.eventverse.app.domain.traceability.print.LaidOutTraceLabel
import com.eventverse.app.domain.traceability.print.TraceLabelRegions
import com.eventverse.app.domain.traceability.print.TraceLabelSheet
import com.eventverse.app.infrastructure.pdf.PdfUnits.toPoints
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDFont
import java.io.ByteArrayOutputStream

/**
 * Mencetak setumpuk kartu telusur ke lembar A4 yang siap dipotong.
 *
 * Kartu dicetak lebih dulu, sebelum bundelnya ada. Itu keputusan yang sengaja diambil: bundel
 * terbentuk di akhir shift, dan menaruh printer di lantai rajut itu mahal dan rapuh. Konsekuensinya
 * kartu harus menyediakan kotak tulis tangan — saat jaringan pabrik putus, operator tetap bisa
 * mencatat di kertas dan menguncinya ke sistem keesokan paginya.
 */
class TraceLabelSheetPdfRenderer(private val scanHost: String) {

    private companion object {
        const val CUT_GUIDE_GRAY = 0.75f
        const val CAPTION_SIZE = 9f
        const val CODE_SIZE = 11f
        const val WRITING_LINE_SIZE = 8f
        const val MAX_LINE_GAP = 60
    }

    fun render(sheet: TraceLabelSheet, spkNumber: String, writingLines: List<String>): ByteArray {
        PDDocument().use { doc ->
            val regular = PdfFonts.bodyRegular(doc)
            val bold = PdfFonts.bodyBold(doc)
            val pageHeightPt = PDRectangle.A4.height

            val byPage = sheet.labels.groupBy { it.pageIndex }.toSortedMap()
            if (byPage.isEmpty()) doc.addPage(PDPage(PDRectangle.A4))

            byPage.forEach { (_, labels) ->
                val page = PDPage(PDRectangle.A4)
                doc.addPage(page)
                PDPageContentStream(doc, page).use { cs ->
                    labels.forEach { drawCard(cs, it, spkNumber, writingLines, regular, bold, pageHeightPt) }
                }
            }

            val out = ByteArrayOutputStream()
            doc.save(out)
            return out.toByteArray()
        }
    }

    private fun drawCard(
        cs: PDPageContentStream,
        laid: LaidOutTraceLabel,
        spkNumber: String,
        writingLines: List<String>,
        regular: PDFont,
        bold: PDFont,
        pageHeightPt: Float
    ) {
        val r = laid.regions

        // Garis potong tipis abu-abu, bukan hitam: ia panduan gunting, bukan bagian dari kartu, dan
        // garis hitam tebal menyisakan bekas di tepi setiap kartu yang dipotong sedikit meleset.
        cs.setStrokingColor(CUT_GUIDE_GRAY, CUT_GUIDE_GRAY, CUT_GUIDE_GRAY)
        cs.setLineWidth(0.4f)
        cs.addRect(
            toPoints(r.card.x.value),
            pageHeightPt - toPoints(r.card.bottom.value),
            toPoints(r.card.width.value),
            toPoints(r.card.height.value)
        )
        cs.stroke()

        QrCodeRenderer.draw(
            content = cs,
            payload = TraceCodec.toScanUrl(laid.plan.code, scanHost),
            xPt = toPoints(r.qr.x.value),
            topPt = pageHeightPt - toPoints(r.qr.y.value),
            sizePt = toPoints(r.qr.width.value)
        )

        drawText(cs, TraceCodec.grouped(laid.plan.code), bold, CODE_SIZE, r.humanCode, pageHeightPt)

        // Baris identitas manusiawi. Inilah yang membuat kartu tetap berguna saat HP habis baterai
        // atau QR-nya kotor — kode mesin untuk mesin, kode manusia untuk manusia.
        val captionLines = listOf(
            laid.plan.tier.displayName,
            spkNumber,
            "Size ${laid.plan.sizeLabel}",
            targetLabelFor(laid)
        )
        captionLines.forEachIndexed { index, line ->
            val font = if (index == 0) bold else regular
            drawText(
                cs, line, font, CAPTION_SIZE,
                TemplateRect(r.caption.x, r.caption.y + Mm10(index * 48), r.caption.width, r.caption.height),
                pageHeightPt
            )
        }

        drawWritingLines(cs, r, writingLines, regular, pageHeightPt)
    }


    /**
     * Membagi ruang tulis tangan rata untuk berapa pun baris yang diminta.
     *
     * Jarak antar baris dihitung, bukan dipatok. Dengan jarak tetap, gaya baju berpanel banyak
     * (kerah, placket, saku) diam-diam kehilangan dua baris terakhirnya di bawah tepi kartu — dan
     * operator baru menyadarinya saat memegang bundel kerah tanpa tempat menuliskan jumlahnya.
     * Batas atas [MAX_LINE_GAP] tetap dijaga supaya kartu berbaris sedikit tidak jadi renggang aneh.
     */
    private fun drawWritingLines(
        cs: PDPageContentStream,
        r: TraceLabelRegions,
        lines: List<String>,
        font: PDFont,
        pageHeightPt: Float
    ) {
        if (lines.isEmpty()) return
        val available = r.writingArea.height.value
        val gap = (available / lines.size).coerceAtMost(MAX_LINE_GAP)
        val ruleInset = (r.writingArea.width.value * 2) / 5

        lines.forEachIndexed { index, label ->
            val y = r.writingArea.y.value + index * gap
            drawText(
                cs, label, font, WRITING_LINE_SIZE,
                TemplateRect(r.writingArea.x, Mm10(y), r.writingArea.width, r.writingArea.height),
                pageHeightPt
            )
            if (label.isBlank()) return@forEachIndexed
            cs.setStrokingColor(CUT_GUIDE_GRAY, CUT_GUIDE_GRAY, CUT_GUIDE_GRAY)
            cs.setLineWidth(0.3f)
            val baseline = pageHeightPt - toPoints(y + gap / 2)
            cs.moveTo(toPoints(r.writingArea.x.value + ruleInset), baseline)
            cs.lineTo(toPoints(r.writingArea.right.value), baseline)
            cs.stroke()
        }
    }

    private fun targetLabelFor(laid: LaidOutTraceLabel): String = when (laid.plan.tier) {
        TraceTier.BUNDLE -> "Target ${laid.plan.targetCapacity} set"
        TraceTier.SACK -> "Target ${laid.plan.targetCapacity} pcs"
        TraceTier.WORKSHEET -> "${laid.plan.targetCapacity} pcs"
    }

    private fun drawText(
        cs: PDPageContentStream,
        text: String,
        font: PDFont,
        sizePt: Float,
        rect: TemplateRect,
        pageHeightPt: Float
    ) {
        if (text.isBlank()) return
        cs.setNonStrokingColor(0f, 0f, 0f)
        cs.beginText()
        cs.setFont(font, sizePt)
        cs.newLineAtOffset(toPoints(rect.x.value), pageHeightPt - toPoints(rect.y.value) - sizePt)
        cs.showText(text)
        cs.endText()
    }
}

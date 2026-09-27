package com.eventverse.app.infrastructure.pdf

import com.eventverse.app.domain.printing.TemplateRect
import com.eventverse.app.domain.sampling.SpkUrgencyLevel
import com.eventverse.app.domain.traceability.TraceCodec
import com.eventverse.app.domain.traceability.print.SpkCardContent
import com.eventverse.app.domain.traceability.print.SpkCardGridRow
import com.eventverse.app.domain.traceability.print.SpkCardLayout
import com.eventverse.app.domain.traceability.print.SpkCardSheet
import com.eventverse.app.domain.traceability.print.SpkMeasurementRow
import com.eventverse.app.infrastructure.pdf.PdfUnits.toPoints
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDFont
import java.io.ByteArrayOutputStream
import java.util.Locale
import kotlinx.datetime.LocalDate

/**
 * Kartu SPK A6 — satu halaman per ukuran, menggantung di tiap section produksi.
 *
 * Strip bawah kartu adalah satu-satunya elemen berwarna: penanda warna polos tanpa teks, mengikuti
 * sinyal produksi (hijau/amber/merah) kanvas Factory Flow. Tahap & deadline sudah tercetak di baris
 * identitas, jadi strip tidak perlu mengulanginya.
 */
class SpkCardPdfRenderer(private val scanHost: String) {

    fun render(sheet: SpkCardSheet): ByteArray {
        PDDocument().use { doc ->
            val regular = PdfFonts.bodyRegular(doc)
            val bold = PdfFonts.bodyBold(doc)
            val a6 = PDRectangle(
                toPoints(SpkCardLayout.PAGE_WIDTH),
                toPoints(SpkCardLayout.PAGE_HEIGHT)
            )

            if (sheet.pages.isEmpty()) doc.addPage(PDPage(a6))
            sheet.pages.forEach { page ->
                val pdPage = PDPage(a6)
                doc.addPage(pdPage)
                PDPageContentStream(doc, pdPage).use { cs ->
                    drawPage(cs, sheet, page.sizeLabel, regular, bold, a6.height)
                }
            }

            val out = ByteArrayOutputStream()
            doc.save(out)
            return out.toByteArray()
        }
    }

    private fun drawPage(
        cs: PDPageContentStream,
        sheet: SpkCardSheet,
        sizeLabel: String,
        regular: PDFont,
        bold: PDFont,
        pageHeightPt: Float
    ) {
        val content = sheet.content
        val regions = sheet.pages.first { it.sizeLabel == sizeLabel }.regions
        val spk = "${content.spkNumber}${revisionSuffix(content.revision)}"

        text(cs, spk, bold, SPK_SIZE, regions.spkNumber, pageHeightPt)
        text(cs, "REV ${content.revision}", bold, LABEL_SIZE, regions.revision, pageHeightPt)
        text(cs, "${content.styleName} · ${content.clientName}", regular, LABEL_SIZE, regions.styleClient, pageHeightPt)

        val card = content.cards.first { it.sizeLabel == sizeLabel }
        QrCodeRenderer.draw(
            content = cs,
            payload = TraceCodec.toScanUrl(card.code, scanHost),
            xPt = toPoints(regions.qr.x.value),
            topPt = pageHeightPt - toPoints(regions.qr.y.value),
            sizePt = toPoints(regions.qr.width.value)
        )
        // Rata kanan ke tepi QR: kode 8pt lebih lebar dari QR 30 mm; rata kiri membuatnya melewati margin kanan.
        textRight(cs, TraceCodec.grouped(card.code), bold, LABEL_SIZE, regions.humanCode, pageHeightPt)

        identityLines(content, card.qtyPcs).forEachIndexed { index, line ->
            text(cs, line, regular, BODY_SIZE, regions.identityLines[index], pageHeightPt)
        }

        text(cs, "UKURAN JADI — SPEK CLIENT (POM)", bold, LABEL_SIZE, regions.pomTitle, pageHeightPt)
        drawGrid(cs, regular, regions.pomGrid, card.pomRows, pageHeightPt)
        drawOverflow(cs, regular, regions.pomOverflow, regions.pomOverflowCount, pageHeightPt)

        text(cs, "HASIL UKURAN TIM SAMPLING", bold, LABEL_SIZE, regions.samplingTitle, pageHeightPt)
        if (card.samplingRows.isEmpty()) {
            text(cs, "Belum ada hasil ukuran — isi di lembar Program CAM.", regular, BODY_SIZE, regions.samplingTitle, pageHeightPt)
        } else {
            drawGrid(cs, regular, regions.samplingGrid, card.samplingRows, pageHeightPt)
        }
        drawOverflow(cs, regular, regions.samplingOverflow, regions.samplingOverflowCount, pageHeightPt)

        text(
            cs,
            "WARNA: ${content.colorwayText.ifBlank { "—" }}",
            bold, LABEL_SIZE, regions.colorwayLine, pageHeightPt
        )
        textRight(
            cs,
            "Dicetak ${formatDate(content.printedOn)}",
            regular, LABEL_SIZE, regions.colorwayLine, pageHeightPt
        )

        drawUrgencyStrip(cs, content, regions.urgencyStrip, pageHeightPt)
    }

    private companion object {
        const val SPK_SIZE = 16f
        const val LABEL_SIZE = 8f
        const val BODY_SIZE = 7.5f
    }

    private fun revisionSuffix(revision: Int): String =
        if (revision > 0) " (Rev $revision)" else ""

    /** Tepat lima baris identitas — urutannya kontrak dengan [SpkCardLayout.IDENTITY_LINES]. */
    private fun identityLines(content: SpkCardContent, qtyPcs: Int): List<String> {
        val size = content.cards.singleOrNull()?.sizeLabel
            ?: content.cards.joinToString("/") { it.sizeLabel }
        return listOf(
            "Client : ${content.clientName}",
            "Size : $size",
            "JUMLAH : $qtyPcs PCS",
            "Tahap : ${content.stageLabel} (${content.stageNumber}/${content.stageCount})",
            "Deadline : ${content.deadline?.let { formatDate(it) } ?: "—"}"
        )
    }

    private fun drawGrid(
        cs: PDPageContentStream,
        regular: PDFont,
        grid: List<SpkCardGridRow>,
        rows: List<SpkMeasurementRow>,
        pageHeightPt: Float
    ) {
        grid.forEachIndexed { index, row ->
            text(cs, compose(rows.getOrNull(index * 2)), regular, BODY_SIZE, row.left, pageHeightPt)
            text(cs, compose(rows.getOrNull(index * 2 + 1)), regular, BODY_SIZE, row.right, pageHeightPt)
        }
    }

    private fun compose(row: SpkMeasurementRow?): String =
        row?.let { "${it.label} : ${it.value}" } ?: ""

    private fun drawOverflow(
        cs: PDPageContentStream,
        regular: PDFont,
        rect: TemplateRect?,
        count: Int,
        pageHeightPt: Float
    ) {
        if (rect == null || count <= 0) return
        text(cs, "+$count ukuran lainnya — lihat lembar kerja rajut", regular, LABEL_SIZE, rect, pageHeightPt)
    }

    private fun drawUrgencyStrip(
        cs: PDPageContentStream,
        content: SpkCardContent,
        strip: TemplateRect,
        pageHeightPt: Float
    ) {
        val (r, g, b) = stripColor(content.urgencyLevel)
        cs.setNonStrokingColor(r, g, b)
        cs.addRect(toPoints(strip.x.value), pageHeightPt - toPoints(strip.y.value + strip.height.value), toPoints(strip.width.value), toPoints(strip.height.value))
        cs.fill()
    }

    /** Hijau/amber/merah = bahasa sinyal produksi yang sama dengan kanvas Factory Flow; abu = tidak bisa dinilai (belum diestimasi / tanpa deadline). */
    private fun stripColor(level: SpkUrgencyLevel): FloatArray = when (level) {
        SpkUrgencyLevel.URGENT -> floatArrayOf(0.863f, 0.149f, 0.149f)
        SpkUrgencyLevel.SEGERA -> floatArrayOf(0.851f, 0.467f, 0.024f)
        SpkUrgencyLevel.AMAN -> floatArrayOf(0.086f, 0.639f, 0.290f)
        SpkUrgencyLevel.BELUM_DIESTIMASI, SpkUrgencyLevel.TANPA_DEADLINE -> floatArrayOf(0.392f, 0.455f, 0.545f)
    }

    private fun formatDate(value: LocalDate): String =
        String.format(Locale.ROOT, "%02d-%02d-%02d", value.dayOfMonth, value.monthNumber, value.year % 100)

    private fun text(
        cs: PDPageContentStream,
        value: String,
        font: PDFont,
        sizePt: Float,
        rect: TemplateRect,
        pageHeightPt: Float
    ) {
        if (value.isBlank()) return
        cs.setNonStrokingColor(0f, 0f, 0f)
        cs.beginText()
        cs.setFont(font, sizePt)
        cs.newLineAtOffset(toPoints(rect.x.value), pageHeightPt - toPoints(rect.y.value) - sizePt)
        cs.showText(value)
        cs.endText()
    }

    private fun textRight(
        cs: PDPageContentStream,
        value: String,
        font: PDFont,
        sizePt: Float,
        rect: TemplateRect,
        pageHeightPt: Float
    ) {
        if (value.isBlank()) return
        val widthPt = font.getStringWidth(value) / 1000f * sizePt
        cs.setNonStrokingColor(0f, 0f, 0f)
        cs.beginText()
        cs.setFont(font, sizePt)
        cs.newLineAtOffset(
            toPoints(rect.x.value + rect.width.value) - widthPt,
            pageHeightPt - toPoints(rect.y.value) - sizePt
        )
        cs.showText(value)
        cs.endText()
    }
}
package com.eventverse.app.infrastructure.pdf

import com.eventverse.app.domain.traceability.TraceCodec
import com.eventverse.app.domain.traceability.print.KnitWorksheet
import com.eventverse.app.domain.traceability.print.KnitWorksheetPage
import com.eventverse.app.infrastructure.pdf.PdfUnits.toPoints
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDFont
import java.io.ByteArrayOutputStream

/**
 * Lembar Kerja Rajut: satu halaman per ukuran, memuat tiga hal yang selama ini tercerai — QR, spek
 * per bagian hasil breakdown, dan spek ukuran dari buyer.
 *
 * Dipecah per ukuran karena satu mesin mengerjakan satu ukuran dalam satu waktu. Lembar yang memuat
 * seluruh ukuran memaksa operator mencari barisnya sendiri di tengah shift, dan baris yang salah baca
 * berarti satu bundel dirajut dengan target gramasi ukuran lain.
 */
class KnitWorksheetPdfRenderer(private val scanHost: String) {

    private companion object {
        const val MARGIN = 120
        const val QR_SIZE = 350
        const val TITLE_SIZE = 16f
        const val HEADING_SIZE = 11f
        // Lembar ini digantung di mesin dan dibaca sambil berdiri, bukan di layar sejengkal dari mata.
        // Nunito ber-x-height besar, jadi 10pt di sini sepadan dengan 11pt pada font berbadan kecil.
        const val BODY_SIZE = 10f
        const val LINE_HEIGHT = 52
        const val ROW_HEIGHT = 48
    }

    fun render(worksheet: KnitWorksheet): ByteArray {
        PDDocument().use { doc ->
            val regular = PdfFonts.bodyRegular(doc)
            val bold = PdfFonts.bodyBold(doc)

            if (worksheet.pages.isEmpty()) doc.addPage(PDPage(PDRectangle.A4))
            worksheet.pages.forEach { page ->
                val pdPage = PDPage(PDRectangle.A4)
                doc.addPage(pdPage)
                PDPageContentStream(doc, pdPage).use { cs ->
                    drawPage(cs, worksheet, page, regular, bold, PDRectangle.A4.height)
                }
            }

            val out = ByteArrayOutputStream()
            doc.save(out)
            return out.toByteArray()
        }
    }

    private fun drawPage(
        cs: PDPageContentStream,
        worksheet: KnitWorksheet,
        page: KnitWorksheetPage,
        regular: PDFont,
        bold: PDFont,
        pageHeightPt: Float
    ) {
        var y = MARGIN

        text(cs, "LEMBAR KERJA RAJUT — SIZE ${page.sizeLabel}", bold, TITLE_SIZE, MARGIN, y, pageHeightPt)
        y += 70
        text(cs, "${worksheet.spkNumber} · ${worksheet.styleName} · ${worksheet.clientName}", regular, HEADING_SIZE, MARGIN, y, pageHeightPt)
        y += 50
        text(cs, "Jumlah: ${page.orderedPcs} pcs" + colorwaySuffix(page), regular, HEADING_SIZE, MARGIN, y, pageHeightPt)

        QrCodeRenderer.draw(
            content = cs,
            payload = TraceCodec.toScanUrl(page.code, scanHost),
            xPt = toPoints(2100 - MARGIN - QR_SIZE),
            topPt = pageHeightPt - toPoints(MARGIN),
            sizePt = toPoints(QR_SIZE)
        )
        text(cs, TraceCodec.grouped(page.code), bold, BODY_SIZE, 2100 - MARGIN - QR_SIZE, MARGIN + QR_SIZE + 20, pageHeightPt)

        y = MARGIN + QR_SIZE + 130

        // ── Spek per bagian ────────────────────────────────────────────────────────────────
        text(cs, "SPEK PER BAGIAN (hasil breakdown panel)", bold, HEADING_SIZE, MARGIN, y, pageHeightPt)
        y += LINE_HEIGHT
        row(cs, bold, y, pageHeightPt, "PANEL", "GRAMASI", "MENIT", "PROGRAM CAM", "SUMBER")
        y += ROW_HEIGHT

        page.panelRows.forEach { panel ->
            row(
                cs, regular, y, pageHeightPt,
                panel.panelLabel,
                formatGrams(panel.weightGrams),
                panel.minutes.toString(),
                panel.program.ifBlank { "—" },
                // Angka warisan ditandai terang-terangan. Target 138 gram yang sebetulnya salinan dari
                // size L dan belum pernah ditimbang tidak boleh terlihat sama meyakinkannya dengan
                // angka hasil timbang.
                panel.inheritedFrom?.let { "salinan $it" } ?: "ditimbang"
            )
            y += ROW_HEIGHT
        }
        row(cs, bold, y, pageHeightPt, "TOTAL", formatGrams(page.totalWeightGrams), page.totalMinutes.toString(), "", "")
        y += ROW_HEIGHT + 40

        if (page.feederNotes.isNotEmpty()) {
            text(cs, "Instruksi Panah / Feeder: " + page.feederNotes.joinToString(", "), regular, BODY_SIZE, MARGIN, y, pageHeightPt)
            y += LINE_HEIGHT + 20
        }

        // ── Spek dari buyer ────────────────────────────────────────────────────────────────
        text(cs, "SPEK DARI BUYER (titik ukur & toleransi)", bold, HEADING_SIZE, MARGIN, y, pageHeightPt)
        y += LINE_HEIGHT
        row(cs, bold, y, pageHeightPt, "TITIK UKUR", "JADI (CM)", "RAJUT MENTAH", "TOLERANSI", "")
        y += ROW_HEIGHT

        if (page.measurementRows.isEmpty()) {
            text(cs, "Buyer belum mengisi tabel ukuran untuk size ini.", regular, BODY_SIZE, MARGIN, y, pageHeightPt)
        } else {
            page.measurementRows.forEach { pom ->
                row(
                    cs, regular, y, pageHeightPt,
                    pom.pomName,
                    pom.finishedCm?.let { formatCm(it) } ?: "—",
                    pom.rawKnitCm?.let { formatCm(it) } ?: "—",
                    "± ${formatCm(pom.toleranceCm)}",
                    ""
                )
                y += ROW_HEIGHT
            }
        }
    }

    private fun colorwaySuffix(page: KnitWorksheetPage): String =
        if (page.colorway.isBlank()) "" else " · Warna: ${page.colorway}"

    private fun row(
        cs: PDPageContentStream,
        font: PDFont,
        y: Int,
        pageHeightPt: Float,
        vararg cells: String
    ) {
        // Jarak kolom ditentukan oleh header TERPANJANG ("RAJUT MENTAH", "PROGRAM CAM"), bukan oleh
        // isi selnya. Dengan jarak yang dipas ke isi, header tabel ukuran saling menimpa dan
        // "RAJUT MENTAH" terbaca menyatu dengan "TOLERANSI".
        val columns = intArrayOf(MARGIN, MARGIN + 560, MARGIN + 920, MARGIN + 1300, MARGIN + 1640)
        cells.forEachIndexed { index, cell ->
            if (index < columns.size) text(cs, cell, font, BODY_SIZE, columns[index], y, pageHeightPt)
        }
    }

    private fun text(
        cs: PDPageContentStream,
        value: String,
        font: PDFont,
        sizePt: Float,
        xMm10: Int,
        yMm10: Int,
        pageHeightPt: Float
    ) {
        if (value.isBlank()) return
        cs.setNonStrokingColor(0f, 0f, 0f)
        cs.beginText()
        cs.setFont(font, sizePt)
        cs.newLineAtOffset(toPoints(xMm10), pageHeightPt - toPoints(yMm10) - sizePt)
        cs.showText(value)
        cs.endText()
    }

    private fun formatGrams(value: Double): String =
        if (value <= 0.0) "—" else "${(value * 10).toLong() / 10.0} g"

    private fun formatCm(value: Double): String = "${(value * 10).toLong() / 10.0}"
}

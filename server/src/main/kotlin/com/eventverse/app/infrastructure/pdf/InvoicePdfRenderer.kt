package com.eventverse.app.infrastructure.pdf

import com.eventverse.app.domain.common.Money
import com.eventverse.app.domain.invoicing.Invoice
import com.eventverse.app.domain.invoicing.template.*
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDFont
import org.apache.pdfbox.pdmodel.font.PDType0Font
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import java.awt.Color
import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * Renderer dokumen faktur PDF server-side menggunakan Apache PDFBox 3.x.
 *
 * Menggunakan sistem koordinat 1/10 milimeter (Mm10) dari domain kanvas dan font Nunito & Fredoka,
 * menjamin output cetak PDF identik secara visual dengan preview Compose di web/desktop.
 */
class InvoicePdfRenderer {

    companion object {
        private const val MM10_TO_PT = 72f / 254f // 1/10 mm to points
    }

    fun render(
        invoice: Invoice,
        template: InvoiceTemplate = invoice.renderedTemplate ?: InvoiceTemplateFactory.standardIndonesianInvoice(invoice.tenantId, invoice.createdAt),
        paidAmount: Money = Money.zero(invoice.currency)
    ): ByteArray {
        PDDocument().use { doc ->
            val pageSize = when (template.paperSize) {
                PaperSize.A4 -> PDRectangle.A4
                PaperSize.LETTER -> PDRectangle.LETTER
                PaperSize.A5 -> PDRectangle(PDRectangle.A4.height / 2f, PDRectangle.A4.width)
            }

            val page = PDPage(pageSize)
            doc.addPage(page)

            val pageHeightPt = pageSize.height

            // Load TrueType fonts
            val fontNunitoRegular = loadFont(doc, "/fonts/nunito_regular.ttf") ?: PDType1Font(Standard14Fonts.FontName.HELVETICA)
            val fontNunitoBold = loadFont(doc, "/fonts/nunito_bold.ttf") ?: PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD)
            val fontFredokaBold = loadFont(doc, "/fonts/fredoka_bold.ttf") ?: fontNunitoBold
            val fontFredokaMedium = loadFont(doc, "/fonts/fredoka_medium.ttf") ?: fontNunitoRegular

            // Calculate table expansion for anchorBelowTable
            val itemTable = template.elements.filterIsInstance<TemplateElement.ItemTable>().firstOrNull()
            val tableDeltaMm10 = if (itemTable != null) {
                val neededRows = invoice.lines.size
                val headerH = if (itemTable.showHeader) itemTable.rowHeight.value else 0
                val dynamicHeight = headerH + (neededRows * itemTable.rowHeight.value)
                if (dynamicHeight > itemTable.rect.height.value) {
                    dynamicHeight - itemTable.rect.height.value
                } else 0
            } else 0

            // Sort elements by zOrder
            val sortedElements = template.elements.sortedBy { it.zOrder }

            PDPageContentStream(doc, page).use { cs ->
                for (element in sortedElements) {
                    val yMm10 = if (element.anchorBelowTable) element.rect.y.value + tableDeltaMm10 else element.rect.y.value
                    val xPt = element.rect.x.value * MM10_TO_PT
                    val widthPt = element.rect.width.value * MM10_TO_PT
                    val heightPt = element.rect.height.value * MM10_TO_PT
                    val topPt = pageHeightPt - (yMm10 * MM10_TO_PT)
                    val bottomPt = topPt - heightPt

                    when (element) {
                        is TemplateElement.StaticText -> {
                            val font = pickFont(element.style, fontNunitoRegular, fontNunitoBold, fontFredokaMedium, fontFredokaBold)
                            drawText(cs, element.text, font, element.style, xPt, topPt, widthPt)
                        }

                        is TemplateElement.BoundField -> {
                            val resolved = InvoiceBindingResolver.resolve(element.binding, invoice, null, paidAmount)
                            val textValue = when (resolved) {
                                is ResolvedBindingValue.Text -> element.prefix + resolved.value + element.suffix
                                is ResolvedBindingValue.Image -> element.prefix + (resolved.assetUrl ?: "") + element.suffix
                                is ResolvedBindingValue.Empty -> ""
                            }
                            if (textValue.isNotBlank()) {
                                val font = pickFont(element.style, fontNunitoRegular, fontNunitoBold, fontFredokaMedium, fontFredokaBold)
                                drawText(cs, textValue, font, element.style, xPt, topPt, widthPt)
                            }
                        }

                        is TemplateElement.RectShape -> {
                            val strokeColor = element.strokeHex?.let { colorFromHex(it) }
                            val fillColor = element.fillHex?.let { colorFromHex(it) }

                            if (fillColor != null) {
                                cs.setNonStrokingColor(fillColor)
                                cs.addRect(xPt, bottomPt, widthPt, heightPt)
                                cs.fill()
                            }
                            if (strokeColor != null && element.strokeMm10 > 0) {
                                cs.setStrokingColor(strokeColor)
                                cs.setLineWidth(element.strokeMm10 * MM10_TO_PT)
                                cs.addRect(xPt, bottomPt, widthPt, heightPt)
                                cs.stroke()
                            }
                        }

                        is TemplateElement.LineShape -> {
                            val strokeColor = colorFromHex(element.strokeHex)
                            cs.setStrokingColor(strokeColor)
                            cs.setLineWidth(element.strokeMm10 * MM10_TO_PT)
                            val lineY = topPt - (heightPt / 2f)
                            cs.moveTo(xPt, lineY)
                            cs.lineTo(xPt + widthPt, lineY)
                            cs.stroke()
                        }

                        is TemplateElement.ItemTable -> {
                            drawItemTable(
                                cs = cs,
                                table = element,
                                invoice = invoice,
                                paidAmount = paidAmount,
                                xPt = xPt,
                                topPt = topPt,
                                widthPt = widthPt,
                                fontRegular = fontNunitoRegular,
                                fontBold = fontNunitoBold
                            )
                        }

                        is TemplateElement.ImageBox -> {
                            // Border outline for signature / image placeholder box
                            cs.setStrokingColor(colorFromHex(0xFFCBD5E1L))
                            cs.setLineWidth(1f)
                            cs.addRect(xPt, bottomPt, widthPt, heightPt)
                            cs.stroke()
                        }
                    }
                }
            }

            val out = ByteArrayOutputStream()
            doc.save(out)
            return out.toByteArray()
        }
    }

    private fun drawText(
        cs: PDPageContentStream,
        rawText: String,
        font: PDFont,
        style: TextStyleSpec,
        xPt: Float,
        topPt: Float,
        widthPt: Float
    ) {
        val sanitized = rawText.replace("\r\n", " ").replace("\n", " ").replace("\r", " ").replace("\t", " ")
        if (sanitized.isBlank()) return

        val fontSizePt = style.fontSizePt.toFloat()
        val textWidth = try {
            (font.getStringWidth(sanitized) / 1000f) * fontSizePt
        } catch (_: Exception) {
            sanitized.length * fontSizePt * 0.5f
        }

        val drawX = when (style.align) {
            TextAlign.LEFT -> xPt
            TextAlign.CENTER -> xPt + (widthPt - textWidth).coerceAtLeast(0f) / 2f
            TextAlign.RIGHT -> xPt + (widthPt - textWidth).coerceAtLeast(0f)
        }

        val baselineY = topPt - (fontSizePt * 0.85f)

        cs.setNonStrokingColor(colorFromHex(style.colorHex))
        cs.beginText()
        cs.setFont(font, fontSizePt)
        cs.newLineAtOffset(drawX, baselineY)
        try {
            cs.showText(sanitized)
        } catch (_: Exception) {
            val safeAscii = sanitized.map { if (it.code in 32..126) it else '?' }.joinToString("")
            cs.showText(safeAscii)
        }
        cs.endText()
    }

    private fun drawItemTable(
        cs: PDPageContentStream,
        table: TemplateElement.ItemTable,
        invoice: Invoice,
        paidAmount: Money,
        xPt: Float,
        topPt: Float,
        widthPt: Float,
        fontRegular: PDFont,
        fontBold: PDFont
    ) {
        val rowHeightPt = table.rowHeight.value * MM10_TO_PT
        val headerHeightPt = if (table.showHeader) rowHeightPt else 0f
        val borderColor = Color(203, 213, 225) // Slate-300

        // Calculate Column Widths
        val colWidthsPt = table.columns.map { col ->
            val ratio = col.widthRatio.numerator.toFloat() / col.widthRatio.denominator.toFloat()
            widthPt * ratio
        }

        // Draw Table Header Background
        if (table.showHeader) {
            cs.setNonStrokingColor(Color(241, 245, 249)) // Slate-100
            cs.addRect(xPt, topPt - headerHeightPt, widthPt, headerHeightPt)
            cs.fill()

            var currentColX = xPt
            table.columns.forEachIndexed { idx, col ->
                val colW = colWidthsPt[idx]
                val headerStyle = table.headerStyle.copy(align = col.align)
                drawText(
                    cs = cs,
                    rawText = col.header,
                    font = fontBold,
                    style = headerStyle,
                    xPt = currentColX + 4f,
                    topPt = topPt - 2f,
                    widthPt = colW - 8f
                )
                currentColX += colW
            }
        }

        // Draw Rows
        var currentRowTop = topPt - headerHeightPt
        val zebraHex = table.zebraFillHex
        invoice.lines.forEachIndexed { rowIdx, line ->
            val rowBottom = currentRowTop - rowHeightPt

            // Zebra background
            if (rowIdx % 2 == 1 && zebraHex != null) {
                cs.setNonStrokingColor(colorFromHex(zebraHex))
                cs.addRect(xPt, rowBottom, widthPt, rowHeightPt)
                cs.fill()
            }

            // Cell text
            var cellX = xPt
            table.columns.forEachIndexed { colIdx, col ->
                val colW = colWidthsPt[colIdx]
                val cellResolved = InvoiceBindingResolver.resolve(col.binding, invoice, line, paidAmount)
                val cellText = when (cellResolved) {
                    is ResolvedBindingValue.Text -> cellResolved.value
                    is ResolvedBindingValue.Image -> cellResolved.assetUrl ?: ""
                    is ResolvedBindingValue.Empty -> ""
                }

                val cellStyle = table.bodyStyle.copy(align = col.align)

                drawText(
                    cs = cs,
                    rawText = cellText,
                    font = fontRegular,
                    style = cellStyle,
                    xPt = cellX + 4f,
                    topPt = currentRowTop - 3f,
                    widthPt = colW - 8f
                )

                cellX += colW
            }

            // Row separator line
            cs.setStrokingColor(borderColor)
            cs.setLineWidth(0.5f)
            cs.moveTo(xPt, rowBottom)
            cs.lineTo(xPt + widthPt, rowBottom)
            cs.stroke()

            currentRowTop -= rowHeightPt
        }

        // Table Outer Border
        val totalTableHeightPt = headerHeightPt + (invoice.lines.size * rowHeightPt)
        cs.setStrokingColor(borderColor)
        cs.setLineWidth(1f)
        cs.addRect(xPt, topPt - totalTableHeightPt, widthPt, totalTableHeightPt)
        cs.stroke()
    }

    private fun pickFont(
        style: TextStyleSpec,
        nunitoRegular: PDFont,
        nunitoBold: PDFont,
        fredokaMedium: PDFont,
        fredokaBold: PDFont
    ): PDFont {
        return when {
            style.fontSizePt >= 14 && style.isBold -> fredokaBold
            style.fontSizePt >= 14 -> fredokaMedium
            style.isBold -> nunitoBold
            else -> nunitoRegular
        }
    }

    private fun colorFromHex(argb: Long): Color {
        val r = ((argb shr 16) and 0xFF).toInt()
        val g = ((argb shr 8) and 0xFF).toInt()
        val b = (argb and 0xFF).toInt()
        return Color(r, g, b)
    }

    private fun loadFont(doc: PDDocument, resourcePath: String): PDFont? {
        return try {
            val stream: InputStream = javaClass.getResourceAsStream(resourcePath) ?: return null
            PDType0Font.load(doc, stream)
        } catch (_: Exception) {
            null
        }
    }
}

package com.eventverse.app.infrastructure.pdf

import com.eventverse.app.domain.common.Money
import com.eventverse.app.domain.invoicing.Invoice
import com.eventverse.app.domain.invoicing.template.*
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDFont
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import java.awt.Color
import java.io.ByteArrayOutputStream

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

            // Setiap InvoiceFont dimuat sekali, lalu dicari lewat enumnya. Sebelumnya keempat font
            // dioper sebagai empat parameter terpisah ke setiap fungsi gambar, sehingga aturan
            // pemilihannya ikut tersebar; sekarang aturan itu tinggal di InvoiceFontResolver.
            val fonts = loadFonts(doc)

            // Geometri dinamis (tinggi turunan + pergeseran elemen ber-anchor) dihitung oleh
            // InvoiceDocumentLayout, entity yang sama yang dipakai kanvas Compose. Sebelumnya
            // perhitungan ini hidup di sini saja, sehingga kanvas menampilkan posisi yang berbeda
            // dari hasil cetak untuk setiap template yang elemen bawahnya mengikuti tabel.
            val laidOutElements = InvoiceDocumentLayout.solve(template, invoice, paidAmount)

            PDPageContentStream(doc, page).use { cs ->
                for (laid in laidOutElements) {
                    val element = laid.element
                    val xPt = laid.rect.x.value * MM10_TO_PT
                    val widthPt = laid.rect.width.value * MM10_TO_PT
                    val heightPt = laid.rect.height.value * MM10_TO_PT
                    val topPt = pageHeightPt - (laid.rect.y.value * MM10_TO_PT)
                    val bottomPt = topPt - heightPt

                    when (element) {
                        is TemplateElement.StaticText -> {
                            drawText(cs, laid.textLines, fonts.forStyle(element.style), element.style, xPt, topPt, widthPt)
                        }

                        is TemplateElement.BoundField -> {
                            // Baris teks sudah dipecah oleh InvoiceTextLayout saat geometri
                            // diselesaikan, sehingga PDF memotong baris di titik yang persis sama
                            // dengan kanvas — bukan dengan metrik fontnya sendiri.
                            if (laid.textLines.any { it.isNotBlank() }) {
                                drawText(cs, laid.textLines, fonts.forStyle(element.style), element.style, xPt, topPt, widthPt)
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
                                fonts = fonts
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

    /**
     * Menggambar teks multi-baris.
     *
     * [lines] datang dari [InvoiceTextLayout] melalui [InvoiceDocumentLayout.solve] dan **tidak boleh
     * dipecah ulang di sini**. Kalau PDFBox memutuskan sendiri di mana baris dipotong memakai metrik
     * aslinya, dokumen hasil cetak akan berbeda dari yang dilihat pengguna di kanvas — persis kelas
     * bug yang membuat desainer template tidak bisa dipercaya.
     *
     * Metrik asli tetap dipakai, tapi hanya untuk hal yang tidak mengubah struktur: lebar tiap baris
     * (untuk perataan kiri/tengah/kanan) dan penempatan glyph.
     */
    private fun drawText(
        cs: PDPageContentStream,
        lines: List<String>,
        font: PDFont,
        style: TextStyleSpec,
        xPt: Float,
        topPt: Float,
        widthPt: Float
    ) {
        val drawable = lines.ifEmpty { return }
        if (drawable.all { it.isBlank() }) return

        val fontSizePt = style.fontSizePt.toFloat()
        val lineHeightPt = InvoiceTextLayout.lineHeightMm10(style) * MM10_TO_PT

        cs.setNonStrokingColor(colorFromHex(style.colorHex))

        drawable.forEachIndexed { index, line ->
            if (line.isBlank()) return@forEachIndexed

            val textWidth = try {
                (font.getStringWidth(line) / 1000f) * fontSizePt
            } catch (_: Exception) {
                line.length * fontSizePt * 0.5f
            }

            val drawX = when (style.align) {
                TextAlign.LEFT -> xPt
                TextAlign.CENTER -> xPt + (widthPt - textWidth).coerceAtLeast(0f) / 2f
                TextAlign.RIGHT -> xPt + (widthPt - textWidth).coerceAtLeast(0f)
            }

            val baselineY = topPt - (fontSizePt * 0.85f) - (index * lineHeightPt)

            cs.beginText()
            // `setFont` wajib berada di dalam blok teks (BT/ET); memanggilnya di luar akan
            // melempar IllegalStateException di PDFBox 3.
            cs.setFont(font, fontSizePt)
            cs.newLineAtOffset(drawX, baselineY)
            try {
                cs.showText(line)
            } catch (_: Exception) {
                val safeAscii = line.map { if (it.code in 32..126) it else '?' }.joinToString("")
                cs.showText(safeAscii)
            }
            cs.endText()
        }
    }

    private fun drawItemTable(
        cs: PDPageContentStream,
        table: TemplateElement.ItemTable,
        invoice: Invoice,
        paidAmount: Money,
        xPt: Float,
        topPt: Float,
        widthPt: Float,
        fonts: LoadedFonts
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
                    // Judul kolom sengaja satu baris: kolom tabel jauh lebih sempit dari elemen teks
                    // biasa, dan judul yang terpecah dua baris akan menabrak baris pertama data.
                    lines = listOf(col.header),
                    font = fonts.forStyle(headerStyle),
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
                    lines = listOf(cellText),
                    font = fonts.forStyle(cellStyle),
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

    /**
     * Keempat font faktur yang sudah dimuat ke dalam satu dokumen, dicari lewat [InvoiceFont].
     *
     * Font PDFBox terikat pada `PDDocument` tempat ia di-embed, jadi pemetaan ini dibangun ulang
     * setiap render dan tidak boleh dijadikan milik kelas renderer.
     */
    private class LoadedFonts(private val byFont: Map<InvoiceFont, PDFont>, private val fallback: PDFont) {
        fun forStyle(style: TextStyleSpec): PDFont = byFont[InvoiceFontResolver.resolve(style)] ?: fallback
    }

    /**
     * Memuat keempat berkas font.
     *
     * Jika Fredoka gagal dimuat, penggantinya adalah Nunito pada bobot setara — bukan Helvetica —
     * supaya kegagalan memuat satu berkas tidak mengubah lebar seluruh judul dokumen. Helvetica
     * hanya dipakai kalau Nunito pun tidak ada, yang berarti berkas font memang tidak terpaket.
     */
    private fun loadFonts(doc: PDDocument): LoadedFonts {
        val nunitoRegular = loadFont(doc, "/fonts/nunito_regular.ttf")
            ?: PDType1Font(Standard14Fonts.FontName.HELVETICA)
        val nunitoBold = loadFont(doc, "/fonts/nunito_bold.ttf")
            ?: PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD)

        return LoadedFonts(
            byFont = mapOf(
                InvoiceFont.NUNITO_REGULAR to nunitoRegular,
                InvoiceFont.NUNITO_BOLD to nunitoBold,
                InvoiceFont.FREDOKA_MEDIUM to (loadFont(doc, "/fonts/fredoka_medium.ttf") ?: nunitoRegular),
                InvoiceFont.FREDOKA_BOLD to (loadFont(doc, "/fonts/fredoka_bold.ttf") ?: nunitoBold)
            ),
            fallback = nunitoRegular
        )
    }

    private fun colorFromHex(argb: Long): Color {
        val r = ((argb shr 16) and 0xFF).toInt()
        val g = ((argb shr 8) and 0xFF).toInt()
        val b = (argb and 0xFF).toInt()
        return Color(r, g, b)
    }

    private fun loadFont(doc: PDDocument, resourcePath: String): PDFont? =
        PdfFonts.loadTtf(doc, resourcePath)
}

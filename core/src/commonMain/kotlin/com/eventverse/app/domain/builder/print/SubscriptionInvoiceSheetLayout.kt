package com.eventverse.app.domain.builder.print

import com.eventverse.app.domain.invoicing.template.InvoiceTextLayout
import com.eventverse.app.domain.printing.Mm10
import com.eventverse.app.domain.printing.PaperSize
import com.eventverse.app.domain.printing.TemplateRect

/**
 * Tata letak lembar tagihan A4 — murni, deterministik, dan bebas platform.
 *
 * ## Yang membedakannya dari `BlueprintSheetLayout`
 *
 * Blueprint hanya punya satu kolom; tagihan punya **kolom angka di kanan**. Karena itu aliran di sini
 * mengenal pasangan (label, angka), dan sisi kanan dihitung dari lebar teks yang **sudah diukur**
 * (`InvoiceTextLayout.measureWidthMm10`) sehingga angkanya berakhir rapi di margin kanan — bukan
 * sekadar "kira-kira cukup". Angka tagihan yang tidak lurus membuat pembaca menghitung ulang dengan
 * mata, dan itu persis yang tidak boleh terjadi pada dokumen uang.
 *
 * ## Pemotongan baris tidak diulang di renderer
 *
 * Sama seperti blueprint: baris dipecah **sekali** di sini lewat [InvoiceTextLayout], pemecah baris
 * dokumen cetak platform yang metrik glyph-nya dibangkitkan dari TTF yang sama dengan yang dipakai
 * PDFBox (dijaga `InvoiceFontMetricsDriftTest`). Renderer hanya menggambar apa yang sudah diputuskan.
 *
 * ## Pemenggalan halaman di tingkat baris
 *
 * Rincian modul bisa panjang (tenant dengan puluhan modul aktif). Tiap item diperiksa satu per satu,
 * dan halaman lanjutan diberi judul "(lanjutan)" supaya lembar yang terpisah dari halaman pertamanya
 * tetap bisa diidentifikasi saat difotokopi atau dicetak ulang.
 */
object SubscriptionInvoiceSheetLayout {

    val PAPER: PaperSize = PaperSize.A4

    const val MARGIN_X_MM10 = 150
    const val MARGIN_TOP_MM10 = 130
    const val MARGIN_BOTTOM_MM10 = 140

    /** Jarak badan dokumen ke blok footer, supaya teks terakhir tidak menempel ke catatan kaki. */
    const val FOOTER_GAP_MM10 = 70

    /** Ruang sebelum judul seksi; tanpa ini tagihan terbaca sebagai satu dinding teks. */
    const val SPACE_BEFORE_HEADING_MM10 = 70

    /** Ruang sebelum garis pemisah; garis yang menempel ke teks di atasnya terbaca sebagai coretan. */
    const val SPACE_BEFORE_RULE_MM10 = 45

    /** Tinggi yang direservasi satu garis pemisah (2 mm). */
    const val RULE_HEIGHT_MM10 = 20

    /**
     * Jarak minimum antara teks kiri dan kolom angka. Tanpa ini, nama modul terpanjang akan
     * bersinggungan dengan harganya walau keduanya "masih muat".
     */
    const val COLUMN_GAP_MM10 = 60

    const val WATERMARK_FONT_PT = 40
    const val WATERMARK_ANGLE_DEG = 33f

    const val CONTINUATION_SUFFIX = "(lanjutan)"

    fun layout(document: SubscriptionInvoicePdfDocument): SubscriptionInvoiceSheet {
        val bodyWidth = PAPER.widthMm10 - 2 * MARGIN_X_MM10
        val footerRole = SubscriptionInvoiceLineRole.FOOTER
        val footerNoteLines = InvoiceTextLayout.wrap(document.footerNote, bodyWidth, footerRole.style)
        val footerHeight = InvoiceTextLayout.lineHeightMm10(footerRole.style) * (1 + footerNoteLines.size)
        val bottomLimit = PAPER.heightMm10 - MARGIN_BOTTOM_MM10 - footerHeight - FOOTER_GAP_MM10

        val builder = PageBuilder(document, bodyWidth, bottomLimit)
        flowItems(document).forEach(builder::emit)

        val total = builder.pages.size
        val pages = builder.pages.mapIndexed { pageIndex, body ->
            SubscriptionInvoicePage(
                index = pageIndex + 1,
                body = body,
                footer = footerLines(pageIndex + 1, total, footerHeight, bodyWidth, footerNoteLines, document),
                watermark = watermark(document)
            )
        }
        return SubscriptionInvoiceSheet(document, pages)
    }

    /** Penanda diagonal di tengah kertas; `width`/`height` nol karena renderer memutarnya sendiri. */
    private fun watermark(document: SubscriptionInvoicePdfDocument) = SubscriptionInvoiceWatermark(
        text = document.watermark,
        center = TemplateRect(Mm10(PAPER.widthMm10 / 2), Mm10(PAPER.heightMm10 / 2), Mm10.ZERO, Mm10.ZERO),
        fontSizePt = WATERMARK_FONT_PT,
        angleDeg = WATERMARK_ANGLE_DEG
    )

    /**
     * Footer diulang di tiap halaman: nomor halaman untuk membuktikan tidak ada lembar yang hilang,
     * catatan kaki yang menyatakan angkanya beku, dan waktu cetak untuk melacak salinan mana yang
     * beredar. Waktu cetak ikut ke **setiap** halaman, bukan hanya halaman pertama: dokumen yang
     * dicetak ulang bulan depan harus bisa dibedakan dari yang asli.
     */
    private fun footerLines(
        page: Int,
        total: Int,
        heightMm10: Int,
        widthMm10: Int,
        noteLines: List<String>,
        document: SubscriptionInvoicePdfDocument
    ): List<SubscriptionInvoiceLine> {
        val role = SubscriptionInvoiceLineRole.FOOTER
        val lineHeight = InvoiceTextLayout.lineHeightMm10(role.style)
        var y = PAPER.heightMm10 - MARGIN_BOTTOM_MM10 - heightMm10
        val lines = mutableListOf<SubscriptionInvoiceLine>()
        val head = "Halaman $page dari $total · ${document.number} · Dicetak ${document.generatedAtLabel}"
        (listOf(head) + noteLines).forEach { text ->
            lines += SubscriptionInvoiceLine(
                text = text,
                role = role,
                rect = TemplateRect(Mm10(MARGIN_X_MM10), Mm10(y), Mm10(widthMm10), Mm10(lineHeight))
            )
            y += lineHeight
        }
        return lines
    }

    /** Satu baris aliran: teks satu kolom, pasangan kolom, atau garis pemisah. */
    private sealed interface FlowItem {
        val spaceBeforeMm10: Int

        data class Text(
            val text: String,
            val role: SubscriptionInvoiceLineRole,
            override val spaceBeforeMm10: Int = 0
        ) : FlowItem

        /**
         * Label di kiri, angka di kanan. Angka selalu **berakhir** di margin kanan, mengikuti perataan
         * [rightRole] — bukan ditempel setelah label dengan jarak tetap.
         */
        data class Columns(
            val left: String,
            val leftRole: SubscriptionInvoiceLineRole,
            val right: String,
            val rightRole: SubscriptionInvoiceLineRole,
            override val spaceBeforeMm10: Int = 0
        ) : FlowItem

        data class Rule(override val spaceBeforeMm10: Int = SPACE_BEFORE_RULE_MM10) : FlowItem
    }

    /**
     * Urutan bacaan dokumen: identitas tagihan → rincian per modul → total → catatan. Angka rupiah
     * sudah berlabel dari dokumen ([SubscriptionInvoicePdfDocument]), jadi tata letak tidak pernah
     * menyentuh `Long` harga — satu tempat yang boleh memformat uang berarti satu tempat yang bisa salah.
     */
    private fun flowItems(document: SubscriptionInvoicePdfDocument): List<FlowItem> = buildList {
        val heading = SubscriptionInvoiceLineRole.HEADING
        add(FlowItem.Text(document.title, SubscriptionInvoiceLineRole.TITLE))
        add(
            FlowItem.Text(
                "Diterbitkan platform untuk ${document.tenantName} (${document.tenantId})",
                SubscriptionInvoiceLineRole.SUBTITLE
            )
        )
        add(FlowItem.Rule(spaceBeforeMm10 = 0))

        add(FlowItem.Text("Nomor invoice: ${document.number}", SubscriptionInvoiceLineRole.BODY))
        add(FlowItem.Text("Periode tagihan: ${document.period}", SubscriptionInvoiceLineRole.BODY))
        add(FlowItem.Text("Status: ${document.statusLabel}", SubscriptionInvoiceLineRole.BODY))
        document.issuedAtLabel?.let {
            add(FlowItem.Text("Diterbitkan pada: $it", SubscriptionInvoiceLineRole.BODY))
        }
        document.paidAtLabel?.let {
            add(FlowItem.Text("Dibayar pada: $it", SubscriptionInvoiceLineRole.BODY))
        }
        document.paidNote?.let {
            add(FlowItem.Text("Catatan pembayaran: $it", SubscriptionInvoiceLineRole.BODY))
        }

        add(
            FlowItem.Text(
                "Rincian modul (${document.lineCount})",
                heading,
                spaceBeforeMm10 = SPACE_BEFORE_HEADING_MM10
            )
        )
        add(
            FlowItem.Columns(
                left = "Modul",
                leftRole = SubscriptionInvoiceLineRole.COLUMN_LEFT,
                right = "Harga / bulan",
                rightRole = SubscriptionInvoiceLineRole.COLUMN_RIGHT,
                spaceBeforeMm10 = SPACE_BEFORE_RULE_MM10
            )
        )
        // Garis di bawah kepala kolom: batas antara "judul kolom" dan "isi kolom". Tanpa ini, baris
        // pertama terbaca menyatu dengan kepala kolomnya.
        add(FlowItem.Rule(spaceBeforeMm10 = 0))
        document.lines.forEach { line ->
            add(
                FlowItem.Columns(
                    left = "${line.displayName} (${line.moduleId}) · ${line.kindLabel}",
                    leftRole = SubscriptionInvoiceLineRole.LABEL,
                    right = line.priceLabel,
                    rightRole = SubscriptionInvoiceLineRole.AMOUNT
                )
            )
        }
        // Garis penutup rincian, sebelum total: memisahkan "daftar harga" dari "yang harus dibayar".
        add(FlowItem.Rule())
        add(
            FlowItem.Columns(
                left = "Total per bulan",
                leftRole = SubscriptionInvoiceLineRole.TOTAL_LABEL,
                right = document.totalLabel,
                rightRole = SubscriptionInvoiceLineRole.TOTAL_AMOUNT
            )
        )

        if (document.notes.isNotEmpty()) {
            add(FlowItem.Text("Catatan", heading, spaceBeforeMm10 = SPACE_BEFORE_HEADING_MM10))
            document.notes.forEach { add(FlowItem.Text(it, SubscriptionInvoiceLineRole.NOTE)) }
        }
    }

    /**
     * Penempat baris: satu-satunya tempat `y` bergerak.
     *
     * Halaman berikutnya selalu diberi judul lanjutan, dan judul itu ditulis lewat [place] — bukan
     * [emit] — supaya ia tidak pernah memicu pemenggalan halaman baru lagi (halaman yang baru dibuka
     * tak mungkin penuh oleh satu baris).
     */
    private class PageBuilder(
        private val document: SubscriptionInvoicePdfDocument,
        private val bodyWidthMm10: Int,
        private val bottomLimitMm10: Int
    ) {
        val pages: MutableList<MutableList<SubscriptionInvoiceLine>> = mutableListOf(mutableListOf())
        private var y: Int = MARGIN_TOP_MM10

        fun emit(item: FlowItem) {
            // Jarak antar-seksi hanya berlaku di tengah halaman: di puncak halaman baru ia hanya
            // menyisakan lubang kosong di bawah judul lanjutan.
            if (item.spaceBeforeMm10 > 0 && pages.last().isNotEmpty()) y += item.spaceBeforeMm10

            when (item) {
                is FlowItem.Text -> emitText(item.text, item.role)
                is FlowItem.Columns -> emitColumns(item)
                is FlowItem.Rule -> emitRule()
            }
        }

        private fun emitText(text: String, role: SubscriptionInvoiceLineRole) {
            val lineHeight = InvoiceTextLayout.lineHeightMm10(role.style)
            InvoiceTextLayout.wrap(text, bodyWidthMm10, role.style).forEach { line ->
                if (y + lineHeight > bottomLimitMm10) openPage()
                place(line, role, leftX = MARGIN_X_MM10, width = bodyWidthMm10, lineHeight = lineHeight)
            }
        }

        /**
         * Pasangan label/angka. Lebar kolom kanan direservasi lebih dulu — dari baris terpanjangnya —
         * lalu sisa lebar menjadi milik label. Bila label tetap perlu beberapa baris, angkanya duduk di
         * baris **pertama**, tempat mata mencarinya.
         */
        private fun emitColumns(item: FlowItem.Columns) {
            val rightLines = InvoiceTextLayout.wrap(item.right, bodyWidthMm10, item.rightRole.style)
            val rightReserved = rightLines
                .maxOf { InvoiceTextLayout.measureWidthMm10(it, item.rightRole.style) }
                .coerceAtMost(bodyWidthMm10)
            val leftWidth = (bodyWidthMm10 - rightReserved - COLUMN_GAP_MM10).coerceAtLeast(1)
            val leftLines = InvoiceTextLayout.wrap(item.left, leftWidth, item.leftRole.style)

            val leftHeight = InvoiceTextLayout.lineHeightMm10(item.leftRole.style)
            val rightHeight = InvoiceTextLayout.lineHeightMm10(item.rightRole.style)
            val blockHeight = lineHeightOf(leftLines.size, leftHeight, rightLines.size, rightHeight)
            if (y + blockHeight > bottomLimitMm10) openPage()

            val blockTop = y
            leftLines.forEach { line ->
                place(line, item.leftRole, leftX = MARGIN_X_MM10, width = leftWidth, lineHeight = leftHeight)
            }
            // Angka dicetak belakangan, dan `y` dikembalikan ke puncak blok dulu: kolom kanan mulai di
            // baris pertama label, bukan di akhir label yang mungkin berbaris-baris.
            y = blockTop
            rightLines.forEach { line ->
                val width = InvoiceTextLayout.measureWidthMm10(line, item.rightRole.style)
                place(
                    text = line,
                    role = item.rightRole,
                    leftX = MARGIN_X_MM10 + bodyWidthMm10 - width,
                    width = width,
                    lineHeight = rightHeight
                )
            }
            y = blockTop + blockHeight
        }

        private fun emitRule() {
            if (pages.last().isNotEmpty() && y + RULE_HEIGHT_MM10 > bottomLimitMm10) openPage()
            pages.last() += SubscriptionInvoiceLine(
                text = "",
                role = SubscriptionInvoiceLineRole.RULE,
                rect = TemplateRect(Mm10(MARGIN_X_MM10), Mm10(y), Mm10(bodyWidthMm10), Mm10.ZERO)
            )
            y += RULE_HEIGHT_MM10
        }

        private fun place(
            text: String,
            role: SubscriptionInvoiceLineRole,
            leftX: Int,
            width: Int,
            lineHeight: Int
        ) {
            pages.last() += SubscriptionInvoiceLine(
                text = text,
                role = role,
                rect = TemplateRect(Mm10(leftX), Mm10(y), Mm10(width), Mm10(lineHeight))
            )
            y += lineHeight
        }

        private fun openPage() {
            pages.add(mutableListOf())
            y = MARGIN_TOP_MM10
            val headingRole = SubscriptionInvoiceLineRole.SUBTITLE
            place(
                text = "${document.title} · ${document.number} $CONTINUATION_SUFFIX",
                role = headingRole,
                leftX = MARGIN_X_MM10,
                width = bodyWidthMm10,
                lineHeight = InvoiceTextLayout.lineHeightMm10(headingRole.style)
            )
        }

        /** Tinggi blok = tinggi sisi yang paling banyak memakai baris. */
        private fun lineHeightOf(leftLines: Int, leftHeight: Int, rightLines: Int, rightHeight: Int): Int =
            maxOf(leftLines * leftHeight, rightLines * rightHeight)
    }
}

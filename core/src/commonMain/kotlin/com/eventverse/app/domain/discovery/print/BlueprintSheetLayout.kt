package com.eventverse.app.domain.discovery.print

import com.eventverse.app.domain.invoicing.template.InvoiceTextLayout
import com.eventverse.app.domain.printing.Mm10
import com.eventverse.app.domain.printing.PaperSize
import com.eventverse.app.domain.printing.TemplateRect

/**
 * Tata letak lembar blueprint A4 — murni, deterministik, dan bebas platform.
 *
 * ## Mengapa pemotongan baris tidak diulang di renderer
 *
 * Renderer PDF tidak boleh memutus barisnya sendiri: kalau ia memakai pengukurnya sendiri, PDF yang
 * dihasilkan bisa memuat baris yang berbeda dari yang dihitung di sini — dan itu jenis bug yang hanya
 * terlihat setelah dokumen dicetak. Karena itu baris dipecah **sekali** lewat [InvoiceTextLayout],
 * pemecah baris dokumen cetak platform (metrik glyph-nya dibangkitkan dari TTF yang sama dengan yang
 * dipakai PDFBox; dijaga `InvoiceFontMetricsDriftTest`).
 *
 * ## Pemenggalan halaman terjadi di tingkat **baris**, bukan blok
 *
 * Deskripsi blueprint bisa panjang, dan pemenggalan per blok akan mendorong satu paragraf utuh ke
 * halaman berikutnya lalu menyisakan setengah halaman kosong di atasnya. Karena itu tiap baris hasil
 * pemotongan diperiksa satu per satu; halaman baru diberi judul "(lanjutan)" supaya halaman yang
 * terpisah dari halaman pertamanya tetap bisa diidentifikasi saat difotokopi.
 */
object BlueprintSheetLayout {

    val PAPER: PaperSize = PaperSize.A4

    const val MARGIN_X_MM10 = 150
    const val MARGIN_TOP_MM10 = 130
    const val MARGIN_BOTTOM_MM10 = 140

    /** Jarak badan dokumen ke blok footer, supaya teks terakhir tidak menempel ke catatan kaki. */
    const val FOOTER_GAP_MM10 = 70

    /** Ruang sebelum judul seksi; tanpa ini PDF terbaca sebagai satu dinding teks. */
    const val SPACE_BEFORE_HEADING_MM10 = 70

    const val WATERMARK_FONT_PT = 40
    const val WATERMARK_ANGLE_DEG = 33f

    const val CONTINUATION_SUFFIX = "(lanjutan)"

    private const val ACTIVE_MARK = "[aktif]"
    private const val BYPASS_MARK = "[bypass]"

    fun layout(document: BlueprintPdfDocument): BlueprintSheet {
        val bodyWidth = PAPER.widthMm10 - 2 * MARGIN_X_MM10
        val footerNoteLines = InvoiceTextLayout.wrap(document.footerNote, bodyWidth, BlueprintLineRole.FOOTER.style)
        val footerHeight = InvoiceTextLayout.lineHeightMm10(BlueprintLineRole.FOOTER.style) * (1 + footerNoteLines.size)
        val bottomLimit = PAPER.heightMm10 - MARGIN_BOTTOM_MM10 - footerHeight - FOOTER_GAP_MM10

        val builder = PageBuilder(document, bodyWidth, bottomLimit)
        flowItems(document).forEach(builder::emit)

        val total = builder.pages.size
        val pages = builder.pages.mapIndexed { pageIndex, body ->
            BlueprintPage(
                index = pageIndex + 1,
                body = body,
                footer = footerLines(pageIndex + 1, total, footerHeight, bodyWidth, footerNoteLines),
                watermark = watermark(document)
            )
        }
        return BlueprintSheet(document, pages)
    }

    /** Penanda diagonal di tengah kertas; `width`/`height` nol karena renderer memutarnya sendiri. */
    private fun watermark(document: BlueprintPdfDocument) = BlueprintWatermark(
        text = document.watermark,
        center = TemplateRect(Mm10(PAPER.widthMm10 / 2), Mm10(PAPER.heightMm10 / 2), Mm10.ZERO, Mm10.ZERO),
        fontSizePt = WATERMARK_FONT_PT,
        angleDeg = WATERMARK_ANGLE_DEG
    )

    /**
     * Footer diulang di tiap halaman: nomor halaman untuk membuktikan tidak ada lembar yang hilang,
     * dan catatan kaki yang menyatakan dokumen ini bukan penawaran harga.
     */
    private fun footerLines(
        page: Int,
        total: Int,
        heightMm10: Int,
        widthMm10: Int,
        noteLines: List<String>
    ): List<BlueprintLine> {
        val lineHeight = InvoiceTextLayout.lineHeightMm10(BlueprintLineRole.FOOTER.style)
        var y = PAPER.heightMm10 - MARGIN_BOTTOM_MM10 - heightMm10
        val lines = mutableListOf<BlueprintLine>()
        (listOf("Halaman $page dari $total") + noteLines).forEach { text ->
            lines += BlueprintLine(
                text = text,
                role = BlueprintLineRole.FOOTER,
                rect = TemplateRect(Mm10(MARGIN_X_MM10), Mm10(y), Mm10(widthMm10), Mm10(lineHeight))
            )
            y += lineHeight
        }
        return lines
    }

    private data class FlowItem(
        val text: String,
        val role: BlueprintLineRole,
        val indentMm10: Int = role.indentMm10,
        val spaceBeforeMm10: Int = 0
    )

    /**
     * Urutan bacaan dokumen. Disusun sebagai daftar aliran (flow) supaya pemenggalan halaman tidak
     * perlu tahu apa pun tentang isi — ia hanya memindahkan baris berikutnya ke halaman berikutnya.
     */
    private fun flowItems(document: BlueprintPdfDocument): List<FlowItem> = buildList {
        add(FlowItem(document.title, BlueprintLineRole.TITLE))
        add(FlowItem("${document.packName} (${document.packCode})", BlueprintLineRole.SUBTITLE))
        add(FlowItem("${document.blueprintName} · ${document.badge}", BlueprintLineRole.BADGE))
        add(FlowItem("Dibuat ${document.generatedAtLabel}", BlueprintLineRole.NOTE))

        add(FlowItem("Ringkasan", BlueprintLineRole.HEADING, spaceBeforeMm10 = SPACE_BEFORE_HEADING_MM10))
        add(FlowItem(document.description, BlueprintLineRole.BODY))
        add(FlowItem("Profil klien sasaran: ${document.targetClientProfile}", BlueprintLineRole.BODY))

        if (document.terms.isNotEmpty()) {
            add(FlowItem("Istilah vertikal", BlueprintLineRole.HEADING, spaceBeforeMm10 = SPACE_BEFORE_HEADING_MM10))
            // Panah ditulis ASCII ("->"), bukan "→": font cetak yang dibundel tidak punya glyph U+2192,
            // dan baris yang gagal digambar bukan sekadar jelek — seluruh PDF-nya tidak jadi terbit.
            document.terms.forEach { (neutral, word) -> add(FlowItem("$neutral -> $word", BlueprintLineRole.ROW)) }
        }

        add(FlowItem("Fase alur", BlueprintLineRole.HEADING, spaceBeforeMm10 = SPACE_BEFORE_HEADING_MM10))
        document.phases.forEach { add(FlowItem(it, BlueprintLineRole.ROW)) }

        add(
            FlowItem(
                "Modul (${document.activeModuleCount} aktif dari ${document.modules.size})",
                BlueprintLineRole.HEADING,
                spaceBeforeMm10 = SPACE_BEFORE_HEADING_MM10
            )
        )
        document.modules.forEach { module ->
            add(FlowItem(moduleHeadline(module), BlueprintLineRole.ROW))
            module.parameters.forEach { (key, value) -> add(FlowItem("$key = $value", BlueprintLineRole.DETAIL)) }
        }

        if (document.screens.isNotEmpty()) {
            add(
                FlowItem(
                    "Layar kustom (${document.screens.size})",
                    BlueprintLineRole.HEADING,
                    spaceBeforeMm10 = SPACE_BEFORE_HEADING_MM10
                )
            )
            document.screens.forEach { add(FlowItem(it, BlueprintLineRole.ROW)) }
        }
    }

    /** `[aktif]`/`[bypass]` di depan, bukan di belakang: mata memindai kolom kiri lebih dulu. */
    private fun moduleHeadline(module: BlueprintModuleLine): String = buildString {
        append(if (module.active) ACTIVE_MARK else BYPASS_MARK)
        append(' ').append(module.displayName).append(" (").append(module.moduleCode).append(')')
        append(" · ").append(module.sectionName)
        module.phaseName?.let { append(" · ").append(it) }
    }

    /**
     * Penempat baris: satu-satunya tempat `y` bergerak.
     *
     * Halaman berikutnya selalu diberi judul lanjutan, dan judul itu ditulis lewat [place] — bukan
     * [emit] — supaya ia tidak pernah memicu pemenggalan halaman baru lagi (halaman yang baru dibuka
     * tak mungkin penuh oleh satu baris).
     */
    private class PageBuilder(
        private val document: BlueprintPdfDocument,
        private val bodyWidthMm10: Int,
        private val bottomLimitMm10: Int
    ) {
        val pages: MutableList<MutableList<BlueprintLine>> = mutableListOf(mutableListOf())
        private var y: Int = MARGIN_TOP_MM10

        fun emit(item: FlowItem) {
            // Jarak antar-seksi hanya berlaku di tengah halaman: di puncak halaman baru ia hanya
            // menyisakan lubang kosong di bawah judul lanjutan.
            if (item.spaceBeforeMm10 > 0 && pages.last().isNotEmpty()) y += item.spaceBeforeMm10

            val width = bodyWidthMm10 - item.indentMm10
            val lineHeight = InvoiceTextLayout.lineHeightMm10(item.role.style)
            InvoiceTextLayout.wrap(item.text, width, item.role.style).forEach { text ->
                if (y + lineHeight > bottomLimitMm10) openPage()
                place(text, item.role, item.indentMm10, width, lineHeight)
            }
        }

        private fun place(text: String, role: BlueprintLineRole, indentMm10: Int, width: Int, lineHeight: Int) {
            pages.last() += BlueprintLine(
                text = text,
                role = role,
                rect = TemplateRect(
                    Mm10(MARGIN_X_MM10 + indentMm10),
                    Mm10(y),
                    Mm10(width),
                    Mm10(lineHeight)
                )
            )
            y += lineHeight
        }

        private fun openPage() {
            pages.add(mutableListOf())
            y = MARGIN_TOP_MM10
            val heading = "${document.title} · ${document.blueprintName} $CONTINUATION_SUFFIX"
            val headingRole = BlueprintLineRole.SUBTITLE
            place(
                text = heading,
                role = headingRole,
                indentMm10 = headingRole.indentMm10,
                width = bodyWidthMm10 - headingRole.indentMm10,
                lineHeight = InvoiceTextLayout.lineHeightMm10(headingRole.style)
            )
        }
    }
}

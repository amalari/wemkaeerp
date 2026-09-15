package com.eventverse.app.domain.invoicing.template

import com.eventverse.app.domain.common.Money
import com.eventverse.app.domain.invoicing.Invoice

/**
 * Hasil tata letak satu elemen setelah seluruh aturan dinamis diterapkan.
 *
 * @param rect Geometri efektif yang boleh digambar — sudah memuat tinggi turunan dan, untuk elemen
 *   ber-anchor, pergeseran akibat tabel item yang memanjang.
 * @param textLines Baris teks hasil [InvoiceTextLayout.wrap]. **Bukan saran**: mesin gambar wajib
 *   memakainya apa adanya agar kanvas dan PDF memotong baris di titik yang sama. Kosong untuk
 *   elemen yang bukan teks.
 */
data class LaidOutElement(
    val element: TemplateElement,
    val rect: TemplateRect,
    val textLines: List<String> = emptyList()
) {
    val isText: Boolean get() = textLines.isNotEmpty()
}

/**
 * Penyelesai geometri dokumen faktur.
 *
 * ## Masalah yang dipecahkan
 *
 * Sebelum ini, dua aturan dinamis hanya hidup di renderer PDF dan tidak ada di kanvas: pergeseran
 * elemen ber-`anchorBelowTable` akibat tabel yang memanjang, dan tinggi tabel yang ikut jumlah baris
 * faktur. Akibatnya kanvas menampilkan posisi yang berbeda dari hasil cetak — selisih yang selama ini
 * tersembunyi karena template bawaan kebetulan hanya punya dua baris.
 *
 * Begitu tinggi elemen menjadi turunan (mengikuti panjang teks, bukan angka yang diketik pengguna),
 * selisih itu ikut berubah setiap kali teks diubah, sehingga tidak bisa lagi diabaikan.
 *
 * ## Aturan yang ditegakkan di sini
 *
 * 1. **Tinggi teks diturunkan** dari [InvoiceTextLayout] — pengguna hanya mengatur lebar.
 * 2. **Tabel item tidak pernah menyusut** di bawah tinggi yang dirancang, tapi tumbuh bila baris
 *    faktur lebih banyak. Ini disengaja: kalau tinggi tabel ikut ditulis hasil hitungan seperti teks,
 *    selisih tinggi tabel akan selalu nol dan seluruh elemen ber-anchor berhenti mengikuti tabel.
 * 3. **`anchorBelowTable` digeser dari posisi tersimpan**, bukan dari posisi hasil geser. Menulis
 *    hasil geser kembali ke model akan membuat pergeserannya menumpuk setiap kali dihitung ulang —
 *    elemen ber-anchor merayap turun pada setiap perubahan teks.
 * 4. **Elemen tidak pernah keluar kertas**, termasuk setelah pergeseran dinamis.
 *
 * Hasil [solve] dipakai **kanvas Compose dan PDFBox server** tanpa pengecualian, sehingga "yang
 * dirancang" dan "yang dicetak" adalah satu geometri yang sama.
 */
object InvoiceDocumentLayout {

    /** Tinggi tabel item yang dibutuhkan untuk menampung seluruh baris faktur. */
    fun requiredTableHeightMm10(table: TemplateElement.ItemTable, lineCount: Int): Int {
        val headerMm10 = if (table.showHeader) table.rowHeight.value else 0
        return headerMm10 + (lineCount.coerceAtLeast(0) * table.rowHeight.value)
    }

    /**
     * Pertambahan tinggi tabel dibanding tinggi yang dirancang. Elemen ber-`anchorBelowTable`
     * digeser turun sebesar ini agar tidak tertimpa baris tabel yang bertambah.
     */
    fun tableDeltaMm10(template: InvoiceTemplate, invoice: Invoice): Int {
        val table = template.itemTable ?: return 0
        val required = requiredTableHeightMm10(table, invoice.lines.size)
        return (required - table.rect.height.value).coerceAtLeast(0)
    }

    /** Menyelesaikan geometri seluruh elemen, terurut sesuai `zOrder`. */
    fun solve(
        template: InvoiceTemplate,
        invoice: Invoice,
        paidAmount: Money = Money.zero(invoice.currency)
    ): List<LaidOutElement> {
        val delta = tableDeltaMm10(template, invoice)
        return template.elements
            .sortedBy { it.zOrder }
            .map { element -> layOut(element, template, invoice, paidAmount, delta) }
    }

    /**
     * Menuliskan tinggi turunan kembali ke model template.
     *
     * Dipanggil setelah setiap perubahan yang bisa mengubah panjang teks (isi, ukuran font) atau
     * lebarnya. **Hanya tinggi** yang ditulis; posisi `y` tidak, karena pergeseran `anchorBelowTable`
     * adalah hasil render, bukan nilai yang disimpan (lihat aturan 3 di atas).
     */
    fun measureHeights(
        template: InvoiceTemplate,
        invoice: Invoice,
        paidAmount: Money = Money.zero(invoice.currency)
    ): InvoiceTemplate {
        val delta = tableDeltaMm10(template, invoice)
        val measured = template.elements.associate { element ->
            element.elementId to layOut(element, template, invoice, paidAmount, delta).rect.height
        }
        return template.copy(
            elements = template.elements.map { element ->
                val height = measured[element.elementId] ?: return@map element
                if (height == element.rect.height) element else element.withDerivedHeight(height)
            }
        )
    }

    private fun layOut(
        element: TemplateElement,
        template: InvoiceTemplate,
        invoice: Invoice,
        paidAmount: Money,
        tableDeltaMm10: Int
    ): LaidOutElement {
        val heightMm10 = derivedHeightMm10(element, invoice, paidAmount)
        val shiftedY =
            if (element.anchorBelowTable) element.rect.y.value + tableDeltaMm10 else element.rect.y.value
        val maxY = (template.paperSize.height.value - heightMm10).coerceAtLeast(0)

        return LaidOutElement(
            element = element,
            rect = element.rect.copy(
                y = Mm10(shiftedY.coerceIn(0, maxY)),
                height = Mm10(heightMm10)
            ),
            textLines = textLinesOf(element, invoice, paidAmount)
        )
    }

    private fun derivedHeightMm10(
        element: TemplateElement,
        invoice: Invoice,
        paidAmount: Money
    ): Int = when (element) {
        is TemplateElement.StaticText ->
            InvoiceTextLayout.measureHeightMm10(element.text, element.rect.width.value, element.style)

        is TemplateElement.BoundField ->
            InvoiceTextLayout.measureHeightMm10(
                element.prefix + resolveText(element, invoice, paidAmount) + element.suffix,
                element.rect.width.value,
                element.style
            )

        is TemplateElement.ItemTable -> maxOf(
            element.rect.height.value,
            requiredTableHeightMm10(element, invoice.lines.size)
        )

        // Garis: tingginya adalah ketebalan goresan, bukan isi teks — tetap seperti yang dirancang.
        // Kotak & gambar: belum bisa mengubah ukurannya sendiri, jadi tetap.
        is TemplateElement.LineShape,
        is TemplateElement.RectShape,
        is TemplateElement.ImageBox -> element.rect.height.value
    }

    private fun textLinesOf(
        element: TemplateElement,
        invoice: Invoice,
        paidAmount: Money
    ): List<String> = when (element) {
        is TemplateElement.StaticText ->
            InvoiceTextLayout.wrap(element.text, element.rect.width.value, element.style)

        is TemplateElement.BoundField ->
            InvoiceTextLayout.wrap(
                element.prefix + resolveText(element, invoice, paidAmount) + element.suffix,
                element.rect.width.value,
                element.style
            )

        else -> emptyList()
    }

    /** Teks isi tanpa prefix/suffix — dipakai bersama oleh pengukur tinggi dan pemecah baris. */
    private fun resolveText(
        element: TemplateElement.BoundField,
        invoice: Invoice,
        paidAmount: Money
    ): String = when (val resolved = InvoiceBindingResolver.resolve(element.binding, invoice, null, paidAmount)) {
        is ResolvedBindingValue.Text -> resolved.value
        is ResolvedBindingValue.Image -> resolved.assetUrl ?: ""
        is ResolvedBindingValue.Empty -> ""
    }
}

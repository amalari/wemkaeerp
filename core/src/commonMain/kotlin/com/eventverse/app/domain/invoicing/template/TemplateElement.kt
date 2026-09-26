package com.eventverse.app.domain.invoicing.template

import com.eventverse.app.domain.common.Ratio

data class TableColumn(
    val binding: BindingToken,
    val header: String,
    val widthRatio: Ratio,
    val align: TextAlign = TextAlign.LEFT
) {
    init {
        require(header.isNotBlank()) { "Judul kolom tabel tidak boleh kosong." }
        require(widthRatio > Ratio.ZERO) { "Lebar rasio kolom harus lebih besar dari 0." }
    }
}

/**
 * Gaya teks elemen, atau `null` bila elemen itu tidak menggambar teks bergaya tunggal.
 *
 * [TemplateElement.ItemTable] sengaja mengembalikan `null` walaupun ia menggambar teks: ia punya
 * **dua** gaya (judul dan isi) dan ratusan sel dengan lebar berbeda-beda, jadi tidak ada satu gaya
 * yang bisa mewakilinya. Pengukuran sel tabel adalah persoalan tersendiri.
 */
val TemplateElement.textStyleOrNull: TextStyleSpec?
    get() = when (this) {
        is TemplateElement.StaticText -> style
        is TemplateElement.BoundField -> style
        is TemplateElement.ItemTable,
        is TemplateElement.ImageBox,
        is TemplateElement.LineShape,
        is TemplateElement.RectShape -> null
    }

sealed interface TemplateElement {
    val elementId: String
    val rect: TemplateRect
    val zOrder: Double
    val anchorBelowTable: Boolean

    fun withRect(newRect: TemplateRect): TemplateElement
    fun withAnchorBelowTable(anchor: Boolean): TemplateElement

    /**
     * Menuliskan tinggi hasil hitung tata letak ([InvoiceDocumentLayout]).
     *
     * Tinggi elemen teks bukan lagi angka yang diketik pengguna, melainkan turunan dari isi teks,
     * ukuran font, dan lebar kotak. Elemen yang tingginya memang ditentukan sendiri (garis, kotak,
     * gambar) mengembalikan dirinya sendiri tanpa perubahan.
     */
    fun withDerivedHeight(height: Mm10): TemplateElement

    /** Mengganti gaya teks. Elemen yang tidak punya gaya teks mengembalikan dirinya sendiri. */
    fun withStyle(style: TextStyleSpec): TemplateElement

    data class StaticText(
        override val elementId: String,
        override val rect: TemplateRect,
        override val zOrder: Double = 0.0,
        override val anchorBelowTable: Boolean = false,
        val text: String,
        val style: TextStyleSpec = TextStyleSpec()
    ) : TemplateElement {
        override fun withRect(newRect: TemplateRect): StaticText = copy(rect = newRect)
        override fun withAnchorBelowTable(anchor: Boolean): StaticText = copy(anchorBelowTable = anchor)
        override fun withDerivedHeight(height: Mm10): StaticText = copy(rect = rect.copy(height = height))
        override fun withStyle(style: TextStyleSpec): StaticText = copy(style = style)
    }

    data class BoundField(
        override val elementId: String,
        override val rect: TemplateRect,
        override val zOrder: Double = 0.0,
        override val anchorBelowTable: Boolean = false,
        val binding: BindingToken,
        val prefix: String = "",
        val suffix: String = "",
        val style: TextStyleSpec = TextStyleSpec()
    ) : TemplateElement {
        override fun withRect(newRect: TemplateRect): BoundField = copy(rect = newRect)
        override fun withAnchorBelowTable(anchor: Boolean): BoundField = copy(anchorBelowTable = anchor)
        override fun withDerivedHeight(height: Mm10): BoundField = copy(rect = rect.copy(height = height))
        override fun withStyle(style: TextStyleSpec): BoundField = copy(style = style)
    }

    data class ImageBox(
        override val elementId: String,
        override val rect: TemplateRect,
        override val zOrder: Double = 0.0,
        override val anchorBelowTable: Boolean = false,
        val binding: BindingToken? = null,
        val assetUrl: String? = null
    ) : TemplateElement {
        override fun withRect(newRect: TemplateRect): ImageBox = copy(rect = newRect)
        override fun withAnchorBelowTable(anchor: Boolean): ImageBox = copy(anchorBelowTable = anchor)
        override fun withDerivedHeight(height: Mm10): ImageBox = this
        override fun withStyle(style: TextStyleSpec): ImageBox = this
    }

    data class RectShape(
        override val elementId: String,
        override val rect: TemplateRect,
        override val zOrder: Double = 0.0,
        override val anchorBelowTable: Boolean = false,
        val fillHex: Long? = null,
        val strokeHex: Long? = 0xFF1E293BL,
        val strokeMm10: Int = 2,
        val cornerMm10: Int = 0
    ) : TemplateElement {
        override fun withRect(newRect: TemplateRect): RectShape = copy(rect = newRect)
        override fun withAnchorBelowTable(anchor: Boolean): RectShape = copy(anchorBelowTable = anchor)
        override fun withDerivedHeight(height: Mm10): RectShape = this
        override fun withStyle(style: TextStyleSpec): RectShape = this
    }

    data class LineShape(
        override val elementId: String,
        override val rect: TemplateRect,
        override val zOrder: Double = 0.0,
        override val anchorBelowTable: Boolean = false,
        val strokeHex: Long = 0xFF1E293BL,
        val strokeMm10: Int = 2
    ) : TemplateElement {
        override fun withRect(newRect: TemplateRect): LineShape = copy(rect = newRect)
        override fun withAnchorBelowTable(anchor: Boolean): LineShape = copy(anchorBelowTable = anchor)
        override fun withDerivedHeight(height: Mm10): LineShape = this
        override fun withStyle(style: TextStyleSpec): LineShape = this
    }

    data class ItemTable(
        override val elementId: String,
        override val rect: TemplateRect,
        override val zOrder: Double = 0.0,
        override val anchorBelowTable: Boolean = false,
        val columns: List<TableColumn>,
        val rowHeight: Mm10 = Mm10(80), // 8.0 mm
        val headerStyle: TextStyleSpec = TextStyleSpec(fontSizePt = 9, isBold = true),
        val bodyStyle: TextStyleSpec = TextStyleSpec(fontSizePt = 9),
        val showHeader: Boolean = true,
        val zebraFillHex: Long? = null
    ) : TemplateElement {
        init {
            require(columns.isNotEmpty()) { "Tabel item harus memiliki minimal satu kolom." }
        }

        override fun withRect(newRect: TemplateRect): ItemTable = copy(rect = newRect)
        override fun withAnchorBelowTable(anchor: Boolean): ItemTable = copy(anchorBelowTable = anchor)
        override fun withDerivedHeight(height: Mm10): ItemTable = copy(rect = rect.copy(height = height))
        override fun withStyle(style: TextStyleSpec): ItemTable = this
    }
}

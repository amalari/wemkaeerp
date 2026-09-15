package com.eventverse.app.domain.invoicing.template

import com.eventverse.app.domain.common.Ratio

/**
 * Elemen yang bisa dipilih pengguna dari perpustakaan elemen desainer.
 *
 * Preset ada supaya **nilai bawaan sebuah elemen baru hidup di satu tempat**. Sebelumnya tombol
 * `+ Teks` di toolbar dan tombol `+ Kotak` masing-masing menulis sendiri koordinat, lebar, dan warna
 * bawaannya di dalam composable. Begitu elemen pertama bisa disisipkan dari dua tempat (palet dan
 * panel properti), duplikasi itu akan mulai menyimpang.
 */
sealed interface TemplateElementPreset {
    val displayName: String

    /** Elemen statis: isinya diketik pengguna dan tidak terhubung ke data mana pun. */
    data object StaticText : TemplateElementPreset {
        override val displayName: String get() = "Teks"
    }

    /** Garis pemisah horizontal. */
    data object Divider : TemplateElementPreset {
        override val displayName: String get() = "Divider"
    }

    /** Tabel baris item — maksimal satu per template (ditegakkan [InvoiceTemplate]). */
    data object ItemTable : TemplateElementPreset {
        override val displayName: String get() = "Tabel Baris Item"
    }

    /** Isian teks dinamis yang nilainya datang dari keluaran modul lain. */
    data class ModuleField(val descriptor: BindingDescriptor) : TemplateElementPreset {
        override val displayName: String get() = descriptor.displayName
    }
}

/** Nilai bawaan geometri elemen template, dipakai palet maupun panel properti. */
object InvoiceTemplateDefaults {

    /**
     * Lebar minimum elemen teks dalam 1/10 mm (20 mm).
     *
     * Di bawah ini, [InvoiceTextLayout] mulai memecah hampir setiap kata per karakter sehingga
     * hasilnya bukan lagi teks yang bisa dibaca — pengguna harus tahu batasnya di kanvas, bukan
     * setelah mencetak.
     */
    const val MIN_TEXT_WIDTH_MM10 = 200

    /** Jarak antar elemen bertumpuk saat penempatan otomatis, 5 mm. */
    const val STACK_GAP_MM10 = 50

    /** Tinggi kotak awal elemen teks; langsung diganti tinggi turunan saat diukur. */
    const val SEED_TEXT_HEIGHT_MM10 = 60
}

/** Lebar & tinggi yang diminta sebuah preset sebelum ditempatkan di kanvas. */
val TemplateElementPreset.requestedWidthMm10: Int
    get() = when (this) {
        is TemplateElementPreset.StaticText -> 1000
        is TemplateElementPreset.Divider -> 1800
        is TemplateElementPreset.ItemTable -> 1800
        is TemplateElementPreset.ModuleField -> descriptor.defaultWidthMm10
    }

val TemplateElementPreset.requestedHeightMm10: Int
    get() = when (this) {
        is TemplateElementPreset.StaticText,
        is TemplateElementPreset.ModuleField -> InvoiceTemplateDefaults.SEED_TEXT_HEIGHT_MM10

        // Tinggi garis adalah ketebalan goresannya, bukan ruang yang ditempatinya.
        is TemplateElementPreset.Divider -> 20

        // Cukup untuk header + tiga baris; tumbuh sendiri mengikuti jumlah baris faktur.
        is TemplateElementPreset.ItemTable -> 360
    }

/** Membangun elemen konkret dari preset, pada geometri yang sudah ditentukan pemanggil. */
object TemplateElementFactory {

    fun create(
        preset: TemplateElementPreset,
        elementId: String,
        rect: TemplateRect,
        zOrder: Double
    ): TemplateElement = when (preset) {
        is TemplateElementPreset.StaticText -> TemplateElement.StaticText(
            elementId = elementId,
            rect = rect,
            zOrder = zOrder,
            text = "Teks baru — klik dua kali untuk mengubah isinya.",
            style = TextStyleSpec(fontSizePt = 10)
        )

        is TemplateElementPreset.Divider -> TemplateElement.LineShape(
            elementId = elementId,
            rect = rect,
            zOrder = zOrder,
            strokeHex = InvoicePrintPalette.Body.hex,
            strokeMm10 = 2
        )

        is TemplateElementPreset.ItemTable -> TemplateElement.ItemTable(
            elementId = elementId,
            rect = rect,
            zOrder = zOrder,
            columns = defaultItemTableColumns()
        )

        is TemplateElementPreset.ModuleField -> TemplateElement.BoundField(
            elementId = elementId,
            rect = rect,
            zOrder = zOrder,
            binding = preset.descriptor.token,
            prefix = preset.descriptor.defaultPrefix,
            suffix = preset.descriptor.defaultSuffix,
            style = TextStyleSpec(fontSizePt = preset.descriptor.defaultFontSizePt)
        )
    }

    /**
     * Kolom bawaan tabel item.
     *
     * Diambil dari registry, bukan ditulis ulang di UI: kalau token `line.*` berubah nama, satu
     * tempat ini yang menyesuaikan, dan tabel bawaan tidak pernah menunjuk token yang tidak ada.
     */
    private fun defaultItemTableColumns(): List<TableColumn> {
        val byToken = InvoiceBindingRegistry.tableColumnTokens.associateBy { it.token.value }

        fun column(
            token: String,
            header: String,
            numerator: Long,
            align: TextAlign
        ): TableColumn? = byToken[token]?.let { descriptor ->
            TableColumn(
                binding = descriptor.token,
                header = header,
                widthRatio = Ratio.of(numerator, 12L),
                align = align
            )
        }

        return listOfNotNull(
            column("line.no", "#", 1L, TextAlign.CENTER),
            column("line.description", "Deskripsi Barang / Jasa", 5L, TextAlign.LEFT),
            column("line.quantity", "Qty", 2L, TextAlign.RIGHT),
            column("line.unitPrice", "Harga Satuan", 2L, TextAlign.RIGHT),
            column("line.amount", "Subtotal", 2L, TextAlign.RIGHT)
        )
    }
}

/**
 * Mencari posisi kosong untuk elemen baru.
 *
 * Aturannya sederhana dan dapat diprediksi: **di bawah elemen terendah yang sudah ada**, dengan jarak
 * satu grid. Elemen baru yang "muncul entah di mana" adalah keluhan paling umum pada penyusun
 * dokumen; menumpuknya ke bawah membuat urutan penambahan terasa seperti menulis dari atas ke bawah.
 *
 * Kalau ruang di bawah tidak cukup, elemen diletakkan di batas bawah area cetak — tetap terlihat dan
 * bisa dipindahkan, bukan menghilang di luar kertas.
 */
fun InvoiceTemplate.nextFreeRect(
    widthMm10: Int,
    heightMm10: Int,
    snapMm10: Int,
    marginMm10: Int = this.marginMm10
): TemplateRect {
    val paperWidth = paperSize.width.value
    val paperHeight = paperSize.height.value
    val minWidth = InvoiceTemplateDefaults.MIN_TEXT_WIDTH_MM10
    val width = widthMm10.coerceIn(minWidth, (paperWidth - marginMm10).coerceAtLeast(minWidth))
    val height = heightMm10.coerceAtLeast(1)
    val x = marginMm10.coerceIn(0, (paperWidth - width).coerceAtLeast(0))

    val lowestBottom = elements.maxOfOrNull { element -> element.rect.y.value + element.rect.height.value }
    val rawY = if (lowestBottom == null) marginMm10 else lowestBottom + InvoiceTemplateDefaults.STACK_GAP_MM10

    val maxY = (paperHeight - height - marginMm10).coerceAtLeast(0)
    val clampedY = rawY.coerceIn(0, maxY)
    val snappedY = if (snapMm10 > 0) (clampedY / snapMm10) * snapMm10 else clampedY

    return TemplateRect(
        x = Mm10(x),
        y = Mm10(snappedY.coerceIn(0, maxY)),
        width = Mm10(width),
        height = Mm10(height)
    )
}

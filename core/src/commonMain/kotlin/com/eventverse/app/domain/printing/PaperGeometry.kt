package com.eventverse.app.domain.printing

import kotlin.jvm.JvmInline

/**
 * Satuan ukuran kanvas invoice dalam 1/10 milimeter (integer).
 * Contoh: 1 mm = 10 Mm10, lebar A4 (210 mm) = 2100 Mm10.
 *
 * Menggunakan integer mencegah drift floating-point antar target KMP (JS/Wasm vs JVM).
 */
@JvmInline
value class Mm10(val value: Int) : Comparable<Mm10> {
    operator fun plus(other: Mm10): Mm10 = Mm10(value + other.value)
    operator fun minus(other: Mm10): Mm10 = Mm10(value - other.value)
    operator fun times(scalar: Int): Mm10 = Mm10(value * scalar)
    operator fun div(scalar: Int): Mm10 = Mm10(value / scalar)

    override fun compareTo(other: Mm10): Int = value.compareTo(other.value)

    fun toMillimeters(): Double = value / 10.0

    companion object {
        val ZERO = Mm10(0)
        fun fromMm(mm: Double): Mm10 = Mm10((mm * 10.0).toInt())
        fun fromMm(mm: Int): Mm10 = Mm10(mm * 10)
    }
}

/**
 * Batas geometris elemen di kanvas.
 */
data class TemplateRect(
    val x: Mm10,
    val y: Mm10,
    val width: Mm10,
    val height: Mm10
) {
    init {
        require(width.value >= 0) { "Lebar elemen tidak boleh negatif: ${width.value}." }
        require(height.value >= 0) { "Tinggi elemen tidak boleh negatif: ${height.value}." }
    }

    val right: Mm10 get() = x + width
    val bottom: Mm10 get() = y + height

    fun translated(dx: Mm10, dy: Mm10): TemplateRect =
        copy(x = x + dx, y = y + dy)

    /**
     * Menggeser elemen sebesar [dx]/[dy] lalu mengunci hasilnya ke grid [snapMm10] dan ke dalam
     * bidang kertas [paperWidth] × [paperHeight].
     *
     * Perilaku ini adalah aturan domain, bukan urusan UI: kanvas yang digambar dengan pixel,
     * tombol panah nudge, dan input milimeter di panel properti harus menghasilkan posisi yang
     * **identik** untuk perpindahan yang sama. Sebelumnya aturan ini hidup di dalam lambda
     * `detectDragGestures` sehingga hanya berlaku untuk jalur drag saja.
     *
     * `snapMm10 <= 0` berarti grid magnet dimatikan. Pembulatan selalu ke bawah (`floor`) supaya
     * elemen tidak pernah keluar dari kertas walau grid-nya lebih besar dari ruang sisa.
     */
    fun movedBy(
        dx: Mm10,
        dy: Mm10,
        snapMm10: Int,
        paperWidth: Mm10,
        paperHeight: Mm10
    ): TemplateRect {
        val maxX = (paperWidth.value - width.value).coerceAtLeast(0)
        val maxY = (paperHeight.value - height.value).coerceAtLeast(0)

        val rawX = (x.value + dx.value).coerceIn(0, maxX)
        val rawY = (y.value + dy.value).coerceIn(0, maxY)

        val snappedX = if (snapMm10 > 0) (rawX / snapMm10) * snapMm10 else rawX
        val snappedY = if (snapMm10 > 0) (rawY / snapMm10) * snapMm10 else rawY

        return copy(
            x = Mm10(snappedX.coerceIn(0, maxX)),
            y = Mm10(snappedY.coerceIn(0, maxY))
        )
    }

    /**
     * Mengubah **lebar** elemen saja, dengan penjepitan ke kertas dan lebar minimum.
     *
     * Dipecah dari [movedBy] karena artinya berbeda: [movedBy] memindahkan, fungsi ini mengubah
     * ukuran. Elemen teks hanya boleh diubah lebarnya — tingginya turunan dari isi teks (lihat
     * `InvoiceDocumentLayout`), sehingga menggeser sudut bawah akan langsung "dilawan" oleh
     * perhitungan ulang tinggi dan terasa seperti gagal.
     *
     * Lebar tidak boleh melebihi sisa ruang ke tepi kanan kertas: elemen yang menjulur keluar lembar
     * akan terpotong saat dicetak, dan pengguna tidak punya cara melihatnya di kanvas.
     */
    fun resizedWidth(
        newWidth: Mm10,
        minWidthMm10: Int,
        paperWidth: Mm10
    ): TemplateRect {
        val minWidth = minWidthMm10.coerceAtLeast(1)
        val maxWidth = (paperWidth.value - x.value).coerceAtLeast(minWidth)
        return copy(width = Mm10(newWidth.value.coerceIn(minWidth, maxWidth)))
    }
}

enum class PaperSize(val displayName: String, val widthMm10: Int, val heightMm10: Int) {
    A4("A4 (210 × 297 mm)", 2100, 2970),
    LETTER("Letter (216 × 279 mm)", 2159, 2794),
    A5("A5 (148 × 210 mm)", 1480, 2100);

    val width: Mm10 get() = Mm10(widthMm10)
    val height: Mm10 get() = Mm10(heightMm10)

    companion object {
        fun fromCode(code: String?): PaperSize =
            entries.firstOrNull { it.name.equals(code, ignoreCase = true) } ?: A4
    }
}

enum class TextAlign {
    LEFT, CENTER, RIGHT;

    companion object {
        fun fromCode(code: String?): TextAlign =
            entries.firstOrNull { it.name.equals(code, ignoreCase = true) } ?: LEFT
    }
}

data class TextStyleSpec(
    val fontSizePt: Int = 10,
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val align: TextAlign = TextAlign.LEFT,
    val colorHex: Long = 0xFF1E293BL // Slate-800
)

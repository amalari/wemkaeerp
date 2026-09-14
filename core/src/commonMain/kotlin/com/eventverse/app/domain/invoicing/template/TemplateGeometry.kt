package com.eventverse.app.domain.invoicing.template

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

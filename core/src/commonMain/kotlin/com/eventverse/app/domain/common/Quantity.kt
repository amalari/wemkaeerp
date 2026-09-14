package com.eventverse.app.domain.common

/**
 * Exact quantity representation using micros (6 decimal places).
 * 1 unit = 1_000_000 micros.
 */
data class Quantity(
    val micros: Long,
    val uom: UnitOfMeasure
) : Comparable<Quantity> {

    operator fun plus(other: Quantity): Quantity {
        return if (uom == other.uom) {
            Quantity(micros + other.micros, uom)
        } else {
            require(uom.canConvertTo(other.uom)) {
                "Tidak dapat menjumlahkan satuan ${other.uom.displayName} ke ${uom.displayName}"
            }
            val otherInThis = other.convertTo(uom)
            Quantity(micros + otherInThis.micros, uom)
        }
    }

    operator fun minus(other: Quantity): Quantity {
        return if (uom == other.uom) {
            Quantity(micros - other.micros, uom)
        } else {
            require(uom.canConvertTo(other.uom)) {
                "Tidak dapat mengurangkan satuan ${other.uom.displayName} dari ${uom.displayName}"
            }
            val otherInThis = other.convertTo(uom)
            Quantity(micros - otherInThis.micros, uom)
        }
    }

    operator fun times(scalar: Int): Quantity = Quantity(micros * scalar, uom)
    operator fun times(scalar: Long): Quantity = Quantity(micros * scalar, uom)
    operator fun times(ratio: Ratio): Quantity = Quantity(ratio.applyTo(micros), uom)

    operator fun div(scalar: Long): Quantity {
        require(scalar != 0L) { "Scalar pembagi tidak boleh 0" }
        return Quantity(micros / scalar, uom)
    }

    fun convertTo(targetUom: UnitOfMeasure): Quantity {
        if (uom == targetUom) return this
        require(uom.canConvertTo(targetUom)) {
            "Konversi gagal: tidak dapat mengonversi ${uom.displayName} (${uom.code}) ke ${targetUom.displayName} (${targetUom.code})"
        }
        val baseMicros = uom.toBaseMicros(micros)
        val targetMicros = targetUom.fromBaseMicros(baseMicros)
        return Quantity(targetMicros, targetUom)
    }

    fun ratioTo(other: Quantity): Ratio {
        require(uom.canConvertTo(other.uom)) {
            "Tidak dapat membandingkan rasio ${uom.code} terhadap ${other.uom.code}"
        }
        val otherInThis = other.convertTo(uom)
        return Ratio.of(micros, otherInThis.micros)
    }

    val isZero: Boolean get() = micros == 0L
    val isPositive: Boolean get() = micros > 0L
    val isNegative: Boolean get() = micros < 0L

    fun coerceAtLeastZero(): Quantity = if (micros < 0L) Quantity(0L, uom) else this

    fun toDouble(): Double = micros / 1_000_000.0

    override fun compareTo(other: Quantity): Int {
        return if (uom == other.uom) {
            micros.compareTo(other.micros)
        } else {
            require(uom.canConvertTo(other.uom)) {
                "Tidak dapat membandingkan ${uom.code} dengan ${other.uom.code}"
            }
            val otherInThis = other.convertTo(uom)
            micros.compareTo(otherInThis.micros)
        }
    }

    fun formatted(decimals: Int = 2): String {
        val whole = micros / 1_000_000L
        val frac = kotlin.math.abs(micros % 1_000_000L)
        if (frac == 0L) return "$whole ${uom.code}"
        val factor = when (decimals) {
            1 -> 100_000L
            2 -> 10_000L
            3 -> 1_000L
            else -> 1L
        }
        val roundedFrac = frac / factor
        return "$whole.$roundedFrac ${uom.code}"
    }

    companion object {
        fun of(value: Double, uom: UnitOfMeasure): Quantity =
            Quantity((value * 1_000_000.0).toLong(), uom)

        fun ofMicros(micros: Long, uom: UnitOfMeasure): Quantity = Quantity(micros, uom)

        fun grams(value: Double): Quantity = of(value, UnitOfMeasure.GRAM)
        fun kilograms(value: Double): Quantity = of(value, UnitOfMeasure.KILOGRAM)
        fun pieces(count: Int): Quantity = Quantity(count.toLong() * 1_000_000L, UnitOfMeasure.PIECE)
        fun zero(uom: UnitOfMeasure): Quantity = Quantity(0L, uom)
    }
}

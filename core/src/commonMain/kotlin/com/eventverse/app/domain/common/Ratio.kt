package com.eventverse.app.domain.common

/**
 * Exact rational factor [numerator] / [denominator] to prevent floating-point drift
 * across KMP targets (JS/Wasm vs JVM).
 */
data class Ratio(val numerator: Long, val denominator: Long) : Comparable<Ratio> {
    init {
        require(denominator != 0L) { "Denominator tidak boleh 0" }
    }

    fun applyTo(value: Long, rounding: Rounding = Rounding.HALF_UP): Long {
        if (value == 0L || numerator == 0L) return 0L
        val effectiveNumerator = value * numerator
        return divideWithRounding(effectiveNumerator, denominator, rounding)
    }

    override fun compareTo(other: Ratio): Int {
        // a/b vs c/d  ->  a*d vs c*b (with sign normalization)
        val n1 = if (denominator < 0) -numerator else numerator
        val d1 = if (denominator < 0) -denominator else denominator
        val n2 = if (other.denominator < 0) -other.numerator else other.numerator
        val d2 = if (other.denominator < 0) -other.denominator else other.denominator
        return (n1 * d2).compareTo(n2 * d1)
    }

    operator fun plus(other: Ratio): Ratio {
        val commonDenom = denominator * other.denominator
        val num = (numerator * other.denominator) + (other.numerator * denominator)
        return Ratio(num, commonDenom)
    }

    fun toDouble(): Double = numerator.toDouble() / denominator.toDouble()

    companion object {
        val ZERO = Ratio(0L, 1L)
        val ONE = Ratio(1L, 1L)

        fun of(numerator: Long, denominator: Long = 1L): Ratio = Ratio(numerator, denominator)

        fun percent(percentValue: Double): Ratio {
            val num = (percentValue * 10_000.0).toLong()
            return Ratio(num, 1_000_000L)
        }

        fun ofBasisPoints(basisPoints: Long): Ratio = Ratio(basisPoints, 10_000L)
    }
}

/**
 * Standard rounding strategies for exact financial and inventory arithmetic.
 */
enum class Rounding {
    HALF_UP,
    DOWN,
    UP
}

internal fun divideWithRounding(dividend: Long, divisor: Long, rounding: Rounding): Long {
    require(divisor != 0L) { "Divisor cannot be 0" }
    val isNegative = (dividend < 0) xor (divisor < 0)
    val absDividend = kotlin.math.abs(dividend)
    val absDivisor = kotlin.math.abs(divisor)

    val quotient = absDividend / absDivisor
    val remainder = absDividend % absDivisor

    val finalQuotient = when (rounding) {
        Rounding.DOWN -> quotient
        Rounding.UP -> if (remainder > 0) quotient + 1 else quotient
        Rounding.HALF_UP -> {
            // Check if remainder * 2 >= absDivisor
            if (remainder * 2 >= absDivisor) quotient + 1 else quotient
        }
    }

    return if (isNegative) -finalQuotient else finalQuotient
}

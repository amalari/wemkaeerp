package com.eventverse.app.domain.common

/**
 * Currency code with ISO minor unit precision.
 */
enum class CurrencyCode(val code: String, val minorDigits: Int, val symbol: String) {
    IDR("IDR", 2, "Rp"),
    USD("USD", 2, "$"),
    CNY("CNY", 2, "¥");

    val minorFactor: Long get() = when (minorDigits) {
        0 -> 1L
        1 -> 10L
        2 -> 100L
        3 -> 1_000L
        4 -> 10_000L
        else -> 100L
    }
}

/**
 * Monetary amount in minor units (e.g. cents/sen) to prevent float drift in production costing.
 *
 * NOTE: Berbeda dari [com.eventverse.app.domain.moduledev.MoneyIdr] yang khusus untuk billing
 * langganan SaaS (IDR-only, non-negatif, rupiah bulat), [Money] ini ditujukan untuk kalkulasi
 * costing manufaktur (multi-currency, presisi sen, bertanda).
 */
data class Money(
    val minorUnits: Long,
    val currency: CurrencyCode = CurrencyCode.IDR
) : Comparable<Money> {

    operator fun plus(other: Money): Money {
        require(currency == other.currency) {
            "Mata uang berbeda tidak dapat dijumlahkan: ${currency.code} vs ${other.currency.code}"
        }
        return Money(minorUnits + other.minorUnits, currency)
    }

    operator fun minus(other: Money): Money {
        require(currency == other.currency) {
            "Mata uang berbeda tidak dapat dikurangkan: ${currency.code} vs ${other.currency.code}"
        }
        return Money(minorUnits - other.minorUnits, currency)
    }

    operator fun unaryMinus(): Money = Money(-minorUnits, currency)

    operator fun times(scalar: Int): Money = Money(minorUnits * scalar, currency)
    operator fun times(scalar: Long): Money = Money(minorUnits * scalar, currency)

    operator fun times(ratio: Ratio): Money =
        Money(ratio.applyTo(minorUnits, Rounding.HALF_UP), currency)

    fun times(ratio: Ratio, rounding: Rounding = Rounding.HALF_UP): Money =
        Money(ratio.applyTo(minorUnits, rounding), currency)

    operator fun div(scalar: Long): Money {
        require(scalar != 0L) { "Scalar pembagi tidak boleh 0" }
        return Money(divideWithRounding(minorUnits, scalar, Rounding.HALF_UP), currency)
    }

    /**
     * Zero-loss penny allocation across weights.
     * Guarantees that the sum of allocated amounts exactly equals [minorUnits].
     */
    fun allocate(weights: List<Long>): List<Money> {
        if (weights.isEmpty()) return emptyList()
        val totalWeight = weights.sum()
        require(totalWeight > 0L) { "Total bobot alokasi harus lebih besar dari 0" }

        val shares = LongArray(weights.size)
        var remainder = minorUnits

        for (i in weights.indices) {
            val share = (minorUnits * weights[i]) / totalWeight
            shares[i] = share
            remainder -= share
        }

        // Distribute remaining cents to largest weights first
        val indexedWeights = weights.indices.sortedByDescending { weights[it] }
        var step = if (remainder >= 0) 1 else -1
        var rem = kotlin.math.abs(remainder)
        var idx = 0
        while (rem > 0) {
            shares[indexedWeights[idx % indexedWeights.size]] += step
            rem--
            idx++
        }

        return shares.map { Money(it, currency) }
    }

    val isZero: Boolean get() = minorUnits == 0L
    val isPositive: Boolean get() = minorUnits > 0L
    val isNegative: Boolean get() = minorUnits < 0L

    fun toWholeUnits(): Long = minorUnits / currency.minorFactor

    override fun compareTo(other: Money): Int {
        require(currency == other.currency) {
            "Tidak dapat membandingkan mata uang berbeda: ${currency.code} vs ${other.currency.code}"
        }
        return minorUnits.compareTo(other.minorUnits)
    }

    fun formatted(): String {
        val factor = currency.minorFactor
        val whole = minorUnits / factor
        val frac = kotlin.math.abs(minorUnits % factor)
        return if (frac == 0L) {
            "${currency.symbol} $whole"
        } else {
            val fracPadded = frac.toString().padStart(currency.minorDigits, '0')
            "${currency.symbol} $whole,$fracPadded"
        }
    }

    companion object {
        fun idr(amountInRupiah: Long): Money = Money(amountInRupiah * 100L, CurrencyCode.IDR)
        fun idrMinor(minorUnits: Long): Money = Money(minorUnits, CurrencyCode.IDR)
        fun zero(currency: CurrencyCode = CurrencyCode.IDR): Money = Money(0L, currency)

        fun sum(items: Iterable<Money>, currency: CurrencyCode = CurrencyCode.IDR): Money {
            var total = 0L
            for (m in items) {
                require(m.currency == currency) { "Mata uang berbeda dalam penjumlahan: ${m.currency} vs $currency" }
                total += m.minorUnits
            }
            return Money(total, currency)
        }
    }
}

/**
 * Unit price of a material or service: [amount] per [per] quantity.
 * Multiplies first, divides at the very end to prevent rounding drift.
 */
data class UnitPrice(
    val amount: Money,
    val per: Quantity
) {
    init {
        require(per.isPositive) { "Kuantitas acuan harga harus positif: ${per.formatted()}" }
    }

    /**
     * Exact costing calculation:
     * cost = (amount.minorUnits * qtyInPerUom.micros) / per.micros
     */
    fun costOf(quantity: Quantity, rounding: Rounding = Rounding.HALF_UP): Money {
        val qtyInPerUom = if (quantity.uom == per.uom) quantity else quantity.convertTo(per.uom)
        val effectiveNumerator = amount.minorUnits * qtyInPerUom.micros
        val denominator = per.micros
        val resultMinor = divideWithRounding(effectiveNumerator, denominator, rounding)
        return Money(resultMinor, amount.currency)
    }

    fun convertedTo(targetUom: UnitOfMeasure, rounding: Rounding = Rounding.HALF_UP): UnitPrice {
        if (targetUom == per.uom) return this
        val targetQty = Quantity.of(1.0, targetUom)
        val cost = costOf(targetQty, rounding)
        return UnitPrice(cost, targetQty)
    }
}

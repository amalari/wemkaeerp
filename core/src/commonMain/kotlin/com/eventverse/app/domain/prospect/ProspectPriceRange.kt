package com.eventverse.app.domain.prospect

import com.eventverse.app.domain.moduledev.MoneyIdr

/**
 * Rounds a price range for display.
 *
 * **Always outward — floor the bottom, ceiling the top — never to nearest.**
 *
 * Rounding to nearest can move the displayed ceiling *below* the real p90 price. The final figure
 * we send after review would then exceed a range the prospect has already seen, which is the one
 * direction a quote must never move. Outward rounding can only ever make the range wider than the
 * arithmetic, never narrower.
 */
object PriceRangeRounding {

    /**
     * Precision scales with magnitude, the way people quote prices anyway.
     *
     * A single fixed step cannot serve both ends of the scale. At Rp 500.000 granularity, a
     * Rp 300.000 subscription floors to **zero** — a displayed floor that reads as "free" and an
     * absurd "Rp 0 – 500.000" range. At Rp 50.000 granularity, a Rp 40 juta quote implies a
     * precision we do not have.
     */
    fun stepFor(amount: MoneyIdr): Long = when {
        amount.amount >= 10_000_000L -> 1_000_000L
        amount.amount >= 2_000_000L -> 500_000L
        amount.amount >= 500_000L -> 100_000L
        else -> 50_000L
    }

    fun floorTo(amount: MoneyIdr, step: Long = stepFor(amount)): MoneyIdr {
        require(step > 0) { "Rounding step must be positive" }
        return MoneyIdr(amount.amount / step * step)
    }

    fun ceilTo(amount: MoneyIdr, step: Long = stepFor(amount)): MoneyIdr {
        require(step > 0) { "Rounding step must be positive" }
        val remainder = amount.amount % step
        return if (remainder == 0L) amount else MoneyIdr(amount.amount - remainder + step)
    }
}

/**
 * What a prospect would pay per month, and whether we may say so yet.
 *
 * The range expresses uncertainty about **hours only**. It is the ledger's p50 and p90 carried all
 * the way through pricing, not a separate guess layered on top.
 */
data class ProspectPriceRange(
    /** Catalogue modules the prospect would run, at list price. Exact — not part of the range. */
    val subscriptionMonthly: MoneyIdr,
    /** Sum over gaps priced from p50 hours. Null when any gap could not be estimated. */
    val gapLowMonthly: MoneyIdr?,
    /** Sum over gaps priced from p90 hours. */
    val gapHighMonthly: MoneyIdr?,
    val unpriceableGapCount: Int,
    val expectedTenantCount: Int,
    val amortizationMonths: Int,
    val pricingModelVersion: String
) {
    init {
        require(unpriceableGapCount >= 0) { "unpriceableGapCount cannot be negative" }
        require(expectedTenantCount > 0) { "expectedTenantCount must be positive" }
        require(amortizationMonths > 0) { "amortizationMonths must be positive" }
    }

    /**
     * Whether a number may be shown to the prospect at all.
     *
     * One unestimated gap is enough to withhold everything. A total assembled from only the
     * priceable gaps is **guaranteed** to understate — we would be summing part of the work and
     * presenting it as the whole. "We will get back to you" is the honest answer, and it is also
     * the commercially safer one: a range that later has to rise is far worse than no range.
     */
    val isPublishable: Boolean
        get() = unpriceableGapCount == 0 && gapLowMonthly != null && gapHighMonthly != null

    val displayLow: MoneyIdr?
        get() = gapLowMonthly?.let { PriceRangeRounding.floorTo(subscriptionMonthly + it) }
            ?.takeIf { isPublishable }

    val displayHigh: MoneyIdr?
        get() = gapHighMonthly?.let { PriceRangeRounding.ceilTo(subscriptionMonthly + it) }
            ?.takeIf { isPublishable }

    companion object {
        /**
         * A range that cannot be shown: the catalogue part is known, the gaps are not.
         *
         * Keeps [subscriptionMonthly] because it is genuinely known and useful to a reviewer, while
         * [isPublishable] stays false.
         */
        fun withheld(
            subscriptionMonthly: MoneyIdr,
            unpriceableGapCount: Int,
            expectedTenantCount: Int = 1,
            amortizationMonths: Int = 24,
            pricingModelVersion: String = "v1"
        ) = ProspectPriceRange(
            subscriptionMonthly = subscriptionMonthly,
            gapLowMonthly = null,
            gapHighMonthly = null,
            unpriceableGapCount = unpriceableGapCount.coerceAtLeast(1),
            expectedTenantCount = expectedTenantCount,
            amortizationMonths = amortizationMonths,
            pricingModelVersion = pricingModelVersion
        )
    }
}

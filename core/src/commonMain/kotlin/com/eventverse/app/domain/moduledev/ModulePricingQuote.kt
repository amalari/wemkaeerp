package com.eventverse.app.domain.moduledev

import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Instant

/**
 * Everything that goes into a price, not just the price.
 *
 * These are snapshotted onto each quote rather than read from configuration at display time. A
 * monthly figure agreed today is billed for the length of the contract; if margin or rate lived
 * only in code, raising them next year would silently restate what an existing customer is
 * paying, and no one could reconstruct how the original number was reached.
 */
data class PricingInputs(
    /**
     * The hours the price is built on — the p90 of the estimate, not the median.
     *
     * The monthly figure is fixed once and we absorb any overrun, so pricing from the midpoint
     * loses money on half of all projects by construction.
     */
    val basisHours: WorkHours,
    val blendedHourlyRate: MoneyIdr,
    /**
     * How many tenants are expected to run the module and therefore share its build cost.
     *
     * 1 means the customer funds the build alone and it stays theirs. Higher means it enters the
     * core product. The same build can price several times apart on this number alone, which is
     * exactly why it is an input to be recorded rather than a decision made in someone's head.
     */
    val expectedTenantCount: Int = 1,
    val amortizationMonths: Int = 24,
    val marginPercent: Percentage = Percentage.ZERO,
    val monthlyMaintenancePercent: Percentage = Percentage.ZERO,
    /** Hosting, storage and bandwidth this module adds per tenant. Never divided by tenant count. */
    val monthlyInfraCost: MoneyIdr = MoneyIdr.ZERO,
    val discountPercent: Percentage = Percentage.ZERO,
    val pricingModelVersion: String = "v1"
) {
    init {
        require(expectedTenantCount > 0) { "expectedTenantCount must be positive" }
        require(amortizationMonths > 0) { "amortizationMonths must be positive" }
        require(discountPercent.value <= 100.0) { "discountPercent cannot exceed 100" }
        require(pricingModelVersion.isNotBlank()) { "pricingModelVersion cannot be blank" }
    }

    val buildCost: MoneyIdr get() = basisHours.costAt(blendedHourlyRate)
}

/**
 * The computed price, with the arithmetic that produced it.
 *
 * [breakdown] exists so a customer can be shown why the figure is what it is. A price nobody can
 * explain gets negotiated on feeling rather than on cost.
 */
data class PricingResult(
    val monthlyPrice: MoneyIdr,
    val oneTimeFee: MoneyIdr = MoneyIdr.ZERO,
    val breakdown: Map<String, Long> = emptyMap()
)

/**
 * A price offered for a module, at one point in time, under one set of assumptions.
 */
data class ModulePricingQuote(
    val id: QuoteId,
    val catalogEntryId: ModuleCatalogEntryId,
    val inputs: PricingInputs,
    val result: PricingResult,
    val buildRecordId: ModuleBuildId? = null,
    /** Set when the quote is for one specific factory; null for a catalogue-wide list price. */
    val tenantId: TenantId? = null,
    val status: QuoteStatus = QuoteStatus.DRAFT,
    val quotedAt: Instant? = null
) {
    val isBillable: Boolean get() = status.isBillable

    fun send(): ModulePricingQuote = copy(status = QuoteStatus.SENT)

    fun accept(): ModulePricingQuote {
        check(status != QuoteStatus.REJECTED) { "Quote ${id.value} was rejected and cannot be accepted" }
        check(status != QuoteStatus.SUPERSEDED) { "Quote ${id.value} has been superseded" }
        return copy(status = QuoteStatus.ACCEPTED)
    }

    fun reject(): ModulePricingQuote = copy(status = QuoteStatus.REJECTED)

    /**
     * Marks the quote as replaced by a newer one.
     *
     * Superseding rather than editing keeps the offer history intact: what was quoted, when, and
     * on what assumptions stays answerable after the fact.
     */
    fun supersede(): ModulePricingQuote = copy(status = QuoteStatus.SUPERSEDED)
}

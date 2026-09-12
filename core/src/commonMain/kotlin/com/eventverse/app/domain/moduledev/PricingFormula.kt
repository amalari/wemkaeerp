package com.eventverse.app.domain.moduledev

/**
 * How a set of [PricingInputs] becomes a price.
 *
 * A strategy rather than a function inside the use case, per Kontrak 4 of the module integration
 * rules: the formula will change — tenor, tiered margins, per-seat components — and every such
 * change would otherwise touch the use case and all of its tests. Keeping it separate also lets a
 * quote name the formula version that produced it, so historical prices remain reproducible.
 */
interface PricingFormula {
    val version: String

    fun priceOf(inputs: PricingInputs): PricingResult
}

/**
 * The default: recover the build cost over the contract, then add margin, upkeep and hosting.
 *
 * ```
 * shareOfBuild = buildCost / expectedTenantCount
 * monthly      = shareOfBuild / amortizationMonths * (1 + margin)
 *              + shareOfBuild * maintenancePercent
 *              + monthlyInfraCost
 * monthly      = monthly * (1 - discount)
 * ```
 *
 * Two divisions that are easy to get wrong, and cost real money when they are:
 *
 *  - **Maintenance divides by tenant count, infrastructure does not.** Keeping a module working is
 *    one effort shared by everyone running it; the storage and bandwidth it consumes is incurred
 *    per factory. Treating them alike either overcharges a shared module or quietly eats the
 *    hosting bill on a popular one.
 *  - **Margin applies to amortisation only.** Maintenance and infra are pass-through costs; mark
 *    them up and the price drifts from anything that can be justified to a customer.
 */
class AmortizedBuildCostFormula : PricingFormula {
    override val version: String = "v1"

    override fun priceOf(inputs: PricingInputs): PricingResult {
        val buildCost = inputs.buildCost
        val shareOfBuild = buildCost / inputs.expectedTenantCount

        val amortized = shareOfBuild / inputs.amortizationMonths
        val amortizedWithMargin = amortized * inputs.marginPercent.asMultiplier
        val maintenance = shareOfBuild * inputs.monthlyMaintenancePercent.asFraction
        val infra = inputs.monthlyInfraCost

        val beforeDiscount = amortizedWithMargin + maintenance + infra
        val monthly = if (inputs.discountPercent.value > 0.0) {
            beforeDiscount * (1.0 - inputs.discountPercent.asFraction)
        } else {
            beforeDiscount
        }

        return PricingResult(
            monthlyPrice = monthly,
            oneTimeFee = MoneyIdr.ZERO,
            breakdown = mapOf(
                "buildCost" to buildCost.amount,
                "shareOfBuild" to shareOfBuild.amount,
                "amortizedPerMonth" to amortized.amount,
                "amortizedWithMargin" to amortizedWithMargin.amount,
                "monthlyMaintenance" to maintenance.amount,
                "monthlyInfra" to infra.amount,
                "beforeDiscount" to beforeDiscount.amount,
                "monthlyPrice" to monthly.amount
            )
        )
    }
}

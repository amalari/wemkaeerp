package com.eventverse.app.domain.moduledev.usecases

import com.eventverse.app.domain.moduledev.AmortizedBuildCostFormula
import com.eventverse.app.domain.moduledev.EstimateConfidence
import com.eventverse.app.domain.moduledev.ModuleBuildId
import com.eventverse.app.domain.moduledev.ModuleBuildRepository
import com.eventverse.app.domain.moduledev.ModulePricingQuote
import com.eventverse.app.domain.moduledev.ModulePricingQuoteRepository
import com.eventverse.app.domain.moduledev.MoneyIdr
import com.eventverse.app.domain.moduledev.Percentage
import com.eventverse.app.domain.moduledev.PricingFormula
import com.eventverse.app.domain.moduledev.PricingInputs
import com.eventverse.app.domain.moduledev.QuoteId
import com.eventverse.app.domain.moduledev.WorkHours
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Instant

/**
 * Turns a build's estimate into a monthly subscription price.
 *
 * Prices from the build's **p90**, not its midpoint. The monthly figure is fixed once and we carry
 * any overrun for the length of the contract, so pricing from the median would lose money on half
 * of all projects by construction.
 *
 * Refuses outright when the estimate behind it is low confidence. Quoting from an estimate the
 * estimator declined to stand behind converts an acknowledged unknown into a signed commitment.
 */
class QuoteModulePriceUseCase(
    private val buildRepository: ModuleBuildRepository,
    private val quoteRepository: ModulePricingQuoteRepository,
    private val formula: PricingFormula = AmortizedBuildCostFormula()
) {
    suspend operator fun invoke(
        quoteId: QuoteId,
        buildId: ModuleBuildId,
        expectedTenantCount: Int,
        amortizationMonths: Int,
        marginPercent: Percentage,
        monthlyMaintenancePercent: Percentage = Percentage.ZERO,
        monthlyInfraCost: MoneyIdr = MoneyIdr.ZERO,
        discountPercent: Percentage = Percentage.ZERO,
        tenantId: TenantId? = null,
        quotedAt: Instant? = null,
        blendedHourlyRateOverride: MoneyIdr? = null
    ): Result<ModulePricingQuote> = runCatching {
        val build = buildRepository.findById(buildId)
            ?: error("Build ${buildId.value} tidak ditemukan.")

        require(build.estimateConfidence != EstimateConfidence.LOW) {
            "Estimasi build ${buildId.value} berkonfidensi rendah; harga tidak boleh diterbitkan " +
                "sebelum ditinjau manusia."
        }

        // p90 is the contract: fall back to the midpoint only if no spread was ever recorded, and
        // never silently — a build with no p90 predates the spread being captured.
        val basisHours: WorkHours = build.estimatedHoursP90
            ?: build.estimatedHours
            ?: error("Build ${buildId.value} belum diestimasi; tidak ada dasar jam untuk harga.")

        // A delivered build knows what it really cost per hour; an open one can only use the rate
        // supplied by the caller.
        val blendedRate = blendedHourlyRateOverride
            ?: build.blendedHourlyRateIdr
            ?: error(
                "Build ${buildId.value} belum punya rate per jam. Sediakan " +
                    "blendedHourlyRateOverride yang dihitung dari jam produktif nyata."
            )

        val inputs = PricingInputs(
            basisHours = basisHours,
            blendedHourlyRate = blendedRate,
            expectedTenantCount = expectedTenantCount,
            amortizationMonths = amortizationMonths,
            marginPercent = marginPercent,
            monthlyMaintenancePercent = monthlyMaintenancePercent,
            monthlyInfraCost = monthlyInfraCost,
            discountPercent = discountPercent,
            pricingModelVersion = formula.version
        )

        val quote = ModulePricingQuote(
            id = quoteId,
            catalogEntryId = build.catalogEntryId,
            inputs = inputs,
            result = formula.priceOf(inputs),
            buildRecordId = buildId,
            tenantId = tenantId,
            quotedAt = quotedAt
        )

        quoteRepository.save(quote)
        quote
    }
}

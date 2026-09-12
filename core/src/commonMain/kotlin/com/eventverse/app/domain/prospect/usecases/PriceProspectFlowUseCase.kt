package com.eventverse.app.domain.prospect.usecases

import com.eventverse.app.domain.moduledev.AmortizedBuildCostFormula
import com.eventverse.app.domain.moduledev.BuildEstimator
import com.eventverse.app.domain.moduledev.EmbeddingProvider
import com.eventverse.app.domain.moduledev.EstimationOutcome
import com.eventverse.app.domain.moduledev.ModuleBuildRepository
import com.eventverse.app.domain.moduledev.MoneyIdr
import com.eventverse.app.domain.moduledev.NeighborMatch
import com.eventverse.app.domain.moduledev.Percentage
import com.eventverse.app.domain.moduledev.PricingFormula
import com.eventverse.app.domain.moduledev.PricingInputs
import com.eventverse.app.domain.moduledev.SizingWeightsRepository
import com.eventverse.app.domain.moduledev.WorkHours
import com.eventverse.app.domain.prospect.CoverageAnalysis
import com.eventverse.app.domain.prospect.CoverageDecision
import com.eventverse.app.domain.prospect.ProspectPriceRange

/**
 * One gap, priced — or explicitly not priced.
 */
data class GapPricing(
    val gap: CoverageDecision.Gap,
    val lowMonthly: MoneyIdr?,
    val highMonthly: MoneyIdr?,
    val refusalReason: String?
) {
    val isPriced: Boolean get() = lowMonthly != null && highMonthly != null
}

/** The full picture behind a prospect's range, for the internal review screen. */
data class ProspectPricingResult(
    val range: ProspectPriceRange,
    val gapPricings: List<GapPricing>
)

/**
 * Rolls a coverage analysis up into one monthly range.
 *
 * ```
 * subscription = Σ base_monthly_price_idr of modules that already exist   (exact)
 * gapLow       = Σ price(p50 hours) of modules that must be built
 * gapHigh      = Σ price(p90 hours)
 * ```
 *
 * **Estimates are computed but never persisted.** It would be easy to route each gap through
 * `EstimateModuleBuildUseCase`, but that writes a build record — and a build record needs a catalogue
 * entry to point at. Every prospect who never replies would leave behind a `PLANNED` module and a
 * speculative build for work nobody agreed to. [BuildEstimator] is pure, so the arithmetic is
 * identical without the residue; records get written when a deal is real.
 */
class PriceProspectFlowUseCase(
    private val buildRepository: ModuleBuildRepository,
    private val sizingWeightsRepository: SizingWeightsRepository,
    private val embeddingProvider: EmbeddingProvider,
    private val estimator: BuildEstimator = BuildEstimator(),
    private val formula: PricingFormula = AmortizedBuildCostFormula(),
    /**
     * Must be derived from **real productive hours**, not a nominal 160-hour month — see
     * [com.eventverse.app.domain.moduledev.WorkHours]. Configured once at wiring so a money
     * decision is not re-guessed at each call site.
     */
    private val defaultBlendedHourlyRate: MoneyIdr
) {
    suspend operator fun invoke(
        coverage: CoverageAnalysis,
        marginPercent: Percentage,
        monthlyMaintenancePercent: Percentage = Percentage.ZERO,
        monthlyInfraCost: MoneyIdr = MoneyIdr.ZERO,
        expectedTenantCount: Int = DEFAULT_EXPECTED_TENANT_COUNT,
        amortizationMonths: Int = DEFAULT_AMORTIZATION_MONTHS,
        blendedHourlyRate: MoneyIdr = defaultBlendedHourlyRate
    ): Result<ProspectPricingResult> = runCatching {
        val subscription = MoneyIdr.sum(
            coverage.covered.mapNotNull { it.entry.baseMonthlyPriceIdr }
        )

        val weights = sizingWeightsRepository.findActive()

        val gapPricings = coverage.gaps.map { gap ->
            val probe = embeddingProvider.embed(gapNarrative(gap))
            val neighbors = buildRepository
                .findEstimationCandidates(gap.requirement.archetype.code)
                .mapNotNull { candidate ->
                    val embedding = candidate.embedding ?: return@mapNotNull null
                    val similarity = probe.cosineSimilarityTo(embedding) ?: return@mapNotNull null
                    NeighborMatch(candidate, similarity)
                }

            // Clarity is left unscored: the requirement came from prose we did not write and cannot
            // grade. The estimator treats an unscored brief as middling rather than as clear, which
            // is the conservative reading and the right one for a stranger's narrative.
            when (val outcome = estimator.estimate(gap.features, weights, null, neighbors)) {
                is EstimationOutcome.Estimated -> GapPricing(
                    gap = gap,
                    lowMonthly = priceOf(
                        outcome.p50Hours, blendedHourlyRate, expectedTenantCount,
                        amortizationMonths, marginPercent, monthlyMaintenancePercent, monthlyInfraCost
                    ),
                    highMonthly = priceOf(
                        outcome.p90Hours, blendedHourlyRate, expectedTenantCount,
                        amortizationMonths, marginPercent, monthlyMaintenancePercent, monthlyInfraCost
                    ),
                    refusalReason = null
                )
                is EstimationOutcome.InsufficientEvidence -> GapPricing(
                    gap = gap,
                    lowMonthly = null,
                    highMonthly = null,
                    refusalReason = outcome.reason
                )
            }
        }

        val unpriceable = gapPricings.count { !it.isPriced }

        // One unpriceable gap withholds the whole range. Summing the gaps we could price and calling
        // it the total is guaranteed to understate — we would be quoting part of the work as if it
        // were all of it.
        val range = if (unpriceable > 0) {
            ProspectPriceRange.withheld(
                subscriptionMonthly = subscription,
                unpriceableGapCount = unpriceable,
                expectedTenantCount = expectedTenantCount,
                amortizationMonths = amortizationMonths,
                pricingModelVersion = formula.version
            )
        } else {
            ProspectPriceRange(
                subscriptionMonthly = subscription,
                gapLowMonthly = MoneyIdr.sum(gapPricings.mapNotNull { it.lowMonthly }),
                gapHighMonthly = MoneyIdr.sum(gapPricings.mapNotNull { it.highMonthly }),
                unpriceableGapCount = 0,
                expectedTenantCount = expectedTenantCount,
                amortizationMonths = amortizationMonths,
                pricingModelVersion = formula.version
            )
        }

        ProspectPricingResult(range, gapPricings)
    }

    private fun priceOf(
        hours: WorkHours,
        rate: MoneyIdr,
        expectedTenantCount: Int,
        amortizationMonths: Int,
        margin: Percentage,
        maintenance: Percentage,
        infra: MoneyIdr
    ): MoneyIdr = formula.priceOf(
        PricingInputs(
            basisHours = hours,
            blendedHourlyRate = rate,
            expectedTenantCount = expectedTenantCount,
            amortizationMonths = amortizationMonths,
            marginPercent = margin,
            monthlyMaintenancePercent = maintenance,
            monthlyInfraCost = infra,
            pricingModelVersion = formula.version
        )
    ).monthlyPrice

    /** What gets embedded for retrieval: the requirement as stated, not our label for it. */
    private fun gapNarrative(gap: CoverageDecision.Gap): String =
        listOf(gap.requirement.title, gap.requirement.description, gap.requirement.sourceQuote)
            .filter { it.isNotBlank() }
            .joinToString(" ")

    companion object {
        /**
         * The prospect funds the build alone unless someone decides otherwise.
         *
         * The range must express uncertainty about **hours only**; folding in a guess about future
         * resale would make it uninterpretable. It also errs in the forgiving direction — a price
         * that falls after review is a good conversation, one that rises is not.
         */
        const val DEFAULT_EXPECTED_TENANT_COUNT = 1
        const val DEFAULT_AMORTIZATION_MONTHS = 24
    }
}

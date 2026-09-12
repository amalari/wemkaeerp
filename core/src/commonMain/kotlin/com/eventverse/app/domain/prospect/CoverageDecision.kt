package com.eventverse.app.domain.prospect

import com.eventverse.app.domain.moduledev.BuildFeatureVector
import com.eventverse.app.domain.moduledev.ModuleCatalogEntry

/**
 * Whether one [CapabilityRequirement] is already sellable or has to be built.
 *
 * A sealed hierarchy rather than a nullable module reference, for the same reason
 * `EstimationOutcome` is sealed: "this has to be built" carries cost and must be **handled**, not
 * quietly skipped by whoever forgets a null check. With `sealed`, the compiler insists.
 */
sealed interface CoverageDecision {

    val requirement: CapabilityRequirement

    /** A module that already exists and is billable. Contributes its list price, nothing more. */
    data class CoveredByCatalog(
        override val requirement: CapabilityRequirement,
        val entry: ModuleCatalogEntry
    ) : CoverageDecision

    /**
     * Nothing in the catalogue fills this slot; it has to be built and therefore estimated.
     *
     * [features] is the translator's breakdown of the work, and it feeds the ledger's estimator
     * unchanged — the same `BuildFeatureVector` used for every internal build, so a prospect gap is
     * measured on exactly the same scale as work we have already done.
     */
    data class Gap(
        override val requirement: CapabilityRequirement,
        val proposedModuleId: String,
        val features: BuildFeatureVector
    ) : CoverageDecision {
        init {
            require(proposedModuleId.isNotBlank()) { "proposedModuleId cannot be blank" }
        }
    }

    val kind: CoverageKind
        get() = when (this) {
            is CoveredByCatalog -> CoverageKind.COVERED
            is Gap -> CoverageKind.GAP
        }
}

/** The coverage decisions for one translated narrative, read together. */
data class CoverageAnalysis(
    val decisions: List<CoverageDecision>
) {
    val covered: List<CoverageDecision.CoveredByCatalog>
        get() = decisions.filterIsInstance<CoverageDecision.CoveredByCatalog>()

    val gaps: List<CoverageDecision.Gap>
        get() = decisions.filterIsInstance<CoverageDecision.Gap>()

    val hasGaps: Boolean get() = gaps.isNotEmpty()

    /**
     * A prospect whose every need is already covered is the best possible outcome: nothing to
     * build, so the price is exact rather than a range, and they can start immediately.
     */
    val isFullyCovered: Boolean get() = decisions.isNotEmpty() && gaps.isEmpty()
}

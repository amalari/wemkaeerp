package com.eventverse.app.domain.prospect.usecases

import com.eventverse.app.domain.moduledev.BuildFeatureVector
import com.eventverse.app.domain.moduledev.ModuleCatalogEntry
import com.eventverse.app.domain.moduledev.ModuleCatalogRepository
import com.eventverse.app.domain.pipeline.ModuleArchetype
import com.eventverse.app.domain.prospect.CapabilityRequirement
import com.eventverse.app.domain.prospect.CoverageAnalysis
import com.eventverse.app.domain.prospect.CoverageDecision
import com.eventverse.app.domain.prospect.FlowTranslation

/**
 * Decides, for each capability the prospect described, whether we can already sell it.
 *
 * Reads the **data-driven** catalogue (`module_catalog_entries`), not the `BusinessModule` enum.
 * `GetTenantModuleCatalogUseCase` consults only the nine built-ins, and mirroring that here would
 * over-report gaps: a custom module already built and paid for by another factory would be quoted
 * again as new work.
 */
class AnalyzeCoverageUseCase(
    private val catalogRepository: ModuleCatalogRepository
) {
    suspend operator fun invoke(
        translation: FlowTranslation
    ): Result<CoverageAnalysis> = runCatching {
        val billable = catalogRepository.findBillable()

        val decisions = translation.requirements.map { requirement ->
            val match = matchFor(requirement, billable)
            if (match != null) {
                CoverageDecision.CoveredByCatalog(requirement, match)
            } else {
                CoverageDecision.Gap(
                    requirement = requirement,
                    proposedModuleId = proposedModuleIdFor(requirement, translation),
                    features = requirement.features
                )
            }
        }

        CoverageAnalysis(decisions)
    }

    /**
     * Matching rule, and why it differs for the wildcard slot.
     *
     * For the eight real archetypes, sharing a slot is enough: a factory that needs cutting needs
     * *our* cutting module, whatever words it used to describe it.
     *
     * `CUSTOM_EXTENSION` is different. It is a deliberate catch-all, so "sablon manual" and "laundry
     * kimia" both land there while being entirely unrelated work. Matching by archetype alone would
     * declare any custom need covered by whichever custom plugin happened to be built first. So the
     * wildcard slot is only covered when the translator names a specific module that actually exists.
     */
    private fun matchFor(
        requirement: CapabilityRequirement,
        billable: List<ModuleCatalogEntry>
    ): ModuleCatalogEntry? = when (requirement.archetype) {
        ModuleArchetype.CUSTOM_EXTENSION -> null
        else -> billable.firstOrNull { it.archetypeCode == requirement.archetype.code }
    }

    private fun proposedModuleIdFor(
        requirement: CapabilityRequirement,
        translation: FlowTranslation
    ): String = translation.proposedPipeline.nodes
        .firstOrNull { it.customDisplayName == requirement.title }
        ?.moduleId
        ?: "proposed_${requirement.archetype.code}"
}

/**
 * Resolves the wildcard slot against a module the translator named explicitly.
 *
 * Kept separate from [AnalyzeCoverageUseCase]'s archetype rule because it answers a different
 * question — "does this exact module exist?" rather than "does anything fill this slot?" — and
 * because an id supplied by a model is exactly the kind of input that must be verified against the
 * catalogue rather than trusted.
 */
class ResolveSuggestedModuleUseCase(
    private val catalogRepository: ModuleCatalogRepository
) {
    suspend operator fun invoke(moduleId: String?): ModuleCatalogEntry? {
        if (moduleId.isNullOrBlank()) return null
        return catalogRepository.findByModuleId(moduleId)?.takeIf { it.isBillable }
    }
}

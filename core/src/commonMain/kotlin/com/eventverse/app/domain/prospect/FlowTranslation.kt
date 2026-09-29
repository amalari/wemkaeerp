package com.eventverse.app.domain.prospect

import com.eventverse.app.domain.blueprint.Blueprint

import com.eventverse.app.domain.pack.GarmentBlueprints

import com.eventverse.app.domain.moduledev.BuildFeatureVector
import com.eventverse.app.domain.pipeline.CustomTenantPipeline
import kotlinx.datetime.Instant

/**
 * One capability as the translator reported it, **before validation**.
 *
 * Every field is a plain `String` on purpose. This is untrusted output: a model will happily emit
 * an archetype code that does not exist, and parsing it into a typed enum at the boundary would
 * throw away the chance to record *what* it made up. Validation happens in
 * [com.eventverse.app.domain.prospect.usecases.TranslateProspectFlowUseCase] and produces warnings
 * rather than exceptions.
 */
data class RawCapabilityRequirement(
    val archetypeCode: String,
    val title: String,
    val description: String = "",
    val sourceQuote: String = "",
    /**
     * A catalogue module the translator believes already covers this.
     *
     * Only consulted for the `CUSTOM_EXTENSION` slot, where archetype matching alone is too loose —
     * "sablon manual" and "laundry kimia" both land in that slot but are entirely different work.
     */
    val suggestedModuleId: String? = null,
    val features: BuildFeatureVector = BuildFeatureVector.EMPTY
)

/**
 * Raw translator output, straight from the model.
 *
 * Nothing here is trusted, including [detectedPresetCode].
 */
data class FlowTranslationDraft(
    val translatorRef: String,
    val detectedPresetCode: String? = null,
    val requirements: List<RawCapabilityRequirement> = emptyList(),
    val openQuestions: List<String> = emptyList()
) {
    init {
        require(translatorRef.isNotBlank()) { "translatorRef cannot be blank" }
    }
}

/**
 * A validated translation of one narrative: what the factory needs, in our vocabulary.
 *
 * [proposedPipeline] is assembled in memory against a placeholder tenant and is **never** persisted
 * through `TenantPipelineRepository` — see [ProspectLead.placeholderTenantId].
 */
data class FlowTranslation(
    val id: FlowTranslationId,
    val leadId: ProspectLeadId,
    val translatorRef: String,
    val requirements: List<CapabilityRequirement>,
    val proposedPipeline: CustomTenantPipeline,
    /**
     * Null when no preset matched, and it must stay null.
     *
     * `GarmentBlueprints.fromCodeOrDefault()` falls back to `DEFAULT` instead of returning null, so
     * anything routed through it would silently file every unrecognised factory as a full-package
     * exporter — and a CMT workshop quoted as FOB is quoted for work it will never do.
     */
    val detectedPreset: Blueprint? = null,
    val openQuestions: List<String> = emptyList(),
    /**
     * What was wrong with the model's output: invented archetype codes, broken hand-offs, cycles,
     * requirements with no supporting quote.
     *
     * Kept rather than thrown. A translation that is mostly right is still worth a reviewer's time,
     * and discarding the warnings would make it look more trustworthy than it is.
     */
    val validationWarnings: List<String> = emptyList(),
    val translatedAt: Instant? = null
) {
    /**
     * Whether a human must look at this before anything is sent.
     *
     * Any warning is enough. The cost of an unnecessary review is a few minutes; the cost of a
     * quote built on a misread narrative runs for the length of a contract.
     */
    val needsHumanReview: Boolean
        get() = validationWarnings.isNotEmpty() ||
            detectedPreset == null ||
            requirements.any { it.lacksEvidence }

    val unclassifiedRequirements: List<CapabilityRequirement>
        get() = requirements.filter { it.isUnclassified }
}

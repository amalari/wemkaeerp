package com.eventverse.app.domain.prospect

import com.eventverse.app.domain.pack.GarmentSlots

import com.eventverse.app.domain.moduledev.BuildFeatureVector
import com.eventverse.app.domain.pipeline.ModuleArchetype

/**
 * One thing the prospect's factory needs, as understood from their narrative.
 *
 * Keyed by [ModuleArchetype] rather than by a module: the narrative says *what has to happen*
 * ("kami cuma jahit", "QC pakai AQL"), and which module fills that slot is a separate decision made
 * later by coverage analysis. Modelling it the other way round would make the translator guess at
 * our catalogue instead of describing the customer.
 */
data class CapabilityRequirement(
    val archetype: ModuleArchetype,
    val title: String,
    val description: String = "",
    /**
     * The fragment of the narrative that produced this requirement.
     *
     * Not decoration. It is the only way a reviewer can tell a genuine reading from an invented
     * one — a model that hallucinates a requirement cannot quote a sentence that supports it, and
     * an empty or mismatched quote is the cheapest hallucination signal available.
     */
    val sourceQuote: String = "",
    /**
     * The translator's breakdown of how much work this would be to build.
     *
     * Carried on every requirement, not only on the ones that turn out to be gaps: whether a slot
     * is already covered is decided later by coverage analysis, and by then the breakdown would
     * otherwise have to be re-associated by title — a fragile key, since two requirements can share
     * a wording and a title can be edited.
     *
     * Ignored for a covered requirement, where the module already exists and its price is known.
     */
    val features: BuildFeatureVector = BuildFeatureVector.EMPTY
) {
    init {
        require(title.isNotBlank()) { "CapabilityRequirement title cannot be blank" }
    }

    /** Whether the model could not map this to a known slot and fell back to the wildcard. */
    val isUnclassified: Boolean get() = archetype == GarmentSlots.CUSTOM_EXTENSION

    /** Weak requirements are the ones a reviewer should read first. */
    val lacksEvidence: Boolean get() = sourceQuote.isBlank()
}

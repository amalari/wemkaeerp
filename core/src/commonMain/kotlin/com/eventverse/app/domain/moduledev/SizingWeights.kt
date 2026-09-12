package com.eventverse.app.domain.moduledev

import kotlin.math.roundToInt

/**
 * Converts a [BuildFeatureVector] into [SizePoints].
 *
 * Weights are data, not constants, for two reasons. They begin as guesses and must eventually be
 * fitted against recorded hours; and once refitted, an old `size_points` value has to remain
 * interpretable under the weights that produced it. Recalibrating in place would silently
 * reinterpret every historical row, which is the one thing a ledger must not do.
 *
 * Hence [version]: a build record stores which version scored it.
 */
data class SizingWeights(
    val version: String,
    val weights: Map<String, Double>
) {
    init {
        require(version.isNotBlank()) { "SizingWeights version cannot be blank" }
        require(weights.isNotEmpty()) { "SizingWeights must define at least one weight" }
        require(weights.values.all { it >= 0.0 }) { "SizingWeights cannot contain negative weights" }
    }

    /**
     * An unknown feature key contributes nothing rather than throwing.
     *
     * A feature added to [BuildFeatureVector] before its weight row exists would otherwise break
     * scoring for every request at once. Scoring slightly low is recoverable; a dead estimation
     * path is not. [missingKeysFor] surfaces the gap so it does not go unnoticed.
     */
    fun scoreOf(features: BuildFeatureVector): SizePoints {
        val total = features.toWeightableCounts().entries.sumOf { (key, count) ->
            (weights[key] ?: 0.0) * count
        }
        return SizePoints(total.roundToInt())
    }

    /** Feature keys present in [features] that this version has no weight for. */
    fun missingKeysFor(features: BuildFeatureVector): Set<String> =
        features.toWeightableCounts().keys.filterNot { weights.containsKey(it) }.toSet()

    companion object {
        /**
         * Starting weights, mirroring the `module_sizing_weights` v1 seed.
         *
         * Present so domain tests and the in-memory repository do not depend on a database, and
         * so a fresh environment can score before the seed is loaded. The database rows remain
         * the source of truth in production.
         */
        val V1 = SizingWeights(
            version = "v1",
            weights = mapOf(
                BuildFeatureVector.KEY_ENTITY to 3.0,
                BuildFeatureVector.KEY_USE_CASE to 2.0,
                BuildFeatureVector.KEY_SCREEN to 5.0,
                BuildFeatureVector.KEY_API_ENDPOINT to 2.0,
                BuildFeatureVector.KEY_DB_TABLE to 3.0,
                BuildFeatureVector.KEY_REPORT to 6.0,
                BuildFeatureVector.KEY_INTEGRATION to 8.0,
                BuildFeatureVector.KEY_CUSTOM_FORMULA to 5.0,
                BuildFeatureVector.KEY_EXTERNAL_INTEGRATION to 6.0,
                BuildFeatureVector.KEY_REALTIME to 7.0,
                BuildFeatureVector.KEY_OFFLINE_SYNC to 9.0,
                BuildFeatureVector.KEY_FILE_UPLOAD to 6.0,
                BuildFeatureVector.KEY_NEW_DESIGN_COMPONENT to 4.0,
                BuildFeatureVector.KEY_PLATFORM_EXTRA to 2.0,
                BuildFeatureVector.KEY_AFFECTED_MODULE to 4.0
            )
        )
    }
}

package com.eventverse.app.domain.moduledev

import kotlin.math.floor

/**
 * A past build offered as evidence for a new one, with how close it is.
 */
data class NeighborMatch(
    val build: ModuleBuildRecord,
    val similarity: Double
) {
    init {
        require(similarity in -1.0..1.0) { "similarity must be a cosine value in [-1, 1]" }
    }

    /** Only builds with genuinely recorded hours can lend a productivity ratio. */
    val isUsableAsEvidence: Boolean get() = build.productivity != null
}

/**
 * What estimating produced.
 *
 * Modelled as a sealed hierarchy rather than a nullable estimate so that "we do not know" is a
 * state callers have to handle, not a null they can slip past.
 */
sealed interface EstimationOutcome {

    /**
     * An estimate backed by comparable work.
     *
     * Carries both [p50Hours] and [p90Hours]: the spread is what a price is built on, because the
     * monthly figure is fixed once and we absorb any overrun.
     */
    data class Estimated(
        val p50Hours: WorkHours,
        val p90Hours: WorkHours,
        val sizePoints: SizePoints,
        val medianProductivity: Double,
        val clarityMultiplier: Double,
        val confidence: EstimateConfidence,
        val neighbors: List<NeighborMatch>,
        val nearestSimilarity: Double
    ) : EstimationOutcome {
        val neighborIds: List<String> get() = neighbors.map { it.build.id.value }
    }

    /**
     * Not enough comparable evidence to put a number on it.
     *
     * Deliberately carries no hours at all. An estimate extrapolated from distant neighbours is
     * wrong *and* confident, and the monthly price derived from it is locked in for the length of
     * the contract — so the honest answer has to be structurally impossible to mistake for a
     * quiet one. A human estimates these, and the result still enters the ledger.
     */
    data class InsufficientEvidence(
        val reason: String,
        val nearestSimilarity: Double?,
        val usableNeighborCount: Int
    ) : EstimationOutcome
}

/**
 * Turns comparable past builds into an estimate for new work.
 *
 * The central idea: **what a neighbour lends is its rate, not its total.** A neighbour that took
 * 26 hours says nothing directly about work that is twice its size — but its 1.08 hours per size
 * point transfers. Averaging raw neighbour hours instead would let a small lookalike drag every
 * estimate down.
 *
 * Pure domain logic with no repository or transport dependency, so it is testable without a
 * database and reusable by whatever eventually retrieves the neighbours.
 */
class BuildEstimator(
    private val minimumSimilarity: Double = DEFAULT_MINIMUM_SIMILARITY,
    private val highConfidenceSimilarity: Double = DEFAULT_HIGH_CONFIDENCE_SIMILARITY,
    private val minimumNeighborsForSpread: Int = DEFAULT_MINIMUM_NEIGHBORS_FOR_SPREAD
) {

    fun estimate(
        features: BuildFeatureVector,
        weights: SizingWeights,
        clarityScore: Int?,
        neighbors: List<NeighborMatch>
    ): EstimationOutcome {
        val sizePoints = weights.scoreOf(features)
        if (sizePoints.isZero) {
            return EstimationOutcome.InsufficientEvidence(
                reason = "Feature vector scores zero size points; the requirement has not been " +
                    "broken down into anything countable yet.",
                nearestSimilarity = neighbors.maxOfOrNull { it.similarity },
                usableNeighborCount = 0
            )
        }

        val usable = neighbors
            .filter { it.isUsableAsEvidence }
            .sortedByDescending { it.similarity }

        val nearest = usable.firstOrNull()
            ?: return EstimationOutcome.InsufficientEvidence(
                reason = "No past build with recorded hours is comparable to this request.",
                nearestSimilarity = neighbors.maxOfOrNull { it.similarity },
                usableNeighborCount = 0
            )

        if (nearest.similarity < minimumSimilarity) {
            return EstimationOutcome.InsufficientEvidence(
                reason = "Closest comparable build is only ${format(nearest.similarity)} similar, " +
                    "below the ${format(minimumSimilarity)} needed to price from evidence.",
                nearestSimilarity = nearest.similarity,
                usableNeighborCount = usable.size
            )
        }

        val productivities = usable.mapNotNull { it.build.productivity }.sorted()
        val median = percentile(productivities, 0.50)
        val p90 = percentile(productivities, 0.90)
        val clarityMultiplier = clarityMultiplierFor(clarityScore)

        val p50Hours = WorkHours(sizePoints.value * median * clarityMultiplier)
        val p90Hours = WorkHours(sizePoints.value * p90 * clarityMultiplier)

        return EstimationOutcome.Estimated(
            p50Hours = p50Hours,
            // With too few neighbours the spread is not measurable, so p90 collapses onto p50.
            // Widen it deliberately rather than pretending a single data point bounds the risk.
            p90Hours = if (usable.size < minimumNeighborsForSpread) {
                WorkHours(maxOf(p90Hours.hours, p50Hours.hours * THIN_EVIDENCE_SPREAD))
            } else {
                p90Hours
            },
            sizePoints = sizePoints,
            medianProductivity = median,
            clarityMultiplier = clarityMultiplier,
            confidence = confidenceFor(nearest.similarity, usable.size),
            neighbors = usable,
            nearestSimilarity = nearest.similarity
        )
    }

    /**
     * Unclear briefs overrun; they do not merely take longer in proportion to their size.
     *
     * An unstated clarity is treated as middling rather than optimistic — assuming the best about
     * a brief nobody scored is how estimates quietly drift low.
     */
    private fun clarityMultiplierFor(clarityScore: Int?): Double = when (clarityScore) {
        5 -> 1.00
        4 -> 1.15
        3 -> 1.30
        2 -> 1.45
        1 -> 1.60
        else -> 1.30
    }

    private fun confidenceFor(nearestSimilarity: Double, usableCount: Int): EstimateConfidence =
        when {
            nearestSimilarity >= highConfidenceSimilarity &&
                usableCount >= minimumNeighborsForSpread -> EstimateConfidence.HIGH
            else -> EstimateConfidence.MEDIUM
        }

    /**
     * Linear-interpolated percentile over a sorted list.
     *
     * Interpolating rather than picking the nearest rank matters at the sizes this runs at: with
     * three neighbours, a nearest-rank p90 is just the maximum, which makes the safety margin a
     * hostage to whichever single build went worst.
     */
    private fun percentile(sorted: List<Double>, fraction: Double): Double {
        require(sorted.isNotEmpty()) { "Cannot take a percentile of an empty list" }
        if (sorted.size == 1) return sorted.first()

        val position = fraction * (sorted.size - 1)
        val lowerIndex = floor(position).toInt()
        val upperIndex = minOf(lowerIndex + 1, sorted.size - 1)
        val weight = position - lowerIndex
        return sorted[lowerIndex] * (1 - weight) + sorted[upperIndex] * weight
    }

    private fun format(value: Double): String {
        val rounded = (value * 100).toInt()
        return "0.${rounded.toString().padStart(2, '0')}"
    }

    companion object {
        /**
         * Below this, the use case refuses to price. Chosen to sit above "shares vocabulary" and
         * below "obviously the same kind of work"; tune it once real retrieval data exists.
         */
        const val DEFAULT_MINIMUM_SIMILARITY = 0.65
        const val DEFAULT_HIGH_CONFIDENCE_SIMILARITY = 0.85
        const val DEFAULT_MINIMUM_NEIGHBORS_FOR_SPREAD = 3

        /** Safety margin applied when there are too few neighbours to measure a real spread. */
        const val THIN_EVIDENCE_SPREAD = 1.40
    }
}

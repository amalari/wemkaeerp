package com.eventverse.app.domain.moduledev.usecases

import com.eventverse.app.domain.moduledev.BuildEstimator
import com.eventverse.app.domain.moduledev.BuildFeatureVector
import com.eventverse.app.domain.moduledev.EmbeddingProvider
import com.eventverse.app.domain.moduledev.EstimationOutcome
import com.eventverse.app.domain.moduledev.EstimatorKind
import com.eventverse.app.domain.moduledev.ModuleBuildRecord
import com.eventverse.app.domain.moduledev.ModuleBuildRepository
import com.eventverse.app.domain.moduledev.NeighborMatch
import com.eventverse.app.domain.moduledev.SizingWeightsRepository
import kotlinx.datetime.Instant

/**
 * The estimate, together with the build record carrying it.
 *
 * Both are returned because a refusal still produces a record worth keeping: it holds the
 * requirement text, the feature breakdown and the embedding, so the human estimate that follows
 * lands on the same row and the next request can retrieve it as a neighbour.
 */
data class ModuleBuildEstimation(
    val record: ModuleBuildRecord,
    val outcome: EstimationOutcome
) {
    val isPriceable: Boolean
        get() = outcome is EstimationOutcome.Estimated && outcome.confidence.permitsPricing
}

/**
 * Estimates how long a piece of module work will take, from what comparable work actually took.
 *
 * The sequence: embed the requirement, retrieve past builds in the same archetype, score the
 * feature vector into size points, borrow the neighbours' hours-per-point, and widen for an
 * unclear brief.
 *
 * **This use case may return a refusal, and callers must handle it.** When the nearest comparable
 * build is too distant, no hours come back at all. An estimate extrapolated from remote
 * neighbours is wrong and confident at once, and the monthly price derived from it is locked in
 * for the length of the contract — so the refusal is a value in the result rather than a low
 * number with a warning attached that someone may not read.
 */
class EstimateModuleBuildUseCase(
    private val buildRepository: ModuleBuildRepository,
    private val sizingWeightsRepository: SizingWeightsRepository,
    private val embeddingProvider: EmbeddingProvider,
    private val estimator: BuildEstimator = BuildEstimator(),
    private val candidateLimit: Int = 50
) {
    suspend operator fun invoke(
        draft: ModuleBuildRecord,
        features: BuildFeatureVector,
        clarityScore: Int?,
        estimatedAt: Instant,
        estimatedBy: EstimatorKind = EstimatorKind.AI,
        estimatorRef: String = "${DEFAULT_ESTIMATOR_PREFIX}/${DEFAULT_PRICING_VERSION}"
    ): Result<ModuleBuildEstimation> = runCatching {
        check(!draft.isEstimated) {
            "Build ${draft.id.value} is already estimated; estimates are frozen once written"
        }

        val weights = sizingWeightsRepository.findActive()
        val embedding = embeddingProvider.embed(draft.requirementText)

        // The feature vector is attached and scored here, at estimation time, and never touched
        // again. What we believed the shape of the work to be is the predictor; correcting it
        // later with hindsight would leave nothing to learn from.
        val scored = draft
            .copy(features = features, embedding = embedding, clarityScore = clarityScore)
            .withSizing(weights)

        val neighbors = buildRepository
            .findEstimationCandidates(draft.archetypeCode, candidateLimit)
            .filter { it.id != draft.id }
            .mapNotNull { candidate ->
                val candidateEmbedding = candidate.embedding ?: return@mapNotNull null
                val similarity = embedding.cosineSimilarityTo(candidateEmbedding)
                    ?: return@mapNotNull null
                NeighborMatch(candidate, similarity)
            }

        val outcome = estimator.estimate(features, weights, clarityScore, neighbors)

        val record = when (outcome) {
            is EstimationOutcome.Estimated -> scored.withEstimate(
                estimatedHours = outcome.p50Hours,
                estimatedHoursP90 = outcome.p90Hours,
                estimatedBy = estimatedBy,
                estimatorRef = estimatorRef,
                confidence = outcome.confidence,
                estimatedAt = estimatedAt,
                retrievedNeighborIds = outcome.neighborIds,
                nearestNeighborSimilarity = outcome.nearestSimilarity
            )
            // No hours are written on a refusal. The row still keeps the requirement, features and
            // embedding, so a human estimate can complete it in place.
            is EstimationOutcome.InsufficientEvidence -> scored
        }

        buildRepository.save(record)
        ModuleBuildEstimation(record, outcome)
    }

    companion object {
        const val DEFAULT_ESTIMATOR_PREFIX = "ai"
        const val DEFAULT_PRICING_VERSION = "pricing-v1"
    }
}

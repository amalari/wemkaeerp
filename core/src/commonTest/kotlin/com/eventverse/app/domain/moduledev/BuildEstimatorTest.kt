package com.eventverse.app.domain.moduledev

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Works through the QC photo / berita-acara scenario from the plan, plus the cases where the
 * estimator must refuse to produce a number at all.
 */
class BuildEstimatorTest {

    private val estimator = BuildEstimator()

    private val qcFeatures = BuildFeatureVector(
        entityCount = 2,
        useCaseCount = 4,
        screenCount = 2,
        apiEndpointCount = 5,
        dbTableCount = 2,
        reportCount = 1,
        targetPlatformCount = 3,
        affectedExistingModuleCount = 1,
        requiresFileUpload = true
    )

    private fun neighbor(
        id: String,
        sizePoints: Int,
        actualHours: Double,
        similarity: Double,
        effortSource: EffortSource = EffortSource.LOGGED
    ) = NeighborMatch(
        build = ModuleBuildRecord(
            id = ModuleBuildId(id),
            catalogEntryId = ModuleCatalogEntryId("mce-quality-control"),
            buildType = BuildType.CUSTOMIZATION,
            archetypeCode = "quality_control",
            requirementText = "past work $id",
            sizePoints = SizePoints(sizePoints),
            actualHours = WorkHours(actualHours),
            effortSource = effortSource
        ),
        similarity = similarity
    )

    /** B-019, B-007, B-012 from the plan: productivities 1.26, 1.42, 1.08. */
    private val planNeighbors = listOf(
        neighbor("B-019", sizePoints = 46, actualHours = 58.0, similarity = 0.88),
        neighbor("B-007", sizePoints = 31, actualHours = 44.0, similarity = 0.81),
        neighbor("B-012", sizePoints = 24, actualHours = 26.0, similarity = 0.76)
    )

    @Test
    fun qc_request_should_estimate_from_neighbour_productivity_not_neighbour_hours() {
        val outcome = estimator.estimate(qcFeatures, SizingWeights.V1, clarityScore = 4, planNeighbors)

        val estimated = assertIs<EstimationOutcome.Estimated>(outcome)
        assertEquals(SizePoints(60), estimated.sizePoints)
        // Median of 1.08, 1.26, 1.42 is 1.26 — the middle neighbour's rate, not its 58 hours.
        assertEquals(1.26, estimated.medianProductivity, 0.01)
        assertEquals(1.15, estimated.clarityMultiplier, 1e-9)
        // 60 points x 1.26 x 1.15 = 87 hours, as worked through in the plan.
        assertEquals(87.0, estimated.p50Hours.hours, 0.5)
        // p90 interpolates between the two slowest neighbours (1.26 and 1.42) rather than taking
        // the slowest outright, which would make the whole safety margin hostage to whichever
        // single build went worst. Interpolated: 1.388 x 60 x 1.15 = 95.7 hours.
        assertEquals(95.7, estimated.p90Hours.hours, 0.5)
    }

    @Test
    fun p90_should_never_sit_below_p50() {
        val outcome = estimator.estimate(qcFeatures, SizingWeights.V1, 4, planNeighbors)
        val estimated = assertIs<EstimationOutcome.Estimated>(outcome)
        assertTrue(estimated.p90Hours.hours >= estimated.p50Hours.hours)
    }

    @Test
    fun a_small_lookalike_should_not_drag_the_estimate_down() {
        // B-012 took only 26 hours, but it was a quarter of the size. Borrowing raw hours would
        // pull the estimate toward 26; borrowing its rate does not.
        val outcome = estimator.estimate(qcFeatures, SizingWeights.V1, 4, planNeighbors)
        val estimated = assertIs<EstimationOutcome.Estimated>(outcome)
        assertTrue(
            estimated.p50Hours.hours > 60.0,
            "estimate collapsed toward the smallest neighbour: ${estimated.p50Hours.hours}"
        )
    }

    @Test
    fun unclear_requirement_should_cost_more_than_a_clear_one() {
        val clear = estimator.estimate(qcFeatures, SizingWeights.V1, clarityScore = 5, planNeighbors)
        val vague = estimator.estimate(qcFeatures, SizingWeights.V1, clarityScore = 1, planNeighbors)

        val clearHours = assertIs<EstimationOutcome.Estimated>(clear).p50Hours.hours
        val vagueHours = assertIs<EstimationOutcome.Estimated>(vague).p50Hours.hours
        assertTrue(vagueHours > clearHours * 1.5, "vague=$vagueHours clear=$clearHours")
    }

    @Test
    fun unscored_clarity_should_be_treated_as_middling_not_optimistic() {
        val unscored = estimator.estimate(qcFeatures, SizingWeights.V1, clarityScore = null, planNeighbors)
        val perfect = estimator.estimate(qcFeatures, SizingWeights.V1, clarityScore = 5, planNeighbors)

        val unscoredHours = assertIs<EstimationOutcome.Estimated>(unscored).p50Hours.hours
        val perfectHours = assertIs<EstimationOutcome.Estimated>(perfect).p50Hours.hours
        assertTrue(unscoredHours > perfectHours)
    }

    // ---- The refusal gate ----------------------------------------------------

    @Test
    fun distant_neighbours_should_refuse_to_produce_an_estimate() {
        val distant = listOf(
            neighbor("B-100", sizePoints = 40, actualHours = 50.0, similarity = 0.52)
        )
        val outcome = estimator.estimate(qcFeatures, SizingWeights.V1, 4, distant)

        val refusal = assertIs<EstimationOutcome.InsufficientEvidence>(outcome)
        assertTrue(refusal.reason.contains("similar"), refusal.reason)
        assertEquals(0.52, refusal.nearestSimilarity)
    }

    @Test
    fun reconstructed_hours_should_not_count_as_evidence() {
        // Hours recalled from memory are a function of how big the work looked, and size_points is
        // also derived from how big it looks. Letting them through would make productivity constant
        // by construction and teach the model its own assumption.
        val recalled = listOf(
            neighbor("B-050", 46, 58.0, similarity = 0.95, effortSource = EffortSource.RECONSTRUCTED)
        )
        val outcome = estimator.estimate(qcFeatures, SizingWeights.V1, 4, recalled)

        val refusal = assertIs<EstimationOutcome.InsufficientEvidence>(outcome)
        assertEquals(0, refusal.usableNeighborCount)
    }

    @Test
    fun imported_rows_without_hours_should_not_count_as_evidence() {
        val imported = NeighborMatch(
            build = ModuleBuildRecord(
                id = ModuleBuildId("B-IMP"),
                catalogEntryId = ModuleCatalogEntryId("mce-quality-control"),
                buildType = BuildType.NEW_MODULE,
                archetypeCode = "quality_control",
                requirementText = "backfilled from a teaching doc",
                sizePoints = SizePoints(40),
                actualHours = null,
                effortSource = EffortSource.IMPORTED
            ),
            similarity = 0.93
        )
        val outcome = estimator.estimate(qcFeatures, SizingWeights.V1, 4, listOf(imported))
        assertIs<EstimationOutcome.InsufficientEvidence>(outcome)
    }

    @Test
    fun empty_feature_vector_should_refuse_rather_than_estimate_zero_hours() {
        val outcome = estimator.estimate(
            BuildFeatureVector.EMPTY, SizingWeights.V1, 4, planNeighbors
        )
        val refusal = assertIs<EstimationOutcome.InsufficientEvidence>(outcome)
        assertTrue(refusal.reason.contains("size points"), refusal.reason)
    }

    // ---- Confidence ----------------------------------------------------------

    @Test
    fun close_and_plentiful_neighbours_should_give_high_confidence() {
        val outcome = estimator.estimate(qcFeatures, SizingWeights.V1, 4, listOf(
            neighbor("B-019", 46, 58.0, similarity = 0.94),
            neighbor("B-007", 31, 44.0, similarity = 0.88),
            neighbor("B-012", 24, 26.0, similarity = 0.86)
        ))
        assertEquals(
            EstimateConfidence.HIGH,
            assertIs<EstimationOutcome.Estimated>(outcome).confidence
        )
    }

    @Test
    fun a_single_neighbour_should_cap_confidence_and_widen_the_spread() {
        val lone = listOf(neighbor("B-019", 46, 58.0, similarity = 0.97))
        val outcome = estimator.estimate(qcFeatures, SizingWeights.V1, 4, lone)

        val estimated = assertIs<EstimationOutcome.Estimated>(outcome)
        // One data point cannot bound risk, however similar it looks.
        assertEquals(EstimateConfidence.MEDIUM, estimated.confidence)
        assertTrue(
            estimated.p90Hours.hours >= estimated.p50Hours.hours * 1.39,
            "p90 ${estimated.p90Hours.hours} did not widen over p50 ${estimated.p50Hours.hours}"
        )
    }

    @Test
    fun similarity_outside_cosine_range_should_fail() {
        assertFailsWith<IllegalArgumentException> {
            NeighborMatch(planNeighbors.first().build, similarity = 1.5)
        }
    }
}

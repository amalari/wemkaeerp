package com.eventverse.app.domain.moduledev

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ModuleDevValueObjectsTest {

    @Test
    fun money_negative_amount_should_fail() {
        assertFailsWith<IllegalArgumentException> { MoneyIdr(-1) }
    }

    @Test
    fun money_multiply_should_round_to_whole_rupiah() {
        // 13_524_000 * 1.35 = 18_257_400 exactly; the point is that no fraction survives.
        assertEquals(MoneyIdr(18_257_400), MoneyIdr(13_524_000) * 1.35)
    }

    @Test
    fun money_divide_by_zero_should_fail() {
        assertFailsWith<IllegalArgumentException> { MoneyIdr(1000) / 0 }
    }

    @Test
    fun money_sum_should_total_all_values() {
        val total = MoneyIdr.sum(listOf(MoneyIdr(250_000), MoneyIdr(400_000), MoneyIdr(350_000)))
        assertEquals(MoneyIdr(1_000_000), total)
    }

    @Test
    fun work_hours_negative_should_fail() {
        assertFailsWith<IllegalArgumentException> { WorkHours(-0.5) }
    }

    @Test
    fun work_hours_cost_should_multiply_by_rate() {
        // 98 hours at Rp 138.000 — the worked example from the plan.
        assertEquals(MoneyIdr(13_524_000), WorkHours(98.0).costAt(MoneyIdr(138_000)))
    }

    @Test
    fun percentage_should_expose_fraction_and_multiplier() {
        val margin = Percentage(35.0)
        assertEquals(0.35, margin.asFraction, 1e-9)
        assertEquals(1.35, margin.asMultiplier, 1e-9)
    }

    @Test
    fun estimate_confidence_low_should_not_permit_pricing() {
        assertTrue(!EstimateConfidence.LOW.permitsPricing)
        assertTrue(EstimateConfidence.MEDIUM.permitsPricing)
        assertTrue(EstimateConfidence.HIGH.permitsPricing)
    }

    @Test
    fun only_logged_effort_should_be_training_grade() {
        assertTrue(EffortSource.LOGGED.isTrainingGrade)
        assertTrue(!EffortSource.RECONSTRUCTED.isTrainingGrade)
        assertTrue(!EffortSource.IMPORTED.isTrainingGrade)
    }
}

class BuildFeatureVectorTest {

    @Test
    fun target_platform_count_below_one_should_fail() {
        assertFailsWith<IllegalArgumentException> { BuildFeatureVector(targetPlatformCount = 0) }
    }

    @Test
    fun zero_counts_should_be_absent_from_weightable_counts() {
        val counts = BuildFeatureVector(entityCount = 2).toWeightableCounts()
        assertEquals(2, counts[BuildFeatureVector.KEY_ENTITY])
        assertTrue(!counts.containsKey(BuildFeatureVector.KEY_SCREEN))
    }

    @Test
    fun flags_should_contribute_once_when_set() {
        val counts = BuildFeatureVector(requiresFileUpload = true).toWeightableCounts()
        assertEquals(1, counts[BuildFeatureVector.KEY_FILE_UPLOAD])
    }

    @Test
    fun extra_platform_count_should_exclude_the_first_target() {
        assertEquals(2, BuildFeatureVector(targetPlatformCount = 3).extraPlatformCount)
    }
}

class SizingWeightsTest {

    /**
     * The QC photo/PDF request worked through in the plan. If this number moves, every historical
     * estimate silently changes meaning — which is exactly why weights are versioned.
     */
    @Test
    fun qc_photo_request_should_score_sixty_points_under_v1() {
        val features = BuildFeatureVector(
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
        assertEquals(SizePoints(60), SizingWeights.V1.scoreOf(features))
    }

    @Test
    fun unknown_feature_key_should_score_zero_rather_than_throw() {
        val partial = SizingWeights("test", mapOf(BuildFeatureVector.KEY_ENTITY to 3.0))
        val features = BuildFeatureVector(entityCount = 1, screenCount = 4)
        assertEquals(SizePoints(3), partial.scoreOf(features))
    }

    @Test
    fun missing_keys_should_be_reported_so_gaps_do_not_stay_silent() {
        val partial = SizingWeights("test", mapOf(BuildFeatureVector.KEY_ENTITY to 3.0))
        val missing = partial.missingKeysFor(BuildFeatureVector(entityCount = 1, screenCount = 4))
        assertEquals(setOf(BuildFeatureVector.KEY_SCREEN), missing)
    }

    @Test
    fun negative_weight_should_fail() {
        assertFailsWith<IllegalArgumentException> {
            SizingWeights("bad", mapOf(BuildFeatureVector.KEY_ENTITY to -1.0))
        }
    }
}

class EmbeddingVectorTest {

    @Test
    fun identical_vectors_should_have_similarity_one() {
        val vector = EmbeddingVector(listOf(0.1, 0.2, 0.3), model = "test-embed")
        assertEquals(1.0, vector.cosineSimilarityTo(vector)!!, 1e-9)
    }

    @Test
    fun orthogonal_vectors_should_have_similarity_zero() {
        val a = EmbeddingVector(listOf(1.0, 0.0), model = "test-embed")
        val b = EmbeddingVector(listOf(0.0, 1.0), model = "test-embed")
        assertEquals(0.0, a.cosineSimilarityTo(b)!!, 1e-9)
    }

    @Test
    fun vectors_from_different_models_should_not_be_comparable() {
        val a = EmbeddingVector(listOf(1.0, 0.0), model = "embed-v1")
        val b = EmbeddingVector(listOf(1.0, 0.0), model = "embed-v2")
        assertTrue(!a.isComparableTo(b))
        assertEquals(null, a.cosineSimilarityTo(b))
    }

    @Test
    fun zero_vector_should_yield_no_similarity_rather_than_zero() {
        val a = EmbeddingVector(listOf(0.0, 0.0), model = "test-embed")
        val b = EmbeddingVector(listOf(1.0, 1.0), model = "test-embed")
        assertEquals(null, a.cosineSimilarityTo(b))
    }

    @Test
    fun empty_vector_should_fail() {
        assertFailsWith<IllegalArgumentException> {
            EmbeddingVector(emptyList(), model = "test-embed")
        }
    }
}

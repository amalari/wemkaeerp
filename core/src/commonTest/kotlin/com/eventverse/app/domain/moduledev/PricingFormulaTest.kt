package com.eventverse.app.domain.moduledev

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The pricing arithmetic from the plan, checked against figures worked out by hand.
 */
class PricingFormulaTest {

    private val formula = AmortizedBuildCostFormula()

    /** 98 hours (p90) at Rp 138.000, 24 months, 35% margin, 1.5% upkeep, Rp 120.000 infra. */
    private fun qcInputs(expectedTenantCount: Int) = PricingInputs(
        basisHours = WorkHours(98.0),
        blendedHourlyRate = MoneyIdr(138_000),
        expectedTenantCount = expectedTenantCount,
        amortizationMonths = 24,
        marginPercent = Percentage(35.0),
        monthlyMaintenancePercent = Percentage(1.5),
        monthlyInfraCost = MoneyIdr(120_000)
    )

    @Test
    fun exclusive_customization_should_price_at_about_one_million_per_month() {
        val result = formula.priceOf(qcInputs(expectedTenantCount = 1))

        // 13_524_000 / 24 = 563_500 -> x1.35 = 760_725
        // upkeep 13_524_000 x 1.5% = 202_860; infra 120_000
        assertEquals(13_524_000L, result.breakdown["buildCost"])
        assertEquals(760_725L, result.breakdown["amortizedWithMargin"])
        assertEquals(202_860L, result.breakdown["monthlyMaintenance"])
        assertEquals(MoneyIdr(1_083_585), result.monthlyPrice)
    }

    @Test
    fun spreading_across_tenants_should_cut_the_price_roughly_threefold() {
        val exclusive = formula.priceOf(qcInputs(expectedTenantCount = 1)).monthlyPrice
        val shared = formula.priceOf(qcInputs(expectedTenantCount = 4)).monthlyPrice

        assertTrue(
            shared.amount < exclusive.amount / 2,
            "shared=${shared.amount} exclusive=${exclusive.amount}"
        )
        // Infra is per tenant and must NOT be divided; it survives at full value in the shared case.
        assertEquals(120_000L, formula.priceOf(qcInputs(4)).breakdown["monthlyInfra"])
    }

    @Test
    fun infrastructure_should_not_be_divided_by_expected_tenant_count() {
        val one = formula.priceOf(qcInputs(1)).breakdown["monthlyInfra"]
        val many = formula.priceOf(qcInputs(10)).breakdown["monthlyInfra"]
        assertEquals(one, many)
    }

    @Test
    fun maintenance_should_be_divided_by_expected_tenant_count() {
        // Keeping a module working is one shared effort; charging every tenant the full upkeep of
        // a popular module would overcharge precisely the modules that succeeded.
        val one = formula.priceOf(qcInputs(1)).breakdown["monthlyMaintenance"]!!
        val four = formula.priceOf(qcInputs(4)).breakdown["monthlyMaintenance"]!!
        assertEquals(one / 4, four)
    }

    @Test
    fun margin_should_apply_only_to_the_amortized_portion() {
        val withMargin = formula.priceOf(qcInputs(1).copy(marginPercent = Percentage(35.0)))
        val withoutMargin = formula.priceOf(qcInputs(1).copy(marginPercent = Percentage.ZERO))

        // Pass-through costs are identical either way; only amortisation moved.
        assertEquals(
            withMargin.breakdown["monthlyMaintenance"],
            withoutMargin.breakdown["monthlyMaintenance"]
        )
        assertEquals(withMargin.breakdown["monthlyInfra"], withoutMargin.breakdown["monthlyInfra"])
        assertTrue(withMargin.monthlyPrice.amount > withoutMargin.monthlyPrice.amount)
    }

    @Test
    fun longer_tenor_should_lower_the_monthly_price() {
        val twoYears = formula.priceOf(qcInputs(1).copy(amortizationMonths = 24)).monthlyPrice
        val threeYears = formula.priceOf(qcInputs(1).copy(amortizationMonths = 36)).monthlyPrice
        assertTrue(threeYears.amount < twoYears.amount)
    }

    @Test
    fun discount_should_reduce_the_final_price() {
        val full = formula.priceOf(qcInputs(1)).monthlyPrice
        val discounted = formula.priceOf(qcInputs(1).copy(discountPercent = Percentage(10.0)))
        // Money rounds to the nearest rupiah rather than truncating, so 975_226.5 lands on 975_227.
        assertEquals(MoneyIdr(975_227), discounted.monthlyPrice)
        assertTrue(discounted.monthlyPrice.amount < full.amount)
    }

    @Test
    fun zero_amortization_months_should_fail_rather_than_divide_by_zero() {
        assertFailsWith<IllegalArgumentException> { qcInputs(1).copy(amortizationMonths = 0) }
    }

    @Test
    fun zero_expected_tenant_count_should_fail() {
        assertFailsWith<IllegalArgumentException> { qcInputs(1).copy(expectedTenantCount = 0) }
    }

    @Test
    fun formula_version_should_be_recorded_so_old_prices_stay_reproducible() {
        assertEquals("v1", formula.version)
    }
}

class ModuleBuildRecordTest {

    private fun baseRecord() = ModuleBuildRecord(
        id = ModuleBuildId("b-001"),
        catalogEntryId = ModuleCatalogEntryId("mce-quality-control"),
        buildType = BuildType.CUSTOMIZATION,
        archetypeCode = "quality_control",
        requirementText = "foto cacat + berita acara PDF",
        sizePoints = SizePoints(60)
    )

    @Test
    fun variance_should_match_the_worked_example() {
        val record = baseRecord().copy(
            estimatedHours = WorkHours(87.0),
            actualHours = WorkHours(104.0)
        )
        assertEquals(19.54, record.estimateVariancePercent!!, 0.01)
    }

    @Test
    fun variance_should_be_unknown_until_the_build_closes() {
        val record = baseRecord().copy(estimatedHours = WorkHours(87.0))
        assertEquals(null, record.estimateVariancePercent)
    }

    @Test
    fun productivity_should_be_hours_per_size_point() {
        val record = baseRecord().copy(actualHours = WorkHours(104.0))
        assertEquals(104.0 / 60.0, record.productivity!!, 1e-9)
    }

    @Test
    fun productivity_should_be_withheld_for_non_logged_hours() {
        val recalled = baseRecord().copy(
            actualHours = WorkHours(104.0),
            effortSource = EffortSource.RECONSTRUCTED
        )
        assertEquals(null, recalled.productivity)
    }

    @Test
    fun blank_requirement_text_should_fail() {
        assertFailsWith<IllegalArgumentException> { baseRecord().copy(requirementText = "  ") }
    }

    @Test
    fun clarity_score_outside_one_to_five_should_fail() {
        assertFailsWith<IllegalArgumentException> { baseRecord().copy(clarityScore = 6) }
    }

    @Test
    fun re_estimating_should_be_refused_so_the_original_prediction_survives() {
        val estimated = baseRecord().withEstimate(
            estimatedHours = WorkHours(87.0),
            estimatedHoursP90 = WorkHours(98.0),
            estimatedBy = EstimatorKind.AI,
            estimatorRef = "claude-opus-5/pricing-v1",
            confidence = EstimateConfidence.MEDIUM,
            estimatedAt = kotlinx.datetime.Instant.fromEpochSeconds(1_800_000_000)
        )
        assertFailsWith<IllegalStateException> {
            estimated.withEstimate(
                estimatedHours = WorkHours(120.0),
                estimatedHoursP90 = WorkHours(140.0),
                estimatedBy = EstimatorKind.HUMAN,
                estimatorRef = "achmad",
                confidence = EstimateConfidence.HIGH,
                estimatedAt = kotlinx.datetime.Instant.fromEpochSeconds(1_800_100_000)
            )
        }
    }

    @Test
    fun p90_below_p50_should_fail() {
        assertFailsWith<IllegalArgumentException> {
            baseRecord().withEstimate(
                estimatedHours = WorkHours(98.0),
                estimatedHoursP90 = WorkHours(87.0),
                estimatedBy = EstimatorKind.HUMAN,
                estimatorRef = "achmad",
                confidence = EstimateConfidence.MEDIUM,
                estimatedAt = kotlinx.datetime.Instant.fromEpochSeconds(1_800_000_000)
            )
        }
    }
}

class EffortRollupTest {

    private fun entry(role: EffortRole, hours: Double, rate: Long) = ModuleBuildEffortEntry(
        id = EffortEntryId("e-${role.code}-$hours"),
        buildRecordId = ModuleBuildId("b-001"),
        role = role,
        phase = BuildPhase.IMPLEMENTATION,
        hours = WorkHours(hours),
        hourlyRateIdr = MoneyIdr(rate)
    )

    @Test
    fun rollup_should_total_hours_and_cost() {
        val rollup = EffortRollup.of(
            listOf(
                entry(EffortRole.BACKEND, 34.0, 150_000),
                entry(EffortRole.FRONTEND, 28.0, 150_000),
                entry(EffortRole.QA, 12.0, 90_000)
            )
        )
        assertEquals(74.0, rollup.totalHours.hours, 1e-9)
        assertEquals(MoneyIdr(34 * 150_000L + 28 * 150_000L + 12 * 90_000L), rollup.totalCost)
    }

    @Test
    fun blended_rate_should_be_weighted_by_hours_not_averaged_across_roles() {
        // 40 backend hours at 150k and 4 QA hours at 90k. A plain mean of the two rates would say
        // 120k; weighting by hours puts it near the backend rate, which is where the cost is.
        val rollup = EffortRollup.of(
            listOf(
                entry(EffortRole.BACKEND, 40.0, 150_000),
                entry(EffortRole.QA, 4.0, 90_000)
            )
        )
        assertTrue(
            rollup.blendedHourlyRate.amount > 140_000,
            "blended rate ${rollup.blendedHourlyRate.amount} looks like an unweighted mean"
        )
    }

    @Test
    fun empty_effort_should_roll_up_to_zero_rather_than_fail() {
        val rollup = EffortRollup.of(emptyList())
        assertEquals(WorkHours.ZERO, rollup.totalHours)
        assertEquals(MoneyIdr.ZERO, rollup.totalCost)
    }

    @Test
    fun zero_hour_effort_entry_should_fail() {
        assertFailsWith<IllegalArgumentException> { entry(EffortRole.BACKEND, 0.0, 150_000) }
    }
}

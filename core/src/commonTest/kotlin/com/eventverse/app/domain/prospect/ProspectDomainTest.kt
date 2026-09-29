package com.eventverse.app.domain.prospect

import com.eventverse.app.domain.pipeline.code

import com.eventverse.app.domain.pack.GarmentSlots

import com.eventverse.app.domain.moduledev.MoneyIdr
import com.eventverse.app.domain.pipeline.ModuleArchetype
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PriceRangeRoundingTest {

    @Test
    fun low_end_should_round_down_and_high_end_should_round_up() {
        assertEquals(MoneyIdr(2_000_000), PriceRangeRounding.floorTo(MoneyIdr(2_400_000)))
        assertEquals(MoneyIdr(2_500_000), PriceRangeRounding.ceilTo(MoneyIdr(2_400_000)))
    }

    @Test
    fun a_small_amount_must_not_floor_to_zero_and_read_as_free() {
        // At a fixed Rp 500.000 step this produced "Rp 0" for a Rp 300.000 subscription.
        assertEquals(MoneyIdr(300_000), PriceRangeRounding.floorTo(MoneyIdr(300_000)))
        assertEquals(MoneyIdr(300_000), PriceRangeRounding.ceilTo(MoneyIdr(300_000)))
    }

    @Test
    fun precision_should_coarsen_as_the_amount_grows() {
        assertEquals(50_000L, PriceRangeRounding.stepFor(MoneyIdr(300_000)))
        assertEquals(100_000L, PriceRangeRounding.stepFor(MoneyIdr(800_000)))
        assertEquals(500_000L, PriceRangeRounding.stepFor(MoneyIdr(2_400_000)))
        assertEquals(1_000_000L, PriceRangeRounding.stepFor(MoneyIdr(42_000_000)))
    }

    @Test
    fun rounding_must_never_narrow_a_range() {
        // The failure this guards against: rounding to nearest would put the displayed ceiling at
        // 4_000_000 while the real p90 price is 4_100_000 — and the figure we send after review
        // would then exceed a range the prospect already saw.
        val realHigh = MoneyIdr(4_100_000)
        val displayed = PriceRangeRounding.ceilTo(realHigh)
        assertTrue(displayed.amount >= realHigh.amount, "ceiling narrowed the range")

        val realLow = MoneyIdr(2_100_000)
        assertTrue(PriceRangeRounding.floorTo(realLow).amount <= realLow.amount)
    }

    @Test
    fun an_exact_multiple_should_be_left_alone() {
        assertEquals(MoneyIdr(2_500_000), PriceRangeRounding.ceilTo(MoneyIdr(2_500_000)))
        assertEquals(MoneyIdr(2_500_000), PriceRangeRounding.floorTo(MoneyIdr(2_500_000)))
    }

    @Test
    fun zero_step_should_fail() {
        assertFailsWith<IllegalArgumentException> { PriceRangeRounding.ceilTo(MoneyIdr(1), step = 0) }
    }
}

class ProspectPriceRangeTest {

    private fun range(
        gapLow: Long?, gapHigh: Long?, unpriceable: Int
    ) = ProspectPriceRange(
        subscriptionMonthly = MoneyIdr(800_000),
        gapLowMonthly = gapLow?.let { MoneyIdr(it) },
        gapHighMonthly = gapHigh?.let { MoneyIdr(it) },
        unpriceableGapCount = unpriceable,
        expectedTenantCount = 1,
        amortizationMonths = 24,
        pricingModelVersion = "v1"
    )

    @Test
    fun a_fully_priced_flow_should_publish_a_rounded_range() {
        val r = range(gapLow = 1_100_000, gapHigh = 1_600_000, unpriceable = 0)
        assertTrue(r.isPublishable)
        // 800k + 1.1jt = 1.9jt (step 100k) -> 1.9jt ; 800k + 1.6jt = 2.4jt (step 500k) -> 2.5jt
        assertEquals(MoneyIdr(1_900_000), r.displayLow)
        assertEquals(MoneyIdr(2_500_000), r.displayHigh)
    }

    @Test
    fun one_unestimated_gap_should_withhold_the_whole_range() {
        // Summing only the gaps we could price and calling it the total is guaranteed to understate.
        val r = range(gapLow = 1_100_000, gapHigh = 1_600_000, unpriceable = 1)
        assertTrue(!r.isPublishable)
        assertNull(r.displayLow)
        assertNull(r.displayHigh)
    }

    @Test
    fun withheld_should_keep_the_subscription_part_a_reviewer_can_still_use() {
        val r = ProspectPriceRange.withheld(MoneyIdr(800_000), unpriceableGapCount = 2)
        assertEquals(MoneyIdr(800_000), r.subscriptionMonthly)
        assertTrue(!r.isPublishable)
        assertEquals(2, r.unpriceableGapCount)
    }

    @Test
    fun withheld_should_never_report_zero_unpriceable_gaps() {
        // Otherwise it would look publishable-but-empty rather than deliberately withheld.
        assertEquals(1, ProspectPriceRange.withheld(MoneyIdr.ZERO, unpriceableGapCount = 0).unpriceableGapCount)
    }

    @Test
    fun a_flow_with_no_gaps_at_all_should_publish_an_exact_figure() {
        val r = range(gapLow = 0, gapHigh = 0, unpriceable = 0)
        assertTrue(r.isPublishable)
        assertEquals(r.displayLow, PriceRangeRounding.floorTo(MoneyIdr(800_000)))
    }

    @Test
    fun zero_expected_tenant_count_should_fail() {
        assertFailsWith<IllegalArgumentException> {
            ProspectPriceRange(MoneyIdr.ZERO, null, null, 0, expectedTenantCount = 0, 24, "v1")
        }
    }
}

class ProposedFlowValidatorTest {

    private fun requirement(
        archetype: ModuleArchetype,
        title: String = archetype.code,
        quote: String = "kutipan narasi"
    ) = CapabilityRequirement(archetype, title, sourceQuote = quote)

    /**
     * The finding that killed port-equality validation.
     *
     * Three of the first four links in the archetype chain do not line up, because the upstream
     * slots are planning steps whose output is *derived into* the next document rather than piped
     * into it. A validator built on equality would fire three times on a completely ordinary
     * full-package factory.
     */
    @Test
    fun the_declared_archetype_chain_does_not_line_up_end_to_end() {
        val chain = listOf(
            GarmentSlots.ORDER_INGESTION,
            GarmentSlots.RAW_MATERIAL,
            GarmentSlots.COSTING_HPP,
            GarmentSlots.CUTTING
        )
        val mismatches = chain.zipWithNext().count { (a, b) -> !ProposedFlowValidator.payloadMatches(a, b) }
        assertEquals(3, mismatches, "port chain changed; revisit why validation avoids equality")
    }

    @Test
    fun the_manufacturing_half_does_pass_the_same_payload() {
        listOf(
            GarmentSlots.CUTTING to GarmentSlots.SEWING,
            GarmentSlots.SEWING to GarmentSlots.FINISHING,
            GarmentSlots.FINISHING to GarmentSlots.QUALITY_CONTROL,
            GarmentSlots.QUALITY_CONTROL to GarmentSlots.FULFILLMENT
        ).forEach { (a, b) ->
            assertTrue(ProposedFlowValidator.payloadMatches(a, b), "$a -> $b should pass the same payload")
        }
    }

    @Test
    fun custom_extension_should_connect_to_anything() {
        assertTrue(ProposedFlowValidator.payloadMatches(GarmentSlots.FULFILLMENT, GarmentSlots.CUSTOM_EXTENSION))
        assertTrue(ProposedFlowValidator.payloadMatches(GarmentSlots.CUSTOM_EXTENSION, GarmentSlots.ORDER_INGESTION))
    }

    @Test
    fun a_normal_cmt_flow_should_produce_no_warnings() {
        // Sewing without cutting: a workshop that receives pre-cut panels. Strict port validation
        // would have rejected this perfectly ordinary factory.
        val warnings = ProposedFlowValidator.warningsFor(
            listOf(
                requirement(GarmentSlots.ORDER_INGESTION),
                requirement(GarmentSlots.SEWING),
                requirement(GarmentSlots.QUALITY_CONTROL)
            )
        )
        assertEquals(emptyList(), warnings)
    }

    @Test
    fun two_requirements_in_one_slot_should_warn_about_double_counting() {
        val warnings = ProposedFlowValidator.warningsFor(
            listOf(
                requirement(GarmentSlots.ORDER_INGESTION),
                requirement(GarmentSlots.QUALITY_CONTROL, "QC AQL"),
                requirement(GarmentSlots.QUALITY_CONTROL, "QC end-line")
            )
        )
        assertEquals(1, warnings.size)
        assertTrue(warnings.single().contains("terpecah dua"), warnings.single())
    }

    @Test
    fun several_custom_needs_should_not_be_treated_as_duplicates() {
        // "sablon" and "laundry kimia" legitimately share the wildcard slot.
        val warnings = ProposedFlowValidator.warningsFor(
            listOf(
                requirement(GarmentSlots.ORDER_INGESTION),
                requirement(GarmentSlots.CUSTOM_EXTENSION, "Sablon manual"),
                requirement(GarmentSlots.CUSTOM_EXTENSION, "Laundry kimia")
            )
        )
        assertTrue(warnings.none { it.contains("terpecah dua") }, warnings.toString())
    }

    @Test
    fun a_flow_with_no_order_intake_should_warn() {
        val warnings = ProposedFlowValidator.warningsFor(
            listOf(requirement(GarmentSlots.SEWING), requirement(GarmentSlots.QUALITY_CONTROL))
        )
        assertTrue(warnings.any { it.contains("penerimaan pesanan") }, warnings.toString())
    }
}

class ProspectLeadTest {

    private fun lead() = ProspectLead(
        id = ProspectLeadId("lead-1"),
        companyName = "CV Berkah Makloon",
        narrativeRaw = "Kami makloon jaket. Kain dari buyer."
    )

    @Test
    fun blank_narrative_should_fail() {
        assertFailsWith<IllegalArgumentException> { lead().copy(narrativeRaw = "   ") }
    }

    @Test
    fun placeholder_tenant_id_should_be_valid_but_obviously_not_a_real_tenant() {
        val placeholder = lead().placeholderTenantId
        assertTrue(placeholder.value.startsWith("prospect-"))
        assertTrue(placeholder.value.length in 3..64)
    }

    @Test
    fun a_very_long_lead_id_should_still_produce_a_valid_placeholder() {
        val long = ProspectLead(
            id = ProspectLeadId("l".repeat(64)),
            companyName = "PT Panjang",
            narrativeRaw = "narasi"
        )
        assertTrue(long.placeholderTenantId.value.length <= 64)
    }

    @Test
    fun conversion_should_record_which_tenant_it_became() {
        val converted = lead().convertTo(com.eventverse.app.domain.tenant.TenantId("ten-baru"))
        assertEquals(LeadStatus.CONVERTED, converted.status)
        assertEquals("ten-baru", converted.convertedTenantId?.value)
        assertTrue(!converted.status.isOpen)
    }
}

class CoverageAnalysisTest {

    private val requirement = CapabilityRequirement(GarmentSlots.SEWING, "Jahit")

    @Test
    fun a_flow_with_no_gaps_should_report_fully_covered() {
        val analysis = CoverageAnalysis(
            listOf(
                CoverageDecision.CoveredByCatalog(
                    requirement,
                    com.eventverse.app.domain.moduledev.ModuleCatalogEntry(
                        id = com.eventverse.app.domain.moduledev.ModuleCatalogEntryId("mce-1"),
                        moduleId = "operator_exec",
                        archetypeCode = "sewing",
                        displayName = "Catatan Kerja Operator"
                    )
                )
            )
        )
        assertTrue(analysis.isFullyCovered)
        assertTrue(!analysis.hasGaps)
    }

    @Test
    fun an_empty_analysis_should_not_claim_full_coverage() {
        assertTrue(!CoverageAnalysis(emptyList()).isFullyCovered)
    }
}

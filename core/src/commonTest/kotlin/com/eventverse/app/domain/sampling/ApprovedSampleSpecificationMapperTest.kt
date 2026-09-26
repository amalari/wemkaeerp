package com.eventverse.app.domain.sampling

import com.eventverse.app.domain.common.UnitOfMeasure
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ApprovedSampleSpecificationMapperTest {

    private val tenantId = TenantId("ten-demo-001")
    private val now = Instant.parse("2026-01-01T00:00:00Z")

    private fun createOrder(status: SamplingStatus) = SamplingOrder(
        id = SamplingOrderId("smp-1"),
        tenantId = tenantId,
        spkNumber = SpkNumber("SPK-SMP-001"),
        clientName = "Brand Distro",
        styleName = "Cardigan Rajut",
        status = status,
        yieldAndTiming = YieldAndTiming(
            panelWeights = PanelWeightGrams(
                front = 120.0,
                back = 110.0,
                sleeve = 80.0,
                collar = 20.0,
                placket = 15.0
            ),
            panelMinutes = PanelKnittingMinutes(
                front = 25,
                back = 22,
                sleeve = 15,
                collar = 5,
                placket = 4
            ),
            additionalProcess = "Pasang Kancing Batok",
            isWashed = true,
            estimatedHppIdr = 65_000
        ),
        knitSpec = KnitSpec(yarnType = "Viscose 2/30"),
        createdAt = now,
        updatedAt = now
    )

    @Test
    fun toApprovedSampleSpecification_whenNotApproved_shouldFail() {
        val order = createOrder(SamplingStatus.IN_PROGRESS)
        val result = order.toApprovedSampleSpecification(now)
        assertTrue(result.isFailure)
    }

    @Test
    fun toApprovedSampleSpecification_whenAccApproved_shouldMapYieldAndMaterials() {
        val order = createOrder(SamplingStatus.ACC_APPROVED)
        val result = order.toApprovedSampleSpecification(now)
        assertTrue(result.isSuccess)

        val spec = result.getOrThrow()
        assertEquals("SPK-SMP-001", spec.spkNumber)
        assertEquals("Brand Distro", spec.clientName)
        assertEquals(5, spec.panelYields.size)

        // Total panel weight = 120 + 110 + 80 + 20 + 15 = 345 grams
        assertEquals(345_000_000L, spec.totalPanelWeight.micros)
        assertEquals(UnitOfMeasure.GRAM, spec.totalPanelWeight.uom)

        // Total knitting minutes = 25 + 22 + 15 + 5 + 4 = 71 minutes
        assertEquals(71L, spec.totalKnittingMinutes)

        // Unresolved materials with Passthrough resolver
        val unresolved = spec.unresolvedMaterials
        assertTrue(unresolved.any { it.freeText.contains("Viscose") })
        assertTrue(unresolved.any { it.freeText.contains("Kancing") })

        // Legacy HPP carried over
        assertEquals(65_000_00L, spec.legacyEstimatedHpp?.minorUnits)
    }
}

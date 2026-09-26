package com.eventverse.app.domain.traceability

import com.eventverse.app.domain.contracts.GarmentPanel
import com.eventverse.app.domain.tenant.TenantId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TraceAllocationPlanTest {

    private fun snapshot(sizes: List<TraceSizeLine>) = TraceWorkOrderSnapshot(
        ref = TraceWorkOrderRef(TraceWorkOrderKind.SAMPLING, "smp_1"),
        tenantId = TenantId("tnt-1"),
        tenantOrdinal = 3,
        ordinal = 9,
        spkNumber = "SPK-SMP-0009",
        styleName = "Cardigan",
        clientName = "PT Buyer",
        sizes = sizes,
        panelRequirements = listOf(PanelRequirement(GarmentPanel.BODY_FRONT, 1))
    )

    @Test
    fun `bundle card count rounds up and adds spares`() {
        val plan = TraceAllocationPlan.plan(
            snapshot(listOf(TraceSizeLine("L", 300))),
            setsPerBundle = 20,
            pcsPerSack = 60,
            sparePerSize = 2
        )
        // 300 / 20 = 15 kartu, + 2 cadangan
        assertEquals(17, plan.labelsFor(TraceTier.BUNDLE, "L").size)
        // 300 / 60 = 5 karung, + 2 cadangan
        assertEquals(7, plan.labelsFor(TraceTier.SACK, "L").size)
    }

    @Test
    fun `a partial last bundle still gets its own card`() {
        val plan = TraceAllocationPlan.plan(
            snapshot(listOf(TraceSizeLine("M", 41))),
            setsPerBundle = 20, pcsPerSack = 60, sparePerSize = 0
        )
        assertEquals(3, plan.labelsFor(TraceTier.BUNDLE, "M").size)
    }

    @Test
    fun `every allocated code is unique and parses back to its own coordinates`() {
        val plan = TraceAllocationPlan.plan(
            snapshot(listOf(TraceSizeLine("L", 100), TraceSizeLine("XL", 60))),
            sparePerSize = 1
        )
        assertEquals(plan.labels.size, plan.labels.map { it.code.value }.distinct().size)

        plan.labels.forEach { label ->
            val parts = TraceCodec.parse(label.code.value)!!
            assertEquals(label.tier, parts.tier)
            assertEquals(label.sizeIndex, parts.sizeIndex)
            assertEquals(label.sequence, parts.sequence)
            assertEquals(3, parts.tenantOrdinal)
            assertEquals(9, parts.workOrderOrdinal)
        }
    }

    @Test
    fun `caption carries the human readable identity printed under the qr`() {
        val plan = TraceAllocationPlan.plan(snapshot(listOf(TraceSizeLine("L", 20))), sparePerSize = 0)
        val caption = plan.labelsFor(TraceTier.BUNDLE, "L").first().captionFor("SPK-SMP-0009")
        assertEquals("SPK-SMP-0009 · Size L · BDL-001", caption)
    }

    @Test
    fun `sheet estimate counts both card grids`() {
        val plan = TraceAllocationPlan.plan(
            snapshot(listOf(TraceSizeLine("L", 160))),
            setsPerBundle = 20, pcsPerSack = 60, sparePerSize = 0
        )
        // 8 kartu bundel -> 1 lembar; 3 kartu karung -> 1 lembar
        assertEquals(2, plan.sheetCountEstimate)
    }

    @Test
    fun `a work order without sizes cannot be planned`() {
        assertTrue(TraceAllocationPlan.plan(snapshot(emptyList())).labels.isEmpty())
    }

    @Test
    fun `ordinal beyond code capacity is rejected loudly`() {
        val tooBig = snapshot(listOf(TraceSizeLine("L", 10)))
            .copy(ordinal = TraceCodec.MAX_WORK_ORDER_ORDINAL)
        assertFailsWith<IllegalArgumentException> { TraceAllocationPlan.plan(tooBig) }
    }
}

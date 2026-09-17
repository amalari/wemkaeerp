package com.eventverse.app.domain.production

import com.eventverse.app.domain.pipeline.FlowHealthStatus
import com.eventverse.app.domain.sampling.SamplingOrderId
import com.eventverse.app.domain.tenant.TenantId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.datetime.Instant

private val NOW = Instant.parse("2026-09-17T10:00:00Z")

private fun workOrder(
    sizeBreakdown: List<BulkSizeLine> = listOf(
        BulkSizeLine("M", 400),
        BulkSizeLine("L", 600)
    ),
    goldenSample: SamplingOrderId? = SamplingOrderId("smp-089"),
    status: BulkProductionStatus = BulkProductionStatus.RELEASED
) = BulkWorkOrder(
    id = BulkWorkOrderId("bwo-1"),
    tenantId = TenantId("tnt-1"),
    spkNumber = BulkSpkNumber("SPK-MSL-0001"),
    clientName = "PT Sinar Jaya",
    styleName = "Kemeja PDH",
    status = status,
    goldenSampleOrderId = goldenSample,
    sizeBreakdown = sizeBreakdown,
    createdAt = NOW,
    updatedAt = NOW
)

class BulkWorkOrderTest {

    @Test
    fun `total ordered pcs sums every size line`() {
        assertEquals(1000, workOrder().totalOrderedPcs)
    }

    @Test
    fun `release when golden sample missing should fail`() {
        val draft = workOrder(goldenSample = null, status = BulkProductionStatus.DRAFT)

        assertFalse(draft.isReadyForRelease)
        val error = assertFailsWith<IllegalArgumentException> { draft.release(NOW) }
        assertTrue(error.message!!.contains("Golden Sample"))
    }

    @Test
    fun `release when requirements met should mark released`() {
        val released = workOrder(status = BulkProductionStatus.DRAFT).release(NOW)

        assertEquals(BulkProductionStatus.RELEASED, released.status)
        assertEquals(NOW, released.releasedAt)
    }

    @Test
    fun `allocate line beyond ordered quantity should fail`() {
        val order = workOrder()
            .allocateLine(MachineLineAllocation(ProductionLineName("Line 1"), 10, 700), NOW)

        val error = assertFailsWith<IllegalArgumentException> {
            order.allocateLine(MachineLineAllocation(ProductionLineName("Line 2"), 10, 400), NOW)
        }
        assertTrue(error.message!!.contains("melebihi pesanan"))
    }

    @Test
    fun `reallocating same line replaces instead of stacking`() {
        val order = workOrder()
            .allocateLine(MachineLineAllocation(ProductionLineName("Line 1"), 10, 400), NOW)
            .allocateLine(MachineLineAllocation(ProductionLineName("Line 1"), 12, 600), NOW)

        assertEquals(1, order.lineAllocations.size)
        assertEquals(600, order.allocatedPcs)
        assertEquals(400, order.unallocatedPcs)
    }

    @Test
    fun `sewing beyond cutting passed output should fail`() {
        val order = workOrder()
            .recordStageProgress(ProductionStage.CUTTING, completedPcs = 300, updatedAt = NOW)

        val error = assertFailsWith<IllegalArgumentException> {
            order.recordStageProgress(ProductionStage.SEWING, completedPcs = 500, updatedAt = NOW)
        }
        assertTrue(error.message!!.contains("melebihi hasil Potong"))
    }

    @Test
    fun `rework reduces pieces passed to the next stage`() {
        val order = workOrder()
            .recordStageProgress(ProductionStage.CUTTING, completedPcs = 300, reworkPcs = 50, updatedAt = NOW)

        assertEquals(250, order.progressFor(ProductionStage.CUTTING).passedPcs)
        assertFailsWith<IllegalArgumentException> {
            order.recordStageProgress(ProductionStage.SEWING, completedPcs = 300, updatedAt = NOW)
        }
    }

    @Test
    fun `progress recording is cumulative not additive`() {
        val order = workOrder()
            .recordStageProgress(ProductionStage.CUTTING, completedPcs = 200, updatedAt = NOW)
            .recordStageProgress(ProductionStage.CUTTING, completedPcs = 200, updatedAt = NOW)

        assertEquals(200, order.progressFor(ProductionStage.CUTTING).completedPcs)
    }

    @Test
    fun `status never walks backwards when an earlier stage is corrected`() {
        val order = workOrder()
            .recordStageProgress(ProductionStage.CUTTING, completedPcs = 1000, updatedAt = NOW)
            .recordStageProgress(ProductionStage.SEWING, completedPcs = 800, updatedAt = NOW)

        assertEquals(BulkProductionStatus.SEWING, order.status)

        val corrected = order.recordStageProgress(ProductionStage.CUTTING, completedPcs = 950, updatedAt = NOW)
        assertEquals(BulkProductionStatus.SEWING, corrected.status)
    }

    @Test
    fun `finishing all ordered pieces completes the work order`() {
        val order = workOrder()
            .recordStageProgress(ProductionStage.CUTTING, completedPcs = 1000, updatedAt = NOW)
            .recordStageProgress(ProductionStage.SEWING, completedPcs = 1000, updatedAt = NOW)
            .recordStageProgress(ProductionStage.FINISHING, completedPcs = 1000, updatedAt = NOW)

        assertEquals(BulkProductionStatus.COMPLETED, order.status)
        assertEquals(1000, order.completedPcs)
        assertEquals(0, order.wipPieces)
        assertEquals(FlowHealthStatus.HEALTHY, order.healthStatus)
    }

    @Test
    fun `recording progress on a draft should fail`() {
        val draft = workOrder(status = BulkProductionStatus.DRAFT)

        val error = assertFailsWith<IllegalArgumentException> {
            draft.recordStageProgress(ProductionStage.CUTTING, completedPcs = 10, updatedAt = NOW)
        }
        assertTrue(error.message!!.contains("belum diterbitkan"))
    }

    @Test
    fun `freshly released order queues at cutting without raising an alert`() {
        val untouched = workOrder()

        // Seluruh pesanan menunggu di meja potong; hilir belum kebagian apa pun.
        assertEquals(1000, untouched.wipPieces)
        assertEquals(1000, untouched.largestStageBacklog)
        assertEquals(FlowHealthStatus.HEALTHY, untouched.healthStatus)
        assertFalse(untouched.healthStatus.isAlert)
    }

    @Test
    fun `pieces piling up in front of one stage reports bottleneck`() {
        // Potong sudah meloloskan 900 pcs, jahit baru menyelesaikan 100 — 800 pcs menumpuk.
        val order = workOrder()
            .recordStageProgress(ProductionStage.CUTTING, completedPcs = 900, updatedAt = NOW)
            .recordStageProgress(ProductionStage.SEWING, completedPcs = 100, updatedAt = NOW)

        assertEquals(800, order.largestStageBacklog)
        assertEquals(FlowHealthStatus.BOTTLENECK, order.healthStatus)
    }

    @Test
    fun `evenly flowing order is healthy even with the same total wip`() {
        val order = workOrder()
            .recordStageProgress(ProductionStage.CUTTING, completedPcs = 1000, updatedAt = NOW)
            .recordStageProgress(ProductionStage.SEWING, completedPcs = 700, updatedAt = NOW)
            .recordStageProgress(ProductionStage.FINISHING, completedPcs = 500, updatedAt = NOW)

        assertEquals(FlowHealthStatus.HEALTHY, order.healthStatus)
    }

    @Test
    fun `reject rate above one tenth reports critical`() {
        val order = workOrder()
            .recordStageProgress(ProductionStage.CUTTING, completedPcs = 900, rejectPcs = 150, updatedAt = NOW)

        assertEquals(FlowHealthStatus.CRITICAL, order.healthStatus)
    }

    @Test
    fun `size breakdown edit merges duplicate labels`() {
        val draft = workOrder(status = BulkProductionStatus.DRAFT).updateSizeBreakdown(
            listOf(BulkSizeLine("m", 100), BulkSizeLine("M", 150), BulkSizeLine("L", 50)),
            NOW
        )

        assertEquals(2, draft.sizeBreakdown.size)
        assertEquals(250, draft.sizeBreakdown.first { it.sizeLabel == "M" }.orderedPcs)
    }

    @Test
    fun `size breakdown cannot be edited after release`() {
        val error = assertFailsWith<IllegalArgumentException> {
            workOrder().updateSizeBreakdown(listOf(BulkSizeLine("M", 10)), NOW)
        }
        assertTrue(error.message!!.contains("draft"))
    }
}

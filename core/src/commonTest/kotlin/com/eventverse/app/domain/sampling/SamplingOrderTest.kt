package com.eventverse.app.domain.sampling

import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.shared.sampling.SamplingOrderCodec
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlin.test.*

class SamplingOrderTest {

    private val now = Instant.parse("2026-09-01T00:00:00Z")
    private val tenantId = TenantId("ten-demo-001")

    private fun createSampleOrder() = SamplingOrder(
        id = SamplingOrderId("smp_001"),
        tenantId = tenantId,
        spkNumber = SpkNumber("SPK-SMP-0001"),
        clientName = "BIANCA",
        styleName = "FLORAL CARDIGAN",
        status = SamplingStatus.DRAFT,
        createdAt = now,
        updatedAt = now
    )

    @Test
    fun initialization_withFactoryPresets_shouldHaveValidDualSizeCharts() {
        val order = createSampleOrder()
        val finished = order.finishedSizeCharts.first()
        val raw = order.rawKnitSizeCharts.first()

        assertEquals("ALL SIZE", finished.sizeLabel)
        assertEquals(60.0, finished.bodyLength)
        assertEquals(55.0, finished.bodyWidth)

        assertEquals("ALL SIZE", raw.sizeLabel)
        assertEquals(55.0, raw.bodyLength)
        assertEquals(56.0, raw.bodyWidth)

        assertEquals(7, order.machineProgram.feederInstructions.size)
    }

    @Test
    fun toggleMilestone_shouldUpdateSpecificMilestone() {
        val order = createSampleOrder()
        val completedDate = LocalDate(2026, 9, 2)
        val updated = order.toggleMilestone(
            step = MilestoneStep.PROGRAM,
            isCompleted = true,
            completedAt = completedDate,
            milestoneNotes = "Program BIAN-D selesai",
            updatedAt = now
        )

        val programStep = updated.milestones.first { it.step == MilestoneStep.PROGRAM }
        assertTrue(programStep.isCompleted)
        assertEquals(completedDate, programStep.completedAt)
        assertEquals("Program BIAN-D selesai", programStep.notes)

        val rajutStep = updated.milestones.first { it.step == MilestoneStep.RAJUT }
        assertFalse(rajutStep.isCompleted)
    }

    @Test
    fun approveAcc_shouldSetAccApprovedAndNotes() {
        val order = createSampleOrder()
        val approved = order.approveAcc(notes = "FIX PRODUKSI SAMBIL JALAN REVISI", updatedAt = now)

        assertEquals(SamplingStatus.ACC_APPROVED, approved.status)
        assertTrue(approved.isAccApproved)
        assertEquals("FIX PRODUKSI SAMBIL JALAN REVISI", approved.accNotes)
    }

    @Test
    fun requestRevision_shouldSetRevisionStatus() {
        val order = createSampleOrder()
        val revised = order.requestRevision(notes = "Panjang tangan kurangi 2cm", updatedAt = now)

        assertEquals(SamplingStatus.REVISION, revised.status)
        assertFalse(revised.isAccApproved)
        assertEquals("Panjang tangan kurangi 2cm", revised.accNotes)
    }

    @Test
    fun codec_encodeAndDecode_shouldPreserveAllData() {
        val order = createSampleOrder().approveAcc("ACC BUYER", now)
        val json = SamplingOrderCodec.encode(order)
        val decoded = SamplingOrderCodec.decode(json)

        assertEquals(order.id, decoded.id)
        assertEquals(order.spkNumber, decoded.spkNumber)
        assertEquals(order.clientName, decoded.clientName)
        assertEquals(order.styleName, decoded.styleName)
        assertEquals(order.status, decoded.status)
        assertEquals(order.accNotes, decoded.accNotes)
        assertEquals(order.finishedSizeCharts.first().bodyLength, decoded.finishedSizeCharts.first().bodyLength)
        assertEquals(order.rawKnitSizeCharts.first().bodyLength, decoded.rawKnitSizeCharts.first().bodyLength)
    }

    @Test
    fun resolveGarmentTimeline_inDraftState_shouldHaveInputSpekActive() {
        val order = createSampleOrder() // DRAFT, NEW_INTAKE
        val timeline = order.resolveGarmentTimeline()

        assertEquals(8, timeline.size)
        val step1 = timeline[0]
        assertEquals(GarmentTrackingStep.INPUT_SPEK, step1.step)
        assertFalse(step1.isCompleted)
        assertTrue(step1.isActive)
        assertEquals("Draft", step1.badgeText)

        val step2 = timeline[1]
        assertEquals(GarmentTrackingStep.SPK_RELEASED, step2.step)
        assertFalse(step2.isCompleted)
    }

    @Test
    fun resolveGarmentTimeline_inKnittingState_shouldCompleteInputAndRelease() {
        val order = createSampleOrder().copy(
            status = SamplingStatus.IN_PROGRESS,
            pipelineStage = SamplingPipelineStage.MACHINE_KNITTING
        )
        val timeline = order.resolveGarmentTimeline()

        val step1 = timeline[0]
        assertTrue(step1.isCompleted)

        val step2 = timeline[1]
        assertTrue(step2.isCompleted)

        val step3 = timeline[2]
        assertEquals(GarmentTrackingStep.KNITTING, step3.step)
        assertTrue(step3.isActive)
        assertEquals("Rajut Turun Mesin", step3.subtitle)
    }

    @Test
    fun resolveGarmentTimeline_inDeliveryState_shouldHaveReadyToShipActive() {
        val order = createSampleOrder().copy(
            status = SamplingStatus.IN_PROGRESS,
            pipelineStage = SamplingPipelineStage.IN_DELIVERY,
            courierTracking = null
        )
        val timeline = order.resolveGarmentTimeline()

        val step7 = timeline[6]
        assertEquals(GarmentTrackingStep.READY_TO_SHIP, step7.step)
        assertTrue(step7.isActive)
        assertEquals("Siap Kirim", step7.badgeText)
    }

    @Test
    fun resolveGarmentTimeline_accApproved_shouldCompleteAllSteps() {
        val order = createSampleOrder()
            .copy(courierTracking = "JNE12345678")
            .approveAcc("ACC BUYER", now)

        val timeline = order.resolveGarmentTimeline()
        val step8 = timeline[7]
        assertEquals(GarmentTrackingStep.ACC_APPROVED, step8.step)
        assertTrue(step8.isCompleted)
        assertEquals("ACC", step8.badgeText)
    }
}

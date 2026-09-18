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

        assertEquals(5, timeline.size)
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
        assertEquals(GarmentTrackingStep.SAMPLING, step3.step)
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

        val step4 = timeline[3]
        assertEquals(GarmentTrackingStep.READY_TO_SHIP, step4.step)
        assertTrue(step4.isActive)
        assertEquals("Siap Kirim", step4.badgeText)
    }

    @Test
    fun resolveGarmentTimeline_accApproved_shouldCompleteAllSteps() {
        val order = createSampleOrder()
            .copy(courierTracking = "JNE12345678")
            .approveAcc("ACC BUYER", now)

        val timeline = order.resolveGarmentTimeline()
        val step5 = timeline[4]
        assertEquals(GarmentTrackingStep.ACC_APPROVED, step5.step)
        assertTrue(step5.isCompleted)
        assertEquals("ACC", step5.badgeText)
    }

    // ==========================================================================
    // Gerbang tahap & lembar input dinamis (StageWorkInput)
    // ==========================================================================

    private fun orderAtCam(): SamplingOrder = createSampleOrder().copy(
        status = SamplingStatus.IN_PROGRESS,
        pipelineStage = SamplingPipelineStage.CAM_PROGRAMMING
    )

    private fun filledCamSections(): List<StageInputSection> = listOf(
        StageInputSection.of(
            StageSectionNames.PROGRAM,
            "DEPAN" to "BIAN-D",
            "BELAKANG" to "BIAN-B"
        ),
        StageInputSection.of(
            StageSectionNames.FEEDER_INSTRUCTIONS,
            "1" to "RIB STRIPE 1 PLAY ( HITAM )"
        ),
        StageInputSection.of(
            StageSectionNames.PATTERN_FORMULAS,
            "P BADAN" to "2.94 K"
        )
    )

    @Test
    fun advanceToMachineKnitting_withoutCamInputs_shouldBeRejected() {
        val error = assertFailsWith<IllegalArgumentException> {
            orderAtCam().advancePipelineStage(SamplingPipelineStage.MACHINE_KNITTING, now)
        }
        assertTrue((error.message ?: "").contains("Program CAM"))
    }

    @Test
    fun advanceToMachineKnitting_withCompleteCamInputs_shouldRecordAudit() {
        val order = orderAtCam().fillStageInput(
            SamplingPipelineStage.CAM_PROGRAMMING,
            filledCamSections(),
            now
        ).advancePipelineStage(
            SamplingPipelineStage.MACHINE_KNITTING,
            now,
            actorEmail = "admin@wemade.id",
            actorRole = "TENANT_ADMIN"
        )

        assertEquals(SamplingPipelineStage.MACHINE_KNITTING, order.pipelineStage)
        assertEquals(1, order.stageHistory.size)
        val audit = order.stageHistory.single()
        assertEquals(SamplingPipelineStage.CAM_PROGRAMMING, audit.fromStage)
        assertEquals(SamplingPipelineStage.MACHINE_KNITTING, audit.toStage)
        assertEquals("admin@wemade.id", audit.actorEmail)
    }

    @Test
    fun advanceWithoutGate_fromNewIntakeToCam_shouldSucceedWithoutInputs() {
        val order = createSampleOrder().advancePipelineStage(
            SamplingPipelineStage.CAM_PROGRAMMING,
            now,
            actorEmail = "sales@wemade.id",
            actorRole = "TENANT_ADMIN"
        )

        assertEquals(SamplingPipelineStage.CAM_PROGRAMMING, order.pipelineStage)
        assertEquals("sales@wemade.id", order.stageHistory.single().actorEmail)
    }

    @Test
    fun fillStageInput_twiceForSameStage_shouldReplaceNotDuplicate() {
        val order = orderAtCam()
            .fillStageInput(SamplingPipelineStage.CAM_PROGRAMMING, filledCamSections(), now)
            .fillStageInput(SamplingPipelineStage.CAM_PROGRAMMING, filledCamSections(), now)

        assertEquals(1, order.stageInputs.size)
    }

    @Test
    fun samplingOrderCodec_stageInputsAndHistory_roundTripShouldPreserveData() {
        val order = orderAtCam()
            .fillStageInput(SamplingPipelineStage.CAM_PROGRAMMING, filledCamSections(), now)
            .advancePipelineStage(SamplingPipelineStage.MACHINE_KNITTING, now, "a@b.id", "TENANT_ADMIN")

        val decoded = SamplingOrderCodec.decode(SamplingOrderCodec.encode(order))

        assertEquals(1, decoded.stageInputs.size)
        assertEquals(
            filledCamSections().map { it.section },
            decoded.stageInputs.single().sections.map { it.section }
        )
        assertEquals(1, decoded.stageHistory.size)
        assertEquals("a@b.id", decoded.stageHistory.single().actorEmail)
    }
}

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
}

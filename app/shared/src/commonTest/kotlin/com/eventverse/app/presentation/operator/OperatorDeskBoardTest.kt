package com.eventverse.app.presentation.operator

import com.eventverse.app.domain.pipeline.DefectLiability
import com.eventverse.app.domain.sampling.FinishingPath
import com.eventverse.app.domain.sampling.SamplingOrder
import com.eventverse.app.domain.sampling.SamplingOrderId
import com.eventverse.app.domain.sampling.SamplingPipelineStage
import com.eventverse.app.domain.sampling.SamplingStatus
import com.eventverse.app.domain.sampling.SpkNumber
import com.eventverse.app.domain.sampling.sendBackForRework
import com.eventverse.app.domain.sampling.startStageWork
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OperatorDeskBoardTest {

    private val tz = TimeZone.UTC
    private val day1 = Instant.parse("2026-09-24T08:00:00Z")
    private val day2 = Instant.parse("2026-09-25T08:00:00Z")
    private val today = LocalDate(2026, 9, 25)

    private fun order(id: String, stage: SamplingPipelineStage, at: Instant = day2) = SamplingOrder(
        id = SamplingOrderId(id),
        tenantId = TenantId("demo"),
        spkNumber = SpkNumber("SPK-$id"),
        clientName = "C",
        styleName = "S",
        status = SamplingStatus.IN_PROGRESS,
        pipelineStage = stage,
        createdAt = at,
        updatedAt = at
    )

    @Test
    fun `board should split queue and in progress for its stage only`() {
        val orders = listOf(
            order("a", SamplingPipelineStage.CUCI_SOFTENER),
            order("b", SamplingPipelineStage.CUCI_SOFTENER).startStageWork("Sari", "", day2),
            order("c", SamplingPipelineStage.SETRIKA_UAP)
        )
        val board = buildOperatorDeskBoard(orders, SamplingPipelineStage.CUCI_SOFTENER, today, tz)
        assertEquals(listOf("a"), board.queue.map { it.id.value })
        assertEquals(listOf("b"), board.inProgress.map { it.id.value })
    }

    @Test
    fun `rework card should sit on top of queue`() {
        val normal = order("n", SamplingPipelineStage.LINKING_ASSEMBLY, day1)
        val rework = order("r", SamplingPipelineStage.QC_FINISHING).sendBackForRework(
            SamplingPipelineStage.LINKING_ASSEMBLY, "lepas", DefectLiability.FACTORY_WORKMANSHIP, "", "", day2
        )
        val board = buildOperatorDeskBoard(listOf(normal, rework), SamplingPipelineStage.LINKING_ASSEMBLY, today, tz)
        assertEquals(listOf("r", "n"), board.queue.map { it.id.value })
    }

    @Test
    fun `done column should only show handoffs from today while history keeps all`() {
        val yesterday = order("y", SamplingPipelineStage.CUCI_SOFTENER, day1)
            .advancePipelineStage(SamplingPipelineStage.SETRIKA_UAP, day1)
        val todayDone = order("t", SamplingPipelineStage.CUCI_SOFTENER)
            .advancePipelineStage(SamplingPipelineStage.SETRIKA_UAP, day2)
        val board = buildOperatorDeskBoard(listOf(yesterday, todayDone), SamplingPipelineStage.CUCI_SOFTENER, today, tz)
        assertEquals(listOf("t"), board.doneToday.map { it.order.id.value })
        assertEquals(2, board.history.size)
    }

    @Test
    fun `makloon order should not appear on linking desk`() {
        val makloon = order("m", SamplingPipelineStage.LINKING_ASSEMBLY).copy(finishingPath = FinishingPath.MAKLOON_VENDOR)
        val board = buildOperatorDeskBoard(listOf(makloon), SamplingPipelineStage.LINKING_ASSEMBLY, today, tz)
        assertTrue(board.queue.isEmpty())
    }
}

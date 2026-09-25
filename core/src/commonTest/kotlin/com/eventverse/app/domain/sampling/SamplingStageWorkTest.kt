package com.eventverse.app.domain.sampling

import com.eventverse.app.domain.pipeline.DefectLiability
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.shared.sampling.SamplingOrderCodec
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SamplingStageWorkTest {

    private val now = Instant.fromEpochMilliseconds(1_700_000_000_000)
    private val later = Instant.fromEpochMilliseconds(1_700_000_600_000)

    private fun order(stage: SamplingPipelineStage, qty: Int = 2) = SamplingOrder(
        id = SamplingOrderId("smp_work"),
        tenantId = TenantId("demo-tenant"),
        spkNumber = SpkNumber("SPK-SMP-0200"),
        clientName = "BIANCA",
        styleName = "RIB CARDIGAN",
        status = SamplingStatus.IN_PROGRESS,
        pipelineStage = stage,
        sampleQuantity = qty,
        createdAt = now,
        updatedAt = now
    )

    @Test
    fun `startStageWork when queued should move card to in progress`() {
        val started = order(SamplingPipelineStage.CUCI_SOFTENER).startStageWork("Sari", "sari@x.id", now)
        assertEquals(OperatorDeskColumn.IN_PROGRESS, started.deskColumn(SamplingPipelineStage.CUCI_SOFTENER))
        assertEquals("Sari", started.currentWork?.operatorName)
    }

    @Test
    fun `startStageWork when already claimed should throw`() {
        val started = order(SamplingPipelineStage.CUCI_SOFTENER).startStageWork("Sari", "", now)
        assertFailsWith<IllegalArgumentException> { started.startStageWork("Budi", "", later) }
    }

    @Test
    fun `startStageWork outside operator desk should throw`() {
        assertFailsWith<IllegalArgumentException> {
            order(SamplingPipelineStage.CAM_PROGRAMMING).startStageWork("Sari", "", now)
        }
    }

    @Test
    fun `releaseStageWork should return card to queue`() {
        val released = order(SamplingPipelineStage.SETRIKA_UAP).startStageWork("Sari", "", now).releaseStageWork(later)
        assertEquals(OperatorDeskColumn.QUEUE, released.deskColumn(SamplingPipelineStage.SETRIKA_UAP))
    }

    @Test
    fun `advance should clear active work and record handoff`() {
        val advanced = order(SamplingPipelineStage.CUCI_SOFTENER)
            .startStageWork("Sari", "", now)
            .advancePipelineStage(SamplingPipelineStage.SETRIKA_UAP, later, "sari@x.id")
        assertNull(advanced.activeWork)
        assertEquals(OperatorDeskColumn.QUEUE, advanced.deskColumn(SamplingPipelineStage.SETRIKA_UAP))
        assertEquals(1, advanced.handoffsFrom(SamplingPipelineStage.CUCI_SOFTENER).size)
    }

    @Test
    fun `stale claim from previous stage should not count as in progress`() {
        val stale = order(SamplingPipelineStage.SETRIKA_UAP).copy(
            activeWork = StageWorkClaim(SamplingPipelineStage.CUCI_SOFTENER, "Sari", "", now)
        )
        assertEquals(OperatorDeskColumn.QUEUE, stale.deskColumn(SamplingPipelineStage.SETRIKA_UAP))
    }

    @Test
    fun `full linking deposit should record handoff to cuci`() {
        val deposit = FinishingDeposit(depositDate = kotlinx.datetime.LocalDate(2026, 9, 25), qtyPcs = 2, operatorName = "Rina")
        val done = order(SamplingPipelineStage.LINKING_ASSEMBLY).addFinishingDeposit(deposit, later)
        assertEquals(SamplingPipelineStage.CUCI_SOFTENER, done.pipelineStage)
        assertEquals("Rina", done.handoffsFrom(SamplingPipelineStage.LINKING_ASSEMBLY).single().actorEmail)
    }

    @Test
    fun `sendBackForRework to earlier desk should land in its queue with reason`() {
        val reworked = order(SamplingPipelineStage.QC_FINISHING)
            .startStageWork("QC Ani", "", now)
            .sendBackForRework(
                target = SamplingPipelineStage.LINKING_ASSEMBLY,
                reason = "Jahitan bahu lepas",
                liability = DefectLiability.FACTORY_WORKMANSHIP,
                actorEmail = "ani@x.id",
                actorRole = "QC",
                now = later
            )
        assertEquals(OperatorDeskColumn.QUEUE, reworked.deskColumn(SamplingPipelineStage.LINKING_ASSEMBLY))
        assertEquals(1, reworked.reworkCount)
        assertEquals("Jahitan bahu lepas", reworked.pendingRework?.reason)
        assertTrue(reworked.handoffsFrom(SamplingPipelineStage.QC_FINISHING).isEmpty())
    }

    @Test
    fun `sendBackForRework to later stage should throw`() {
        assertFailsWith<IllegalArgumentException> {
            order(SamplingPipelineStage.CUCI_SOFTENER).sendBackForRework(
                SamplingPipelineStage.QC_FINISHING, "x", DefectLiability.FACTORY_WORKMANSHIP, "", "", now
            )
        }
    }

    @Test
    fun `sendBackForRework without reason should throw`() {
        assertFailsWith<IllegalArgumentException> {
            order(SamplingPipelineStage.QC_FINISHING).sendBackForRework(
                SamplingPipelineStage.LINKING_ASSEMBLY, " ", DefectLiability.FACTORY_WORKMANSHIP, "", "", now
            )
        }
    }

    @Test
    fun `codec round trip should keep active work and rework audit`() {
        val original = order(SamplingPipelineStage.QC_FINISHING)
            .sendBackForRework(SamplingPipelineStage.SETRIKA_UAP, "Kusut", DefectLiability.FACTORY_WORKMANSHIP, "a", "QC", now)
            .startStageWork("Budi", "b", later)
        val decoded = SamplingOrderCodec.decode(SamplingOrderCodec.encode(original))
        assertNotNull(decoded.currentWork)
        assertEquals("Budi", decoded.currentWork?.operatorName)
        assertEquals(DefectLiability.FACTORY_WORKMANSHIP, decoded.stageHistory.single().liability)
        assertEquals("Kusut", decoded.pendingRework?.reason)
    }

    @Test
    fun `codec decode of legacy payload without active work should default to queue`() {
        val legacy = SamplingOrderCodec.encode(order(SamplingPipelineStage.CUCI_SOFTENER))
        val stripped = com.eventverse.app.shared.json.JsonValue.Obj(legacy.entries - "activeWork")
        assertNull(SamplingOrderCodec.decode(stripped).activeWork)
    }
}

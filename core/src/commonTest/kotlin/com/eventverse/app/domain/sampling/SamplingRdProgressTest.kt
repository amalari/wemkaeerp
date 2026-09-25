package com.eventverse.app.domain.sampling

import com.eventverse.app.domain.pipeline.ModuleArchetype
import com.eventverse.app.domain.process.TenantOptionalProcess
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SamplingRdProgressTest {

    private val now = Instant.fromEpochMilliseconds(1_700_000_000_000)

    private fun order(stage: SamplingPipelineStage) = SamplingOrder(
        id = SamplingOrderId("smp_rd"),
        tenantId = TenantId("demo-tenant"),
        spkNumber = SpkNumber("SPK-SMP-0100"),
        clientName = "BIANCA",
        styleName = "FLORAL CARDIGAN",
        status = SamplingStatus.IN_PROGRESS,
        pipelineStage = stage,
        createdAt = now,
        updatedAt = now
    )

    @Test
    fun `rd progress when order in cuci should mark earlier stages done and cuci active`() {
        val steps = order(SamplingPipelineStage.CUCI_SOFTENER).rdProgress(emptyList())
        assertEquals(RD_STAGES.size, steps.size)
        assertEquals(RdStepState.DONE, steps[0].state)
        assertEquals(RdStepState.DONE, steps[1].state)
        assertEquals(RdStepState.ACTIVE, steps[2].state)
        assertTrue(steps.drop(3).all { it.state == RdStepState.PENDING })
    }

    @Test
    fun `rd progress inserted process should be done only after next mandatory stage reached`() {
        val bordir = TenantOptionalProcess(
            processId = "proc_bordir",
            tenantId = TenantId("demo-tenant"),
            code = "BORDIR",
            displayName = "Bordir",
            archetype = ModuleArchetype.CUSTOM_EXTENSION,
            samplingAnchorAfter = SamplingPipelineStage.LINKING_ASSEMBLY
        )
        val pending = order(SamplingPipelineStage.LINKING_ASSEMBLY).rdProgress(listOf(bordir))
        assertEquals(RdStepState.PENDING, pending.first { it.label == "Bordir" }.state)

        val done = order(SamplingPipelineStage.CUCI_SOFTENER).rdProgress(listOf(bordir))
        assertEquals(RdStepState.DONE, done.first { it.label == "Bordir" }.state)
    }
}

package com.eventverse.app.domain.sampling

import com.eventverse.app.domain.stageflow.StageCode
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.shared.sampling.SamplingOrderCodec
import kotlinx.datetime.Instant
import kotlin.test.*

/** TRD-FLOW-001 Tahap 2 (paket sampling, bagian 2): [SamplingOrder.stageCode] sumber kebenaran. */
class SamplingOrderStageCodeTest {

    private val now = Instant.parse("2026-09-01T00:00:00Z")

    private fun order(stageCode: StageCode = SamplingPipelineStage.NEW_INTAKE.toStageCode()) = SamplingOrder(
        id = SamplingOrderId("smp_code_001"),
        tenantId = TenantId("ten-code"),
        spkNumber = SpkNumber("SPK-SMP-0101"),
        clientName = "Buyer",
        styleName = "Sweater",
        stageCode = stageCode,
        createdAt = now,
        updatedAt = now
    )

    @Test
    fun stageCode_default_shouldBeNewIntakeAndBridgeToEnum() {
        assertEquals(StageCode("NEW_INTAKE"), order().stageCode)
        assertEquals(SamplingPipelineStage.NEW_INTAKE, order().pipelineStage)
    }

    @Test
    fun advance_shouldWriteStageCode() {
        val moved = order().advancePipelineStage(SamplingPipelineStage.FLOW_REVIEW, now)

        assertEquals(StageCode("FLOW_REVIEW"), moved.stageCode)
        assertEquals(SamplingPipelineStage.FLOW_REVIEW, moved.pipelineStage)
    }

    @Test
    fun bridge_withNonKnitCode_shouldFailLoudlyNotFallBack() {
        // Tahap non-rajut belum boleh aktif (constraint TRD-FLOW-001): gagal keras lebih aman
        // daripada diam-diam menampilkan kartu di tahap yang salah.
        val embroidery = order(StageCode("MACHINE_EMBROIDERY"))

        val error = assertFailsWith<IllegalStateException> { embroidery.pipelineStage }
        assertTrue("MACHINE_EMBROIDERY" in (error.message ?: ""))
    }

    @Test
    fun codec_roundTrip_shouldPreserveStageCode() {
        val atLinking = order(SamplingPipelineStage.LINKING_ASSEMBLY.toStageCode())

        assertEquals(atLinking.stageCode, SamplingOrderCodec.decode(SamplingOrderCodec.encode(atLinking)).stageCode)
    }
}

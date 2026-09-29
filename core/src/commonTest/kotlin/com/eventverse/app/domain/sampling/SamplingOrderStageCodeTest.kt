package com.eventverse.app.domain.sampling

import com.eventverse.app.domain.pipeline.code

import com.eventverse.app.domain.pack.GarmentSlots

import com.eventverse.app.domain.pipeline.ModuleArchetype
import com.eventverse.app.domain.stageflow.IndustryStageTemplates
import com.eventverse.app.domain.stageflow.IndustryTemplateCode
import com.eventverse.app.domain.stageflow.StageCode
import com.eventverse.app.domain.stageflow.StageDefinition
import com.eventverse.app.domain.stageflow.StageKind
import com.eventverse.app.domain.stageflow.StageTrait
import com.eventverse.app.domain.stageflow.TenantStageFlow
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
    fun historyRecords_enumConstructor_shouldStoreCodeAndEqualCodeConstructor() {
        val audit = StageTransitionAudit(SamplingPipelineStage.MACHINE_KNITTING, SamplingPipelineStage.LINKING_ASSEMBLY, "a@x", "OPERATOR", now)
        assertEquals(StageCode("MACHINE_KNITTING"), audit.fromCode)
        assertEquals(StageTransitionAudit(StageCode("MACHINE_KNITTING"), StageCode("LINKING_ASSEMBLY"), "a@x", "OPERATOR", now), audit)
        assertEquals(SamplingPipelineStage.LINKING_ASSEMBLY, audit.toStage)

        val claim = StageWorkClaim(SamplingPipelineStage.CAM_PROGRAMMING, "Budi", "b@x", now)
        assertEquals(StageCode("CAM_PROGRAMMING"), claim.stageCode)

        val input = StageWorkInput(SamplingPipelineStage.QC_FINISHING, emptyList())
        assertEquals(StageWorkInput(StageCode("QC_FINISHING"), emptyList()), input)
    }

    @Test
    fun historyRecords_withNonKnitCode_shouldFailLoudlyOnEnumRead() {
        val audit = StageTransitionAudit(StageCode("DIGITIZING"), StageCode("HOOPING"), "a@x", "OPERATOR", now)
        assertFailsWith<IllegalStateException> { audit.fromStage }
        assertFailsWith<IllegalStateException> { StageWorkClaim(StageCode("HOOPING"), "Budi", "b@x", now).stage }
    }

    @Test
    fun revisionSnapshot_shouldCaptureStageCode() {
        val atQc = order(SamplingPipelineStage.QC_FINISHING.toStageCode())
        val revised = atQc.requestRevision("Lengan terlalu panjang", now)

        assertEquals(StageCode("QC_FINISHING"), revised.revisionHistory.last().snapshot?.stageCode)
        assertEquals(StageCode("CAM_PROGRAMMING"), revised.stageCode)
    }

    private val embroideryFlow = TenantStageFlow(
        TenantId("ten-code"),
        IndustryTemplateCode.KNIT_SWEATER,
        listOf(
            StageDefinition(StageCode("NEW_INTAKE"), "SPK Masuk", StageKind.ENTRY_ANCHOR, GarmentSlots.ORDER_INGESTION),
            StageDefinition(StageCode("DIGITIZING"), "Digitizing", StageKind.WORK, GarmentSlots.PRODUCT_ENGINEERING),
            StageDefinition(StageCode("MACHINE_EMBROIDERY"), "Bordir Mesin", StageKind.WORK, GarmentSlots.SEWING, setOf(StageTrait.OPERATOR_DESK)),
            StageDefinition(StageCode("ACC_APPROVED"), "ACC", StageKind.EXIT_ANCHOR, GarmentSlots.FULFILLMENT)
        )
    )

    @Test
    fun stageFrame_whenNotFrozen_shouldBeKnitTemplate() {
        assertEquals(SamplingRoute.DEFAULT_FRAME, order().stageFrame.map { it.code })
        assertEquals("Draft", order().currentStage.shortLabel)
    }

    @Test
    fun freezeStageFlow_shouldBeIdempotentAndDriveRoleLookupsAndRoute() {
        val frozen = order().freezeStageFlow(embroideryFlow)

        assertSame(frozen, frozen.freezeStageFlow(IndustryStageTemplates.instantiate(TenantId("ten-code"), IndustryTemplateCode.KNIT_SWEATER)))
        assertEquals(StageCode("MACHINE_EMBROIDERY"), frozen.firstStageWith(GarmentSlots.SEWING)?.code)
        assertEquals(listOf(StageCode("MACHINE_EMBROIDERY")), frozen.stagesWith(StageTrait.OPERATOR_DESK).map { it.code })
        assertEquals(StageCode("MACHINE_EMBROIDERY"), frozen.samplingRoute.nextAfter(StageCode("DIGITIZING")))
    }

    @Test
    fun codec_roundTrip_shouldPreserveFrozenStageFlow() {
        val frozen = order().freezeStageFlow(embroideryFlow)

        assertEquals(frozen.frozenStageFlow, SamplingOrderCodec.decode(SamplingOrderCodec.encode(frozen)).frozenStageFlow)
        assertNull(SamplingOrderCodec.decode(SamplingOrderCodec.encode(order())).frozenStageFlow)
    }

    @Test
    fun codec_roundTrip_shouldPreserveStageCode() {
        val atLinking = order(SamplingPipelineStage.LINKING_ASSEMBLY.toStageCode())

        assertEquals(atLinking.stageCode, SamplingOrderCodec.decode(SamplingOrderCodec.encode(atLinking)).stageCode)
    }
}

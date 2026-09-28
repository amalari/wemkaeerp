package com.eventverse.app.domain.transfer

import com.eventverse.app.domain.pipeline.ModuleArchetype
import com.eventverse.app.domain.process.TenantOptionalProcess
import com.eventverse.app.domain.sampling.SamplingPipelineStage
import com.eventverse.app.domain.sampling.toStageCode
import com.eventverse.app.domain.stageflow.StageCode
import com.eventverse.app.domain.tenant.TenantId
import kotlin.test.*

/** TRD-FLOW-001 Tahap 2 (paket transfer): simpul tahap dirujuk lewat [StageCode]. */
class FlowNodeRefStageCodeTest {

    @Test
    fun stage_fromEnumAndFromCode_shouldBeEqualWithSameKey() {
        val legacy = FlowNodeRef.Stage(SamplingPipelineStage.MACHINE_KNITTING)
        val coded = FlowNodeRef.Stage(StageCode("MACHINE_KNITTING"))

        assertEquals(legacy, coded)
        assertEquals("STAGE:MACHINE_KNITTING", coded.key)
        assertEquals(SamplingPipelineStage.MACHINE_KNITTING.displayName, coded.displayName)
    }

    @Test
    fun stage_withCodeOutsideEnum_shouldFallBackToCodeAsName() {
        assertEquals("MACHINE_EMBROIDERY", FlowNodeRef.Stage(StageCode("MACHINE_EMBROIDERY")).displayName)
    }

    @Test
    fun parse_shouldStillAcceptOnlyExactEnumNames() {
        assertEquals(FlowNodeRef.Stage(StageCode("CUCI_SOFTENER")), FlowNodeRef.parse("STAGE:CUCI_SOFTENER"))
        // Perilaku lama dipertahankan: alias dan kode di luar enum dibuang, bukan diterima.
        assertNull(FlowNodeRef.parse("STAGE:FINISHING_QC"))
        assertNull(FlowNodeRef.parse("STAGE:MACHINE_EMBROIDERY"))
    }

    @Test
    fun resolveNodes_withCodesOutsideEnum_shouldBuildNodesAndAnchorProcesses() {
        val sablon = TenantOptionalProcess(
            processId = "proc-sablon",
            tenantId = TenantId("ten-x"),
            code = "SABLON",
            displayName = "Sablon",
            archetype = ModuleArchetype.CUSTOM_EXTENSION,
            samplingAnchorAfter = SamplingPipelineStage.CAM_PROGRAMMING.toStageCode()
        )
        val stages = listOf("CAM_PROGRAMMING", "DIGITIZING", "MACHINE_EMBROIDERY").map(::StageCode)

        val nodes = FlowLegDerivation.resolveNodes(stages, listOf(sablon), skipped = setOf(StageCode("DIGITIZING")))

        assertEquals(
            listOf("STAGE:CAM_PROGRAMMING", "PROC:SABLON", "STAGE:MACHINE_EMBROIDERY"),
            nodes.map { it.key }
        )
    }

    @Test
    fun resolveNodes_legacyOverload_shouldMatchCodeOverload() {
        val legacy = SamplingPipelineStage.entries
        val skipped = setOf(SamplingPipelineStage.CUCI_SOFTENER)

        assertEquals(
            FlowLegDerivation.resolveNodes(legacy.map { StageCode(it.name) }, emptyList(), setOf(StageCode("CUCI_SOFTENER"))),
            FlowLegDerivation.resolveNodes(legacy, emptyList(), skipped)
        )
    }
}

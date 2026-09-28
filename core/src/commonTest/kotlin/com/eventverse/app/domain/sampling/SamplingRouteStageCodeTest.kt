package com.eventverse.app.domain.sampling

import com.eventverse.app.domain.stageflow.StageCode
import kotlin.test.*

/** TRD-FLOW-001 Tahap 2 (paket sampling, bagian 1): rute berjalan di atas kerangka [StageCode]. */
class SamplingRouteStageCodeTest {

    private val embroideryFrame = listOf(
        "NEW_INTAKE", "FLOW_REVIEW", "DIGITIZING", "HOOPING", "MACHINE_EMBROIDERY",
        "THREAD_TRIMMING", "QC_FINISHING", "PENGEMASAN", "STORAGE_HOLDING", "IN_DELIVERY", "ACC_APPROVED"
    ).map(::StageCode)

    @Test
    fun defaultFrame_shouldEqualLegacyEnumOrder() {
        assertEquals(SamplingPipelineStage.entries.map { it.name }, SamplingRoute.DEFAULT_FRAME.map { it.value })
        assertEquals(SamplingPipelineStage.entries.toList(), SamplingRoute.FULL.stages)
    }

    @Test
    fun nextAfter_onNonKnitFrame_shouldWalkThatFrameAndHonourSkips() {
        val route = SamplingRoute(skipped = setOf(StageCode("HOOPING")), frame = embroideryFrame)

        assertEquals(StageCode("MACHINE_EMBROIDERY"), route.nextAfter(StageCode("DIGITIZING")))
        assertFalse(StageCode("HOOPING") in route)
        assertFalse(StageCode("MACHINE_KNITTING") in route)
        assertNull(route.nextAfter(StageCode("ACC_APPROVED")))
        assertNull(route.nextAfter(StageCode("MACHINE_KNITTING")))
    }

    @Test
    fun enumBridge_onNonKnitFrame_shouldOnlyExposeStagesThatHaveEnumCounterpart() {
        val route = SamplingRoute(frame = embroideryFrame)

        assertTrue(SamplingPipelineStage.QC_FINISHING in route)
        assertFalse(SamplingPipelineStage.MACHINE_KNITTING in route)
        // Sesudah FLOW_REVIEW adalah DIGITIZING, yang tidak punya padanan enum — jembatan enum tak bisa mewakilinya.
        assertNull(route.nextAfter(SamplingPipelineStage.FLOW_REVIEW))
    }
}

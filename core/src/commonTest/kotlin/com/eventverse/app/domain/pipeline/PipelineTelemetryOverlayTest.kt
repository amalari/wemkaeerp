package com.eventverse.app.domain.pipeline

import com.eventverse.app.domain.rbac.isScopeSupported

import com.eventverse.app.domain.rbac.isFoundation

import com.eventverse.app.domain.rbac.isOperational

import com.eventverse.app.domain.rbac.isGovernance

import com.eventverse.app.domain.rbac.isHierarchical

import com.eventverse.app.domain.rbac.isGlobalOnly

import com.eventverse.app.domain.rbac.category

import com.eventverse.app.domain.rbac.supportedScopes

import com.eventverse.app.domain.rbac.kind

import com.eventverse.app.domain.rbac.scopeCapability

import com.eventverse.app.domain.rbac.iconKey

import com.eventverse.app.domain.rbac.description

import com.eventverse.app.domain.rbac.displayName

import com.eventverse.app.domain.rbac.name

import com.eventverse.app.domain.rbac.code

import com.eventverse.app.domain.pack.GarmentModules

import com.eventverse.app.domain.pack.GarmentBlueprints

import com.eventverse.app.domain.rbac.BusinessModule
import com.eventverse.app.domain.stageflow.StageCode
import com.eventverse.app.shared.json.JsonParser
import com.eventverse.app.shared.pipeline.ModuleTelemetryCodec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** TRD-FLOW-002 Fase 5: angka nyata menimpa seed; tanpa bacaan → ditandai estimasi. */
class PipelineTelemetryOverlayTest {

    private val sampling = ModuleTelemetry(
        GarmentModules.SAMPLING_ORDER, wipPieces = 7, cycleTimeHours = 5.5,
        healthStatus = FlowHealthStatus.BOTTLENECK, stageWip = mapOf(StageCode("HOOPING") to 2)
    )

    @Test
    fun apply_withReading_shouldReplaceSeedNumbers_andMarkOthersEstimate() {
        val snapshot = PipelinePresetFactory.createSnapshot(GarmentBlueprints.DEFAULT)

        val result = PipelineTelemetryOverlay.applyTo(snapshot, listOf(sampling), PipelineSimulationScenario.NORMAL)

        val node = result.nodes.single { it.module == GarmentModules.SAMPLING_ORDER }
        assertEquals(7, node.wipPieces)
        assertEquals(FlowHealthStatus.BOTTLENECK, node.healthStatus)
        assertFalse(node.isTelemetryEstimate)
        val others = result.nodes.filter { it.module != GarmentModules.SAMPLING_ORDER && !it.isBypassed }
        assertTrue(others.isNotEmpty() && others.all { it.isTelemetryEstimate })
        assertTrue(result.nodes.filter { it.isBypassed }.none { it.isTelemetryEstimate }, "node bypass tidak ditandai")
        assertEquals(result.nodes.filterNot { it.isBypassed }.sumOf { it.wipPieces }, result.totalWipPieces, "KPI dihitung ulang")
    }

    @Test
    fun codec_roundTrip_shouldKeepStageWip_andSkipUnknownModules() {
        val encoded = ModuleTelemetryCodec.encode(listOf(sampling)).encode()
            .replace("\"modules\":[", "\"modules\":[{\"module\":\"NOT_A_MODULE\",\"healthStatus\":\"HEALTHY\"},")

        val decoded = ModuleTelemetryCodec.decode(JsonParser.parseObject(encoded))

        assertEquals(listOf(sampling), decoded)
    }
}

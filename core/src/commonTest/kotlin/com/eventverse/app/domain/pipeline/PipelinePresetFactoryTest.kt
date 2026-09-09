package com.eventverse.app.domain.pipeline

import com.eventverse.app.domain.rbac.BusinessModule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PipelinePresetFactoryTest {

    @Test
    fun fobPreset_shouldActivateAllModulesWithoutBypass() {
        val snapshot = PipelinePresetFactory.createSnapshot(GarmentBusinessPreset.FOB_FULL_PACKAGE)

        assertEquals(GarmentBusinessPreset.FOB_FULL_PACKAGE, snapshot.preset)
        assertEquals(9, snapshot.nodes.size)
        assertEquals(9, snapshot.activeModulesCount)
        assertEquals(0, snapshot.bypassedModulesCount)
        assertTrue(snapshot.totalWipPieces > 0)
        assertTrue(snapshot.overallHealthScore in 40..100)

        // Verify key nodes exist
        val inventoryNode = snapshot.nodes.firstOrNull { it.module == BusinessModule.INVENTORY }
        assertNotNull(inventoryNode)
        assertFalse(inventoryNode.isBypassed)
        assertEquals(FlowHealthStatus.HEALTHY, inventoryNode.healthStatus)
    }

    @Test
    fun cmtPreset_shouldBypassTechPackAndInventoryModules() {
        val snapshot = PipelinePresetFactory.createSnapshot(GarmentBusinessPreset.CMT_MAKLOON)

        assertEquals(GarmentBusinessPreset.CMT_MAKLOON, snapshot.preset)
        assertEquals(9, snapshot.nodes.size)
        assertEquals(2, snapshot.bypassedModulesCount)
        assertEquals(7, snapshot.activeModulesCount)

        // Inventory & Tech Pack must be bypassed because buyer supplies them
        val inventoryNode = snapshot.nodes.first { it.module == BusinessModule.INVENTORY }
        assertTrue(inventoryNode.isBypassed)
        assertEquals(FlowHealthStatus.BYPASSED, inventoryNode.healthStatus)
        assertEquals(0, inventoryNode.wipPieces)

        val techPackNode = snapshot.nodes.first { it.module == BusinessModule.TECH_PACK_BOM }
        assertTrue(techPackNode.isBypassed)
        assertEquals(FlowHealthStatus.BYPASSED, techPackNode.healthStatus)
    }

    @Test
    fun brandD2cPreset_shouldHaveActiveInternalEndToEndChain() {
        val snapshot = PipelinePresetFactory.createSnapshot(GarmentBusinessPreset.BRAND_D2C)

        assertEquals(GarmentBusinessPreset.BRAND_D2C, snapshot.preset)
        assertEquals(9, snapshot.nodes.size)
        assertEquals(0, snapshot.bypassedModulesCount)
        assertEquals(9, snapshot.activeModulesCount)

        // Operator sewing must have active WIP
        val operatorNode = snapshot.nodes.first { it.module == BusinessModule.OPERATOR_EXEC }
        assertFalse(operatorNode.isBypassed)
        assertTrue(operatorNode.wipPieces > 0)
    }

    @Test
    fun presetResolution_shouldFallbackToDefaultOnUnknownCode() {
        val resolved = GarmentBusinessPreset.fromCode("unknown_or_null")
        assertEquals(GarmentBusinessPreset.DEFAULT, resolved)

        val cmtResolved = GarmentBusinessPreset.fromCode("cmt_makloon")
        assertEquals(GarmentBusinessPreset.CMT_MAKLOON, cmtResolved)
    }
}

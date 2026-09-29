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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PipelinePresetFactoryTest {

    @Test
    fun fobPreset_shouldActivateAllModulesWithoutBypass() {
        val snapshot = PipelinePresetFactory.createSnapshot(GarmentBlueprints.FOB_FULL_PACKAGE)

        assertEquals(GarmentBlueprints.FOB_FULL_PACKAGE, snapshot.preset)
        assertEquals(9, snapshot.nodes.size)
        assertEquals(9, snapshot.activeModulesCount)
        assertEquals(0, snapshot.bypassedModulesCount)
        assertTrue(snapshot.totalWipPieces > 0)
        assertTrue(snapshot.overallHealthScore in 40..100)

        // Verify key nodes exist
        val inventoryNode = snapshot.nodes.firstOrNull { it.module == GarmentModules.INVENTORY }
        assertNotNull(inventoryNode)
        assertFalse(inventoryNode.isBypassed)
        assertEquals(FlowHealthStatus.HEALTHY, inventoryNode.healthStatus)
    }

    @Test
    fun cmtPreset_shouldBypassTechPackAndInventoryModules() {
        val snapshot = PipelinePresetFactory.createSnapshot(GarmentBlueprints.CMT_MAKLOON)

        assertEquals(GarmentBlueprints.CMT_MAKLOON, snapshot.preset)
        assertEquals(9, snapshot.nodes.size)
        assertEquals(2, snapshot.bypassedModulesCount)
        assertEquals(7, snapshot.activeModulesCount)

        // Inventory & Tech Pack must be bypassed because buyer supplies them
        val inventoryNode = snapshot.nodes.first { it.module == GarmentModules.INVENTORY }
        assertTrue(inventoryNode.isBypassed)
        assertEquals(FlowHealthStatus.BYPASSED, inventoryNode.healthStatus)
        assertEquals(0, inventoryNode.wipPieces)

        val techPackNode = snapshot.nodes.first { it.module == GarmentModules.TECH_PACK_BOM }
        assertTrue(techPackNode.isBypassed)
        assertEquals(FlowHealthStatus.BYPASSED, techPackNode.healthStatus)
    }

    @Test
    fun brandD2cPreset_shouldHaveActiveInternalEndToEndChain() {
        val snapshot = PipelinePresetFactory.createSnapshot(GarmentBlueprints.BRAND_D2C)

        assertEquals(GarmentBlueprints.BRAND_D2C, snapshot.preset)
        assertEquals(9, snapshot.nodes.size)
        assertEquals(0, snapshot.bypassedModulesCount)
        assertEquals(9, snapshot.activeModulesCount)

        // Operator sewing must have active WIP
        val operatorNode = snapshot.nodes.first { it.module == GarmentModules.OPERATOR_EXEC }
        assertFalse(operatorNode.isBypassed)
        assertTrue(operatorNode.wipPieces > 0)
    }

    /** B4d: perilaku lama (kode tak dikenal → FOB) sengaja diganti — menebak model bisnis tenant mengubah datanya. */
    @Test
    fun presetResolution_shouldRejectUnknownCode_notFallBackToFob() {
        kotlin.test.assertFailsWith<IllegalArgumentException> { GarmentBlueprints.parse("unknown_or_null") }
        assertEquals(null, GarmentBlueprints.findByCode("unknown_or_null"))
        assertEquals(GarmentBlueprints.CMT_MAKLOON, GarmentBlueprints.parse("cmt_makloon"))
        assertEquals(GarmentBlueprints.CMT_MAKLOON, GarmentBlueprints.parse("CMT_MAKLOON"), "tidak peka huruf besar, seperti sebelumnya")
    }
}

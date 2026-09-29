package com.eventverse.app.domain.contracts

import com.eventverse.app.domain.rbac.isScopeSupported

import com.eventverse.app.domain.rbac.isFoundation

import com.eventverse.app.domain.rbac.isOperational

import com.eventverse.app.domain.rbac.isGovernance

import com.eventverse.app.domain.rbac.isHierarchical

import com.eventverse.app.domain.rbac.isGlobalOnly


import com.eventverse.app.domain.rbac.supportedScopes

import com.eventverse.app.domain.rbac.kind

import com.eventverse.app.domain.rbac.scopeCapability

import com.eventverse.app.domain.rbac.iconKey

import com.eventverse.app.domain.rbac.description

import com.eventverse.app.domain.rbac.displayName

import com.eventverse.app.domain.rbac.name

import com.eventverse.app.domain.rbac.code

import com.eventverse.app.domain.pack.GarmentModules

import com.eventverse.app.domain.pipeline.defaultProducedOutputType

import com.eventverse.app.domain.pipeline.defaultExpectedInputType

import com.eventverse.app.domain.pack.GarmentSlots

import com.eventverse.app.domain.pack.GarmentPortTypes

import com.eventverse.app.domain.pipeline.CustomPipelineEdge
import com.eventverse.app.domain.pipeline.CustomPipelineNode
import com.eventverse.app.domain.pipeline.CustomTenantPipeline
import com.eventverse.app.domain.pipeline.ModuleArchetype
import com.eventverse.app.domain.rbac.BusinessModule
import com.eventverse.app.domain.tenant.TenantId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PortCompatibilityTest {

    @Test
    fun `module archetype slot for tech pack is PRODUCT_ENGINEERING`() {
        val archetype = GarmentSlots.forModule(GarmentModules.TECH_PACK_BOM)
        assertEquals(GarmentSlots.PRODUCT_ENGINEERING, archetype)
        assertEquals(GarmentPortTypes.APPROVED_SAMPLE_SPECIFICATION.value, archetype?.defaultExpectedInputType)
        assertEquals(GarmentPortTypes.TECH_PACK_AND_YIELD_DATA.value, archetype?.defaultProducedOutputType)
    }

    @Test
    fun `direct port match is compatible`() {
        val isCompat = PortCompatibility.isCompatible(
            producedType = GarmentPortTypes.APPROVED_SAMPLE_SPECIFICATION.value,
            acceptedTypes = listOf(GarmentPortTypes.APPROVED_SAMPLE_SPECIFICATION.value)
        )
        assertTrue(isCompat)
    }

    @Test
    fun `bypass adapter connects sample spec to tech pack and yield data`() {
        val isConnectable = PortCompatibility.isConnectable(
            producedType = GarmentPortTypes.APPROVED_SAMPLE_SPECIFICATION.value,
            acceptedTypes = listOf(GarmentPortTypes.TECH_PACK_AND_YIELD_DATA.value)
        )
        assertTrue(isConnectable)

        val adapter = PortCompatibility.adapterPathFor(
            from = GarmentPortTypes.APPROVED_SAMPLE_SPECIFICATION.value,
            to = GarmentPortTypes.TECH_PACK_AND_YIELD_DATA.value
        )
        assertEquals(SampleSpecToTechPackAdapter.descriptor, adapter)
    }

    @Test
    fun `incompatible ports without adapter produce BLOCKING mismatch`() {
        val node1 = CustomPipelineNode(
            nodeId = "node-costing",
            moduleId = "costing_hpp",
            customDisplayName = "Costing",
            archetype = GarmentSlots.COSTING_HPP
        )
        val node2 = CustomPipelineNode(
            nodeId = "node-engineering",
            moduleId = "tech_pack_bom",
            customDisplayName = "Tech Pack",
            archetype = GarmentSlots.PRODUCT_ENGINEERING
        )
        val edge = CustomPipelineEdge(
            edgeId = "edge-1",
            fromNodeId = "node-costing",
            toNodeId = "node-engineering",
            expectedDataType = "StandardHandoffPayload"
        )

        val pipeline = CustomTenantPipeline(
            tenantId = TenantId("tenant-1"),
            pipelineName = "Test Pipeline",
            nodes = listOf(node1, node2),
            edges = listOf(edge)
        )

        val mismatches = PortCompatibility.validate(pipeline)
        assertEquals(1, mismatches.size)
        assertEquals(PortMismatchSeverity.BLOCKING, mismatches.first().severity)
    }
}

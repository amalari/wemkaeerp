package com.eventverse.app.domain.contracts

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
        val archetype = ModuleArchetype.forModule(BusinessModule.TECH_PACK_BOM)
        assertEquals(ModuleArchetype.PRODUCT_ENGINEERING, archetype)
        assertEquals(PortDataTypeRegistry.APPROVED_SAMPLE_SPECIFICATION, archetype?.defaultExpectedInputType)
        assertEquals(PortDataTypeRegistry.TECH_PACK_AND_YIELD_DATA, archetype?.defaultProducedOutputType)
    }

    @Test
    fun `direct port match is compatible`() {
        val isCompat = PortCompatibility.isCompatible(
            producedType = PortDataTypeRegistry.APPROVED_SAMPLE_SPECIFICATION,
            acceptedTypes = listOf(PortDataTypeRegistry.APPROVED_SAMPLE_SPECIFICATION)
        )
        assertTrue(isCompat)
    }

    @Test
    fun `bypass adapter connects sample spec to tech pack and yield data`() {
        val isConnectable = PortCompatibility.isConnectable(
            producedType = PortDataTypeRegistry.APPROVED_SAMPLE_SPECIFICATION,
            acceptedTypes = listOf(PortDataTypeRegistry.TECH_PACK_AND_YIELD_DATA)
        )
        assertTrue(isConnectable)

        val adapter = PortCompatibility.adapterPathFor(
            from = PortDataTypeRegistry.APPROVED_SAMPLE_SPECIFICATION,
            to = PortDataTypeRegistry.TECH_PACK_AND_YIELD_DATA
        )
        assertEquals(SampleSpecToTechPackAdapter.descriptor, adapter)
    }

    @Test
    fun `incompatible ports without adapter produce BLOCKING mismatch`() {
        val node1 = CustomPipelineNode(
            nodeId = "node-costing",
            moduleId = "costing_hpp",
            customDisplayName = "Costing",
            archetype = ModuleArchetype.COSTING_HPP
        )
        val node2 = CustomPipelineNode(
            nodeId = "node-engineering",
            moduleId = "tech_pack_bom",
            customDisplayName = "Tech Pack",
            archetype = ModuleArchetype.PRODUCT_ENGINEERING
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

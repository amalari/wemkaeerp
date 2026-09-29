package com.eventverse.app.domain.pipeline

import com.eventverse.app.domain.pipeline.defaultExpectedInputType

import com.eventverse.app.domain.pack.GarmentSlots

import com.eventverse.app.domain.pack.GarmentPhases

import com.eventverse.app.domain.tenant.TenantId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TenantPipelineProjectorTest {

    private val tenantId = TenantId("ten-projector-test")

    private fun fobPipeline() =
        CustomTenantPipeline.fromPreset(tenantId, GarmentBusinessPreset.FOB_FULL_PACKAGE)

    @Test
    fun project_shouldRenderPersistedTopologyNotPresetDefaults() {
        val pipeline = fobPipeline().renameModuleByCode("inventory", "Gudang Kain Roll Impor")

        val snapshot = TenantPipelineProjector.project(pipeline)

        assertTrue(
            snapshot.nodes.any { it.title == "Gudang Kain Roll Impor" },
            "Nama modul dari data tenant harus muncul di kanvas"
        )
        assertFalse(
            snapshot.nodes.any { it.title == "Bahan Baku & Stok Kain" },
            "Nama bawaan preset tidak boleh menimpa nama tenant"
        )
    }

    @Test
    fun project_bypassedNode_shouldRenderAsBypassedWithNoWip() {
        val inventoryNodeId = fobPipeline().nodes.first { it.moduleId == "inventory" }.nodeId
        val pipeline = fobPipeline().setNodeBypassed(inventoryNodeId, true)

        val snapshot = TenantPipelineProjector.project(pipeline)
        val projected = snapshot.nodes.first { it.id == inventoryNodeId }

        assertEquals(FlowHealthStatus.BYPASSED, projected.healthStatus)
        assertEquals(0, projected.wipPieces, "Modul yang di-bypass tidak boleh membawa WIP")
        assertEquals(1, snapshot.bypassedModulesCount)
        assertEquals(8, snapshot.activeModulesCount)
    }

    @Test
    fun project_moduleReEnabledByTenant_shouldNotStayGreyedOut() {
        // CMT bypasses raw material by default. A tenant that switches it back on must see it
        // as active, not permanently greyed out by the preset's own default.
        val cmt = CustomTenantPipeline.fromPreset(tenantId, GarmentBusinessPreset.CMT_MAKLOON)
        val bypassedNode = cmt.nodes.first { it.isBypassed }

        val reEnabled = cmt.setNodeBypassed(bypassedNode.nodeId, false)
        val projected = TenantPipelineProjector.project(reEnabled)
            .nodes
            .first { it.id == bypassedNode.nodeId }

        assertEquals(FlowHealthStatus.HEALTHY, projected.healthStatus)
        assertFalse(projected.isBypassed)
    }

    @Test
    fun project_customPluginNode_shouldBeSynthesizedFromArchetype() {
        val descriptor = DynamicModuleDescriptor(
            moduleId = "sablon_bordir_custom",
            archetype = GarmentSlots.FINISHING,
            name = "Sablon Manual & Bordir Komputer",
            description = "Stasiun sablon dan bordir khusus.",
            acceptedInputDataTypes = setOf("CutPiecesBundle"),
            producedOutputDataType = "DecoratedGarmentBundle",
            isCustomTenantPlugin = true
        )
        val pipeline = fobPipeline().addNode(descriptor.toPipelineNode(nodeId = "custom-sablon"))

        val projected = TenantPipelineProjector.project(pipeline)
            .nodes
            .first { it.id == "custom-sablon" }

        assertEquals("Sablon Manual & Bordir Komputer", projected.title)
        assertEquals(GarmentPhases.MANUFACTURING, projected.stage)
        assertEquals("sablon_bordir_custom", projected.customModuleCode)
        assertTrue(projected.isCustomPlugin)
        assertEquals(GarmentSlots.FINISHING.defaultExpectedInputType, projected.inputContract)
    }

    @Test
    fun project_shouldExposeTenantFormulaParametersOnTheNode() {
        val costingNodeId = fobPipeline().nodes.first { it.moduleId == "costing_hpp" }.nodeId
        val parameters = mapOf("marginPercent" to "18.5", "overheadPerPcsIdr" to "3500")
        val pipeline = fobPipeline().updateNodeFormulaParameters(costingNodeId, parameters)

        val projected = TenantPipelineProjector.project(pipeline)
            .nodes
            .first { it.id == costingNodeId }

        assertEquals(parameters, projected.formulaParameters)
    }

    @Test
    fun project_shouldDeriveDownstreamHandoffsFromPersistedEdges() {
        val pipeline = fobPipeline()
        val crmNode = pipeline.nodes.first { it.moduleId == "crm_sales" }
        val expectedTargets = pipeline.edges
            .filter { it.fromNodeId == crmNode.nodeId && !it.isFeedbackReworkLoop }
            .mapNotNull { edge -> pipeline.findNode(edge.toNodeId)?.moduleId }

        val projected = TenantPipelineProjector.project(pipeline)
            .nodes
            .first { it.id == crmNode.nodeId }

        assertEquals(expectedTargets, projected.downstreamModuleCodes)
    }

    @Test
    fun project_shouldOrderNodesByPersistedStepIndex() {
        val pipeline = fobPipeline()

        val snapshot = TenantPipelineProjector.project(pipeline)

        assertEquals(
            pipeline.orderedNodes.map { it.nodeId },
            snapshot.nodes.map { it.id }
        )
        assertEquals(
            (1..pipeline.nodes.size).toList(),
            snapshot.nodes.map { it.stepNumber },
            "Nomor langkah harus berurutan mengikuti urutan tenant"
        )
    }

    @Test
    fun project_withoutBasePreset_shouldStillRenderUsingDefault() {
        val pipeline = fobPipeline().copy(baseStarterPreset = null)

        val snapshot = TenantPipelineProjector.project(pipeline)

        assertEquals(GarmentBusinessPreset.DEFAULT, snapshot.preset)
        assertEquals(pipeline.nodes.size, snapshot.nodes.size)
    }

    @Test
    fun project_scenarioChange_shouldAffectLeadTimeMetrics() {
        val pipeline = fobPipeline()

        val normal = TenantPipelineProjector.project(pipeline, PipelineSimulationScenario.NORMAL)
        val defect = TenantPipelineProjector.project(
            pipeline,
            PipelineSimulationScenario.QC_FABRIC_DEFECT
        )

        assertTrue(
            defect.avgLeadTimeDays > normal.avgLeadTimeDays,
            "Skenario cacat kain harus memperpanjang lead time"
        )
        assertTrue(defect.overallHealthScore <= normal.overallHealthScore)
    }

    @Test
    fun project_twoTenantsFromSamePreset_shouldRenderDifferently() {
        // The core promise: one codebase, one preset, two different factories.
        val fullFactory = fobPipeline()
        val leanFactory = fobPipeline()
            .let { pipeline ->
                val inventory = pipeline.nodes.first { it.moduleId == "inventory" }
                pipeline.setNodeBypassed(inventory.nodeId, true)
            }
            .renameModuleByCode("operator_exec", "Jahit Borongan Rumahan")

        val full = TenantPipelineProjector.project(fullFactory)
        val lean = TenantPipelineProjector.project(leanFactory)

        assertEquals(9, full.activeModulesCount)
        assertEquals(8, lean.activeModulesCount)
        assertNotNull(lean.nodes.firstOrNull { it.title == "Jahit Borongan Rumahan" })
        assertTrue(full.nodes.none { it.title == "Jahit Borongan Rumahan" })
    }
}

private fun CustomTenantPipeline.renameModuleByCode(
    moduleCode: String,
    displayName: String
): CustomTenantPipeline {
    val node = nodes.first { it.moduleId == moduleCode }
    return renameNode(node.nodeId, displayName)
}

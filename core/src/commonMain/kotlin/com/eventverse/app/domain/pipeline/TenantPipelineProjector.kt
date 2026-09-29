package com.eventverse.app.domain.pipeline

/**
 * Projects a tenant's persisted [CustomTenantPipeline] onto a renderable
 * [FactoryPipelineSnapshot].
 *
 * The persisted graph is the authority on **which** modules exist, what the tenant calls
 * them, their order, and whether they are bypassed. The preset built by
 * [PipelinePresetFactory] supplies only the operational detail a topology cannot carry —
 * live WIP counts, cycle times, input ports, departments, contract labels.
 *
 * This is what lets one tenant run nine modules under their own names while another runs
 * five, from the same codebase and the same canvas, driven purely by database rows.
 */
object TenantPipelineProjector {

    fun project(
        pipeline: CustomTenantPipeline,
        scenario: PipelineSimulationScenario = PipelineSimulationScenario.NORMAL
    ): FactoryPipelineSnapshot {
        val preset = pipeline.baseStarterPreset ?: GarmentBusinessPreset.DEFAULT
        val template = PipelinePresetFactory.createSnapshot(preset, scenario)
        val templateByModuleCode = template.nodes.associateBy { it.module.code }

        val moduleCodeByNodeId = pipeline.nodes.associate { it.nodeId to it.moduleId }
        val downstreamByNodeId = pipeline.edges
            .filterNot { it.isFeedbackReworkLoop }
            .groupBy { it.fromNodeId }
            .mapValues { (_, edges) -> edges.mapNotNull { moduleCodeByNodeId[it.toNodeId] }.distinct() }

        val projectedNodes = pipeline.orderedNodes.mapIndexed { index, node ->
            val templateNode = templateByModuleCode[node.moduleId]
            val downstream = downstreamByNodeId[node.nodeId] ?: emptyList()
            if (templateNode != null) {
                overlayOnTemplate(templateNode, node, index + 1, downstream)
            } else {
                synthesizeCustomNode(node, index + 1, downstream)
            }
        }

        return buildSnapshot(preset, projectedNodes, scenario)
    }

    /**
     * Applies the tenant's overrides on top of the preset node. Health status is derived
     * from the persisted bypass flag in both directions: a module the tenant switched back
     * on must not stay greyed out just because the preset bypasses it by default.
     */
    private fun overlayOnTemplate(
        templateNode: PipelineNode,
        node: CustomPipelineNode,
        stepNumber: Int,
        downstreamModuleCodes: List<String>
    ): PipelineNode {
        val healthStatus = when {
            node.isBypassed -> FlowHealthStatus.BYPASSED
            templateNode.healthStatus == FlowHealthStatus.BYPASSED -> FlowHealthStatus.HEALTHY
            else -> templateNode.healthStatus
        }
        val healthMessage = when {
            node.isBypassed -> "Modul dinonaktifkan (bypass) pada konfigurasi alur tenant ini."
            templateNode.healthStatus == FlowHealthStatus.BYPASSED ->
                "Modul diaktifkan tenant ini di luar konfigurasi standar preset."
            else -> templateNode.healthMessage
        }

        return templateNode.copy(
            title = node.customDisplayName,
            stepNumber = stepNumber,
            healthStatus = healthStatus,
            healthMessage = healthMessage,
            // A bypassed module holds no work in progress.
            wipPieces = if (node.isBypassed) 0 else templateNode.wipPieces,
            downstreamModuleCodes = downstreamModuleCodes.ifEmpty { templateNode.downstreamModuleCodes },
            formulaParameters = node.customFormulaParameters,
            isNewFromCatalog = node.isBypassed && node.nodeId.startsWith(PipelineCatalogReconciler.NODE_ID_PREFIX)
        )
    }

    /**
     * Renders a node that has no preset counterpart — a custom tenant plugin, or a standard
     * module the tenant added into a preset that does not normally include it. Detail comes
     * from the archetype contract, which is exactly what capability slots are for.
     */
    private fun synthesizeCustomNode(
        node: CustomPipelineNode,
        stepNumber: Int,
        downstreamModuleCodes: List<String>
    ): PipelineNode {
        val archetype = node.archetype
        val stage = archetype.canvasPhase
        return PipelineNode(
            id = node.nodeId,
            module = node.standardModule ?: archetype.representativeModule,
            stage = stage,
            stepNumber = stepNumber,
            title = node.customDisplayName,
            description = if (node.isCustomPlugin) {
                "Modul kustom tenant pada slot ${archetype.displayName}."
            } else {
                archetype.displayName
            },
            assignedDepartment = "Belum Ditugaskan",
            deptColorHex = stage.colorHex,
            inputContract = archetype.defaultExpectedInputType,
            outputContract = archetype.defaultProducedOutputType,
            wipPieces = 0,
            cycleTimeHours = 0.0,
            healthStatus = if (node.isBypassed) FlowHealthStatus.BYPASSED else FlowHealthStatus.HEALTHY,
            healthMessage = if (node.isBypassed) {
                "Modul kustom dinonaktifkan (bypass)."
            } else {
                "Modul kustom aktif; metrik operasional belum tersambung."
            },
            downstreamModuleCodes = downstreamModuleCodes,
            customModuleCode = if (node.isCustomPlugin) node.moduleId else null,
            formulaParameters = node.customFormulaParameters
        )
    }

    /** KPI agregat — rumus tunggal di [PipelinePresetFactory.snapshotOf]. */
    private fun buildSnapshot(
        preset: GarmentBusinessPreset,
        nodes: List<PipelineNode>,
        scenario: PipelineSimulationScenario
    ): FactoryPipelineSnapshot = PipelinePresetFactory.snapshotOf(preset, nodes, scenario)
}

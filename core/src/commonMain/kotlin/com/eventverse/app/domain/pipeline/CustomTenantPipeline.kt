package com.eventverse.app.domain.pipeline

import com.eventverse.app.domain.pack.GarmentSlots

import com.eventverse.app.domain.rbac.BusinessModule
import com.eventverse.app.domain.tenant.TenantId

/**
 * Dynamic tenant pipeline graph representing a custom, "puzzled" workflow.
 * Tenants can design, rewire, add, or swap module nodes without modifying codebase enums.
 */
data class CustomTenantPipeline(
    val tenantId: TenantId,
    val pipelineName: String,
    val baseStarterPreset: GarmentBusinessPreset? = null,
    val nodes: List<CustomPipelineNode>,
    val edges: List<CustomPipelineEdge>
) {
    /** Nodes in execution order, as the tenant arranged them. */
    val orderedNodes: List<CustomPipelineNode>
        get() = nodes.sortedBy { it.stepOrderIndex }

    val activeNodes: List<CustomPipelineNode> get() = nodes.filterNot { it.isBypassed }

    val bypassedNodes: List<CustomPipelineNode> get() = nodes.filter { it.isBypassed }

    val customPluginNodes: List<CustomPipelineNode> get() = nodes.filter { it.isCustomPlugin }

    /**
     * True when the graph carries no operational node. A tenant row can legitimately
     * exist in this state (e.g. seeded before its topology was written), and callers
     * must treat it as "needs provisioning" rather than as a valid empty workflow.
     */
    val isEmpty: Boolean get() = nodes.isEmpty()

    fun findNode(nodeId: String): CustomPipelineNode? = nodes.firstOrNull { it.nodeId == nodeId }

    /** Renames one module for this tenant only, leaving every other tenant untouched. */
    fun renameNode(nodeId: String, newDisplayName: String): CustomTenantPipeline {
        require(newDisplayName.isNotBlank()) { "Module display name cannot be blank" }
        requireNotNull(findNode(nodeId)) { "Node not found in pipeline: $nodeId" }
        return copy(nodes = nodes.map { if (it.nodeId == nodeId) it.rename(newDisplayName) else it })
    }

    /** Switches a module on or off without severing the surrounding wiring. */
    fun setNodeBypassed(nodeId: String, isBypassed: Boolean): CustomTenantPipeline {
        requireNotNull(findNode(nodeId)) { "Node not found in pipeline: $nodeId" }
        return copy(nodes = nodes.map { if (it.nodeId == nodeId) it.withBypassed(isBypassed) else it })
    }

    /** Overrides tenant-specific calculation parameters (sewing tariff, secret margin, …). */
    fun updateNodeFormulaParameters(
        nodeId: String,
        parameters: Map<String, String>
    ): CustomTenantPipeline {
        requireNotNull(findNode(nodeId)) { "Node not found in pipeline: $nodeId" }
        return copy(
            nodes = nodes.map {
                if (it.nodeId == nodeId) it.copy(customFormulaParameters = parameters) else it
            }
        )
    }

    /** Appends a module (standard or custom plugin) at the end of the flow. */
    fun addNode(node: CustomPipelineNode): CustomTenantPipeline {
        require(findNode(node.nodeId) == null) { "Duplicate node ID: ${node.nodeId}" }
        val nextIndex = (nodes.maxOfOrNull { it.stepOrderIndex } ?: 0) + 1
        return copy(nodes = nodes + node.copy(stepOrderIndex = nextIndex))
    }

    /** Removes a module and every edge that referenced it, keeping the graph consistent. */
    fun removeNode(nodeId: String): CustomTenantPipeline = copy(
        nodes = nodes.filterNot { it.nodeId == nodeId },
        edges = edges.filterNot { it.fromNodeId == nodeId || it.toNodeId == nodeId }
    )

    fun connect(edge: CustomPipelineEdge): CustomTenantPipeline {
        requireNotNull(findNode(edge.fromNodeId)) { "Unknown source node: ${edge.fromNodeId}" }
        requireNotNull(findNode(edge.toNodeId)) { "Unknown target node: ${edge.toNodeId}" }
        require(edges.none { it.edgeId == edge.edgeId }) { "Duplicate edge ID: ${edge.edgeId}" }
        return copy(edges = edges + edge)
    }

    fun rename(newName: String): CustomTenantPipeline {
        require(newName.isNotBlank()) { "Pipeline name cannot be blank" }
        return copy(pipelineName = newName)
    }

    companion object {
        fun fromPreset(tenantId: TenantId, preset: GarmentBusinessPreset): CustomTenantPipeline {
            val snapshot = PipelinePresetFactory.createSnapshot(preset)
            val customNodes = snapshot.nodes.map { node ->
                CustomPipelineNode(
                    nodeId = node.id,
                    moduleId = node.module.code,
                    customDisplayName = node.title,
                    // forModuleCode, bukan forModule: ia total dan jatuh ke CUSTOM_EXTENSION.
                    // Node preset selalu operasional, jadi hasilnya identik — yang berubah hanya
                    // bahwa penambahan modul non-operasional tidak lagi memaksa perubahan di sini.
                    archetype = GarmentSlots.forModuleCode(node.module.code),
                    isBypassed = node.isBypassed,
                    stepOrderIndex = node.stepNumber,
                    customFormulaParameters = emptyMap()
                )
            }

            val nodesByModuleCode = snapshot.nodes.associateBy { it.module.code }
            val customEdges = snapshot.nodes.flatMap { sourceNode ->
                sourceNode.downstreamModuleCodes.mapNotNull { targetCode ->
                    nodesByModuleCode[targetCode]?.let { targetNode ->
                        CustomPipelineEdge(
                            edgeId = "edge-${sourceNode.id}-to-${targetNode.id}",
                            fromNodeId = sourceNode.id,
                            toNodeId = targetNode.id,
                            expectedDataType = "StandardHandoffPayload"
                        )
                    }
                }
            }

            // Rework/defect feedback routes are part of the tenant topology, not decoration:
            // persist them so a restored graph still knows where rejects flow back to.
            val feedbackEdges = snapshot.nodes.flatMap { sourceNode ->
                sourceNode.feedbackRoutes.mapNotNull { route ->
                    nodesByModuleCode[route.targetModuleCode]?.let { targetNode ->
                        CustomPipelineEdge(
                            edgeId = "rework-${sourceNode.id}-to-${targetNode.id}",
                            fromNodeId = sourceNode.id,
                            toNodeId = targetNode.id,
                            expectedDataType = "DefectReworkPayload",
                            isFeedbackReworkLoop = true
                        )
                    }
                }
            }

            return CustomTenantPipeline(
                tenantId = tenantId,
                pipelineName = "Alur Operasional ${preset.shortBadge}",
                baseStarterPreset = preset,
                nodes = customNodes,
                edges = (customEdges + feedbackEdges).distinctBy { it.edgeId }
            )
        }
    }
}

data class CustomPipelineNode(
    val nodeId: String,
    val moduleId: String,
    val customDisplayName: String,
    val archetype: ModuleArchetype,
    val isBypassed: Boolean = false,
    val stepOrderIndex: Int = 0,
    val customFormulaParameters: Map<String, String> = emptyMap(),
    /** True when this node comes from a tenant/third-party plugin rather than a built-in module. */
    val isCustomPlugin: Boolean = false,
    /** Opaque JSON config schema for a custom plugin; passed through untouched. */
    val configSchemaJson: String? = null
) {
    init {
        require(nodeId.isNotBlank()) { "CustomPipelineNode.nodeId cannot be blank" }
        require(moduleId.isNotBlank()) { "CustomPipelineNode.moduleId cannot be blank" }
    }

    /** The built-in module this node maps to, or null when it is a custom plugin. */
    val standardModule: BusinessModule?
        get() = BusinessModule.entries.firstOrNull { it.code == moduleId }

    fun rename(newDisplayName: String): CustomPipelineNode {
        require(newDisplayName.isNotBlank()) { "Module display name cannot be blank" }
        return copy(customDisplayName = newDisplayName)
    }

    fun withBypassed(isBypassed: Boolean): CustomPipelineNode = copy(isBypassed = isBypassed)

    fun formulaParameter(key: String): String? = customFormulaParameters[key]
}

data class CustomPipelineEdge(
    val edgeId: String,
    val fromNodeId: String,
    val toNodeId: String,
    val expectedDataType: String,
    val isFeedbackReworkLoop: Boolean = false
)

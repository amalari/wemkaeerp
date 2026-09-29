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

/**
 * What an edge represents: the normal forward hand-off between modules, or a conditional
 * branch (a rework loop, an exception path) declared via [PipelineNode.conditionalPaths].
 */
enum class PipelineEdgeKind { FORWARD, CONDITIONAL }

/**
 * A resolved connection between two pipeline nodes.
 *
 * Unlike the raw string references stored on [PipelineNode] and [PipelineInputPort],
 * an edge is fully resolved: both endpoints are guaranteed to exist in the same
 * [PipelineGraph.nodes] set, so the UI can safely draw it.
 */
data class PipelineEdge(
    val id: String,
    val fromNodeId: String,
    val toNodeId: String,
    /** The destination IN port, or empty when the edge only comes from `downstreamModuleCodes`. */
    val toPortId: String,
    val label: String,
    val edgeType: PipelineEdgeType = PipelineEdgeType.FORWARD,
    val kind: PipelineEdgeKind = if (edgeType.isFeedback) PipelineEdgeKind.CONDITIONAL else PipelineEdgeKind.FORWARD
) {
    /** True when no declared input port claims this edge, so it has no specific landing point. */
    val isImplicit: Boolean get() = toPortId.isEmpty()
    val isFeedback: Boolean get() = edgeType.isFeedback
}

/**
 * A directed graph view over a set of [PipelineNode]s, built by resolving the module-code
 * references that the nodes carry into real node-to-node edges, plus a column assignment
 * derived from actual dependencies rather than from phase ordering.
 *
 * Build it with [from]; nodes are never mutated.
 */
data class PipelineGraph(
    val nodes: List<PipelineNode>,
    val edges: List<PipelineEdge>,
    /** Nodes grouped into columns; index is the column, left to right. */
    val layers: List<List<PipelineNode>>,
    /** Automated input ports whose source module is not present in [nodes]. */
    val danglingInputPortIds: Set<String>,
    /** True when dependency layering had to fall back to stage order because of a cycle. */
    val hasCycle: Boolean
) {
    private val layerByNodeId: Map<String, Int> =
        layers.flatMapIndexed { layer, nodesInLayer -> nodesInLayer.map { it.id to layer } }.toMap()

    val edgesByFromNodeId: Map<String, List<PipelineEdge>> = edges.groupBy { it.fromNodeId }
    val edgesByToNodeId: Map<String, List<PipelineEdge>> = edges.groupBy { it.toNodeId }

    fun layerOf(nodeId: String): Int = layerByNodeId[nodeId] ?: 0

    /** Every node directly upstream or downstream of [nodeId]. */
    fun neighborIdsOf(nodeId: String): Set<String> {
        val upstream = edgesByToNodeId[nodeId].orEmpty().map { it.fromNodeId }
        val downstream = edgesByFromNodeId[nodeId].orEmpty().map { it.toNodeId }
        return (upstream + downstream).toSet()
    }

    /** Edges that touch [nodeId] on either end. */
    fun edgesTouching(nodeId: String): Set<String> =
        edges.filter { it.fromNodeId == nodeId || it.toNodeId == nodeId }.map { it.id }.toSet()

    companion object {
        fun from(nodes: List<PipelineNode>): PipelineGraph {
            if (nodes.isEmpty()) {
                return PipelineGraph(
                    nodes = emptyList(),
                    edges = emptyList(),
                    layers = emptyList(),
                    danglingInputPortIds = emptySet(),
                    hasCycle = false
                )
            }

            // Module codes are unique within a preset, so this resolves references unambiguously.
            val nodeIdByModuleCode = nodes.associate { it.module.code to it.id }
            val nodeById = nodes.associateBy { it.id }

            val edges = mutableListOf<PipelineEdge>()
            val seenPairs = mutableSetOf<Pair<String, String>>()
            val dangling = mutableSetOf<String>()

            // Primary source of truth: declared input ports. Only these carry a landing port id.
            nodes.forEach { target ->
                target.inputs.filter { it.isAutomated }.forEach { port ->
                    val sourceCode = port.sourceModuleCode
                    val sourceId = sourceCode?.let { nodeIdByModuleCode[it] }
                    if (sourceId == null || sourceId == target.id) {
                        dangling += port.id
                    } else {
                        edges += PipelineEdge(
                            id = "${sourceId}->${target.id}:${port.id}",
                            fromNodeId = sourceId,
                            toNodeId = target.id,
                            toPortId = port.id,
                            label = port.sourceOutputContract
                                ?: nodeById[sourceId]?.outputContract.orEmpty()
                        )
                        seenPairs += sourceId to target.id
                    }
                }
            }

            // Secondary: downstream declarations that no input port already claims.
            nodes.forEach { source ->
                source.downstreamModuleCodes.forEach { code ->
                    val targetId = nodeIdByModuleCode[code]
                    if (targetId != null &&
                        targetId != source.id &&
                        (source.id to targetId) !in seenPairs
                    ) {
                        edges += PipelineEdge(
                            id = "${source.id}->$targetId:implicit",
                            fromNodeId = source.id,
                            toNodeId = targetId,
                            toPortId = "",
                            label = source.outputContract
                        )
                        seenPairs += source.id to targetId
                    }
                }
            }

            // Conditional branches (rework loops, exceptions): resolved the same way, but kept
            // out of seenPairs and out of layering — see assignLayers — so a branch back to an
            // earlier module never distorts the main flow's column order or reads as a cycle.
            nodes.forEach { source ->
                source.conditionalPaths.forEach { path ->
                    val targetId = nodeIdByModuleCode[path.targetModuleCode]
                    if (targetId != null && targetId != source.id) {
                        edges += PipelineEdge(
                            id = "${source.id}->$targetId:conditional:${path.targetModuleCode}",
                            fromNodeId = source.id,
                            toNodeId = targetId,
                            toPortId = "",
                            label = path.label,
                            edgeType = PipelineEdgeType.CONDITIONAL_BRANCH,
                            kind = PipelineEdgeKind.CONDITIONAL
                        )
                    }
                }
            }

            // Feedback & Exception Routes (QC Failure -> Supply Chain / Warehouse, or Rework station)
            nodes.forEach { source ->
                source.feedbackRoutes.filter { it.isActive }.forEach { route ->
                    val targetId = nodeIdByModuleCode[route.targetModuleCode]
                    if (targetId != null && targetId != source.id) {
                        edges += PipelineEdge(
                            id = "${source.id}->$targetId:feedback:${route.id}",
                            fromNodeId = source.id,
                            toNodeId = targetId,
                            toPortId = route.targetPortId.orEmpty(),
                            label = route.actionContract,
                            edgeType = route.edgeType,
                            kind = PipelineEdgeKind.CONDITIONAL
                        )
                    }
                }
            }

            val (layers, hasCycle) = assignLayers(nodes, edges)

            return PipelineGraph(
                nodes = nodes,
                edges = edges,
                layers = layers,
                danglingInputPortIds = dangling,
                hasCycle = hasCycle
            )
        }

        /**
         * Longest-path layering: a node sits one column right of its farthest predecessor.
         * This is what makes skip-edges read correctly, unlike grouping blindly by stage.
         */
        private fun assignLayers(
            nodes: List<PipelineNode>,
            edges: List<PipelineEdge>
        ): Pair<List<List<PipelineNode>>, Boolean> {
            // Conditional branches & feedback loops (rework loops, exceptions) are drawn but
            // never drive column order — a QC-fail-back-to-supply-chain edge would otherwise
            // look like a cycle and knock the entire layout back to stage order.
            val forwardEdges = edges.filter { it.kind == PipelineEdgeKind.FORWARD && !it.isFeedback }

            val rank = nodes.associate { it.id to 0 }.toMutableMap()
            var changed = true
            var passes = 0
            while (changed && passes <= nodes.size) {
                changed = false
                forwardEdges.forEach { edge ->
                    val fromRank = rank[edge.fromNodeId] ?: return@forEach
                    val toRank = rank[edge.toNodeId] ?: return@forEach
                    if (toRank < fromRank + 1) {
                        rank[edge.toNodeId] = fromRank + 1
                        changed = true
                    }
                }
                passes++
            }

            // Still moving after a full pass per node means there is a cycle; fall back to stages.
            val hasCycle = changed
            if (hasCycle) {
                nodes.forEach { rank[it.id] = it.stage.order - 1 }
            }

            val layers = nodes
                .groupBy { rank[it.id] ?: 0 }
                .entries
                .sortedBy { it.key }
                .map { entry -> entry.value.sortedBy { it.stepNumber } }

            return layers to hasCycle
        }
    }
}

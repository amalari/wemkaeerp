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

import com.eventverse.app.domain.pack.GarmentBlueprints

import com.eventverse.app.domain.rbac.BusinessModule

/**
 * Brings a tenant's persisted topology up to date with [OperationalModuleCatalog].
 *
 * A pipeline is provisioned once from a preset and then persisted, so a module added to the
 * catalogue afterwards never reached tenants that already existed — the canvas kept drawing
 * the catalogue as it was on the day the tenant was created. This closes that gap without
 * the destructive "reset to preset", which would throw away the tenant's names and bypasses.
 *
 * Every missing module is inserted **bypassed**: syncing makes a module visible and ready to
 * switch on, but never changes what the factory actually runs or what counts against its plan.
 */
object PipelineCatalogReconciler {

    /** Awalan id node sisipan katalog/aktivasi — pembeda dari node preset (`fob-…`). */
    const val NODE_ID_PREFIX = "node-"

    /**
     * Returns [pipeline] itself (same instance) when nothing is missing, so callers can skip a
     * write with a plain identity check.
     */
    fun reconcile(
        pipeline: CustomTenantPipeline,
        grantedModules: Set<BusinessModule>
    ): CustomTenantPipeline {
        val catalogOrder = OperationalModuleCatalog.all.map { it.module }
        val installedCodes = pipeline.nodes.map { it.moduleId }.toSet()
        val missing = catalogOrder.filter { it in grantedModules && it.code !in installedCodes }
        if (missing.isEmpty()) return pipeline

        val ordered = pipeline.orderedNodes.toMutableList()
        missing.forEach { module ->
            ordered.add(insertionIndex(ordered, module, catalogOrder), missingNode(module))
        }

        val withNodes = pipeline.copy(
            nodes = ordered.mapIndexed { index, node -> node.copy(stepOrderIndex = index + 1) }
        )
        return withNodes.copy(edges = withNodes.edges + portEdgesFor(withNodes, missing.toSet()))
    }

    /**
     * Menyambung modul yang baru disisipkan ke node yang sudah ada **berdasarkan port** (keluar A ∩
     * masuk/rujukan B), supaya ia tampil tersambung di kanvas bahkan sebelum diaktifkan.
     */
    private fun portEdgesFor(pipeline: CustomTenantPipeline, inserted: Set<BusinessModule>): List<CustomPipelineEdge> {
        val blueprint = (pipeline.baseStarterPreset ?: GarmentBlueprints.DEFAULT)
        val nodeByModule = pipeline.nodes.mapNotNull { node -> node.standardModule?.let { it to node } }.toMap()
        val existingPairs = pipeline.edges.map { it.fromNodeId to it.toNodeId }.toSet()
        return nodeByModule.keys.flatMap { from -> nodeByModule.keys.map { to -> from to to } }
            .filter { (from, to) -> from != to && (from in inserted || to in inserted) }
            .mapNotNull { (from, to) ->
                val a = OperationalModuleCatalog.specificationFor(from)
                val b = OperationalModuleCatalog.specificationFor(to)
                val types = a.outputsFor(blueprint.parametersOf(from.code)).toSet() intersect
                    (b.inputsFor(blueprint.parametersOf(to.code)) + b.referenceInputs).toSet()
                val fromNode = nodeByModule.getValue(from)
                val toNode = nodeByModule.getValue(to)
                types.firstOrNull()
                    ?.takeIf { (fromNode.nodeId to toNode.nodeId) !in existingPairs }
                    ?.let { type -> CustomPipelineEdge("edge-${fromNode.nodeId}-to-${toNode.nodeId}", fromNode.nodeId, toNode.nodeId, type) }
            }
    }

    /**
     * Places a module right after the last node that precedes it in catalogue order, so a new
     * sewing-stage module lands among the sewing modules instead of at the end of the flow.
     * Custom plugins have no catalogue position and are stepped over.
     */
    private fun insertionIndex(
        ordered: List<CustomPipelineNode>,
        module: BusinessModule,
        catalogOrder: List<BusinessModule>
    ): Int {
        val position = catalogOrder.indexOf(module)
        val predecessor = ordered.indexOfLast { node ->
            val nodePosition = node.standardModule?.let(catalogOrder::indexOf) ?: -1
            nodePosition in 0 until position
        }
        return predecessor + 1
    }

    private fun missingNode(module: BusinessModule): CustomPipelineNode {
        val specification = OperationalModuleCatalog.specificationFor(module)
        return CustomPipelineNode(
            nodeId = "$NODE_ID_PREFIX${module.code}",
            moduleId = module.code,
            customDisplayName = module.displayName,
            archetype = specification.archetype,
            isBypassed = true
        )
    }
}

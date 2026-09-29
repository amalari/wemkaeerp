package com.eventverse.app.domain.pipeline.usecases

import com.eventverse.app.domain.blueprint.Blueprint

import com.eventverse.app.domain.pack.GarmentBlueprints

import com.eventverse.app.domain.pipeline.defaultExpectedInputType

import com.eventverse.app.domain.pipeline.CustomPipelineEdge
import com.eventverse.app.domain.pipeline.CustomPipelineNode
import com.eventverse.app.domain.pipeline.CustomTenantPipeline
import com.eventverse.app.domain.pipeline.OperationalModuleCatalog
import com.eventverse.app.domain.pipeline.TenantModuleEntitlement
import com.eventverse.app.domain.pipeline.TenantPipelineRepository
import com.eventverse.app.domain.tenant.TenantId

/**
 * Switches one operational module on or off for a single tenant.
 *
 * Turning a module off marks it bypassed rather than deleting it, so the surrounding wiring
 * and the tenant's naming survive and the module can be switched back on later. Turning on a
 * module the tenant never installed adds it to the graph and connects it into the flow.
 *
 * This is the write side of "give module X to factory Y", and it is where the subscription
 * plan is enforced.
 */
class SetTenantModuleActivationUseCase(
    private val pipelineRepository: TenantPipelineRepository,
    private val getPipelineUseCase: GetTenantPipelineUseCase = GetTenantPipelineUseCase(pipelineRepository),
    private val savePipelineUseCase: SaveTenantPipelineUseCase = SaveTenantPipelineUseCase(pipelineRepository)
) {
    suspend operator fun invoke(
        tenantId: TenantId,
        moduleId: String,
        isActive: Boolean,
        entitlement: TenantModuleEntitlement,
        fallbackPreset: Blueprint = GarmentBlueprints.DEFAULT
    ): Result<CustomTenantPipeline> = runCatching {
        require(moduleId.isNotBlank()) { "moduleId cannot be blank" }

        val pipeline = getPipelineUseCase(tenantId, fallbackPreset).getOrThrow()
        val existingNode = pipeline.nodes.firstOrNull { it.moduleId == moduleId }

        val updated = when {
            existingNode != null && isActive ->
                wireIfIsolated(pipeline.setNodeBypassed(existingNode.nodeId, false), existingNode.nodeId)
            existingNode != null -> pipeline.setNodeBypassed(existingNode.nodeId, true)
            !isActive -> pipeline // Not installed and asked to stay off: nothing to do.
            else -> installModule(pipeline, moduleId)
        }

        savePipelineUseCase(updated, entitlement).getOrThrow()
    }

    /**
     * Adds a built-in module the tenant did not previously have, wiring it after the last
     * node that feeds its capability slot so the flow stays connected.
     */
    private fun installModule(
        pipeline: CustomTenantPipeline,
        moduleId: String
    ): CustomTenantPipeline {
        val specification = OperationalModuleCatalog.specificationForCode(moduleId)
        requireNotNull(specification) {
            "Modul \"$moduleId\" bukan modul bawaan. Gunakan InstallCustomModuleUseCase untuk modul kustom."
        }

        val nodeId = "node-$moduleId"
        val newNode = CustomPipelineNode(
            nodeId = nodeId,
            moduleId = moduleId,
            customDisplayName = specification.module.displayName,
            archetype = specification.archetype,
            isBypassed = false
        )

        return wireIfIsolated(pipeline.addNode(newNode), nodeId)
    }

    /**
     * Connects a node from the last active node before it when it has no forward wiring yet —
     * the case for a freshly installed module and for one that catalogue sync inserted bypassed.
     * A node the tenant already wired is left exactly as they wired it.
     */
    private fun wireIfIsolated(pipeline: CustomTenantPipeline, nodeId: String): CustomTenantPipeline {
        val isWired = pipeline.edges.any {
            !it.isFeedbackReworkLoop && (it.fromNodeId == nodeId || it.toNodeId == nodeId)
        }
        if (isWired) return pipeline

        val ordered = pipeline.orderedNodes
        val node = ordered.first { it.nodeId == nodeId }
        val upstream = ordered
            .takeWhile { it.nodeId != nodeId }
            .lastOrNull { !it.isBypassed }
            ?: return pipeline

        return pipeline.connect(
            CustomPipelineEdge(
                edgeId = "edge-${upstream.nodeId}-to-$nodeId",
                fromNodeId = upstream.nodeId,
                toNodeId = nodeId,
                expectedDataType = node.archetype.defaultExpectedInputType
            )
        )
    }
}

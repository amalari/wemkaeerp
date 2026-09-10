package com.eventverse.app.domain.pipeline.usecases

import com.eventverse.app.domain.pipeline.CustomPipelineEdge
import com.eventverse.app.domain.pipeline.CustomPipelineNode
import com.eventverse.app.domain.pipeline.CustomTenantPipeline
import com.eventverse.app.domain.pipeline.GarmentBusinessPreset
import com.eventverse.app.domain.pipeline.ModuleArchetype
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
        fallbackPreset: GarmentBusinessPreset = GarmentBusinessPreset.DEFAULT
    ): Result<CustomTenantPipeline> = runCatching {
        require(moduleId.isNotBlank()) { "moduleId cannot be blank" }

        val pipeline = getPipelineUseCase(tenantId, fallbackPreset).getOrThrow()
        val existingNode = pipeline.nodes.firstOrNull { it.moduleId == moduleId }

        val updated = when {
            existingNode != null -> pipeline.setNodeBypassed(existingNode.nodeId, !isActive)
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
            archetype = ModuleArchetype.forModule(specification.module),
            isBypassed = false
        )

        val withNode = pipeline.addNode(newNode)
        val upstream = withNode.orderedNodes
            .lastOrNull { it.nodeId != nodeId && !it.isBypassed }
            ?: return withNode

        return withNode.connect(
            CustomPipelineEdge(
                edgeId = "edge-${upstream.nodeId}-to-$nodeId",
                fromNodeId = upstream.nodeId,
                toNodeId = nodeId,
                expectedDataType = specification.archetype.defaultExpectedInputType
            )
        )
    }
}

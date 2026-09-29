package com.eventverse.app.domain.pipeline.usecases

import com.eventverse.app.domain.blueprint.Blueprint

import com.eventverse.app.domain.pack.GarmentBlueprints

import com.eventverse.app.domain.pipeline.defaultExpectedInputType

import com.eventverse.app.domain.pipeline.CustomPipelineEdge
import com.eventverse.app.domain.pipeline.CustomTenantPipeline
import com.eventverse.app.domain.pipeline.DynamicModuleDescriptor
import com.eventverse.app.domain.pipeline.TenantEntitlementGrants
import com.eventverse.app.domain.pipeline.TenantEntitlementRepository
import com.eventverse.app.domain.pipeline.TenantModuleEntitlement
import com.eventverse.app.domain.pipeline.TenantPipelineRepository
import com.eventverse.app.domain.tenant.TenantId

/**
 * Installs a custom / third-party plugin module into one tenant's pipeline — a manual
 * screen-printing station, computerised embroidery, a marketplace integration.
 *
 * This is the extension point [DynamicModuleDescriptor] was declared for. The plugin is
 * persisted as an ordinary node in the tenant's graph, so no codebase enum has to change to
 * give one factory a module no other factory has.
 */
class InstallCustomModuleUseCase(
    private val pipelineRepository: TenantPipelineRepository,
    /**
     * Records the plugin grant. A custom module grant cannot be derived from the plan tier,
     * so without persisting it here every later edit to this tenant's pipeline would be
     * rejected for running a plugin nothing says it is allowed to run.
     */
    private val entitlementRepository: TenantEntitlementRepository? = null,
    private val getPipelineUseCase: GetTenantPipelineUseCase = GetTenantPipelineUseCase(pipelineRepository),
    private val savePipelineUseCase: SaveTenantPipelineUseCase = SaveTenantPipelineUseCase(pipelineRepository)
) {
    suspend operator fun invoke(
        tenantId: TenantId,
        descriptor: DynamicModuleDescriptor,
        entitlement: TenantModuleEntitlement,
        attachAfterNodeId: String? = null,
        formulaParameters: Map<String, String> = emptyMap(),
        fallbackPreset: Blueprint = GarmentBlueprints.DEFAULT
    ): Result<CustomTenantPipeline> = runCatching {
        require(descriptor.isCustomTenantPlugin) {
            "Descriptor \"${descriptor.moduleId}\" bukan plugin tenant; tandai isCustomTenantPlugin = true."
        }
        require(entitlement.allowsCustomPlugins) {
            "Paket ${entitlement.tier.name} tidak mendukung pemasangan modul kustom."
        }

        val pipeline = getPipelineUseCase(tenantId, fallbackPreset).getOrThrow()
        require(pipeline.nodes.none { it.moduleId == descriptor.moduleId }) {
            "Modul kustom \"${descriptor.moduleId}\" sudah terpasang pada tenant ini."
        }

        val nodeId = "custom-${descriptor.moduleId}"
        val withNode = pipeline.addNode(
            descriptor.toPipelineNode(nodeId = nodeId, formulaParameters = formulaParameters)
        )

        val upstream = attachAfterNodeId?.let { requestedId ->
            requireNotNull(withNode.findNode(requestedId)) {
                "Node tujuan penyambungan tidak ditemukan: $requestedId"
            }
        } ?: withNode.orderedNodes.lastOrNull { it.nodeId != nodeId && !it.isBypassed }

        val wired = upstream?.let {
            withNode.connect(
                CustomPipelineEdge(
                    edgeId = "edge-${it.nodeId}-to-$nodeId",
                    fromNodeId = it.nodeId,
                    toNodeId = nodeId,
                    expectedDataType = descriptor.acceptedInputDataTypes.firstOrNull()
                        ?: descriptor.archetype.defaultExpectedInputType
                )
            )
        } ?: withNode

        // The plugin must be granted before it can be saved, otherwise the entitlement
        // check would reject the very node we just installed.
        val grantedEntitlement = entitlement.grantCustomModule(descriptor.moduleId)

        // Persist the grant first: a saved pipeline whose grant was lost would leave the
        // tenant unable to edit its own flow.
        entitlementRepository?.let { repository ->
            val existing = repository.findByTenantId(tenantId) ?: TenantEntitlementGrants.NONE
            repository.save(tenantId, existing.grantCustomModule(descriptor.moduleId)).getOrThrow()
        }

        savePipelineUseCase(wired, grantedEntitlement).getOrThrow()
    }
}

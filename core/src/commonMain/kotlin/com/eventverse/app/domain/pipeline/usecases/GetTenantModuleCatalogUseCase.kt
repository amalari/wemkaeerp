package com.eventverse.app.domain.pipeline.usecases

import com.eventverse.app.domain.pipeline.CustomPipelineNode
import com.eventverse.app.domain.pipeline.GarmentBusinessPreset
import com.eventverse.app.domain.pipeline.OperationalModuleCatalog
import com.eventverse.app.domain.pipeline.TenantModuleAvailability
import com.eventverse.app.domain.pipeline.TenantModuleEntitlement
import com.eventverse.app.domain.pipeline.TenantPipelineRepository
import com.eventverse.app.domain.tenant.TenantId

/**
 * Lists every module a tenant could run, annotated with whether their plan grants it and
 * whether it is currently installed and switched on.
 *
 * This is the read side of module provisioning: it answers "which modules does this factory
 * have, and which could it have?" in one call.
 */
class GetTenantModuleCatalogUseCase(
    private val pipelineRepository: TenantPipelineRepository
) {
    suspend operator fun invoke(
        tenantId: TenantId,
        entitlement: TenantModuleEntitlement,
        fallbackPreset: GarmentBusinessPreset = GarmentBusinessPreset.DEFAULT
    ): Result<List<TenantModuleAvailability>> = runCatching {
        val pipeline = pipelineRepository.findByTenantId(tenantId)
        val preset = pipeline?.baseStarterPreset ?: fallbackPreset
        val installedByModuleId: Map<String, CustomPipelineNode> =
            pipeline?.nodes?.associateBy { it.moduleId } ?: emptyMap()
        val recommended = OperationalModuleCatalog.recommendedFor(preset).map { it.module }.toSet()

        val builtIns = OperationalModuleCatalog.all.map { specification ->
            val installed = installedByModuleId[specification.module.code]
            TenantModuleAvailability(
                moduleId = specification.module.code,
                displayName = specification.module.displayName,
                tenantDisplayName = installed?.customDisplayName
                    ?.takeIf { it != specification.module.displayName },
                archetype = specification.archetype,
                standardModule = specification.module,
                isCustomPlugin = false,
                isInstalled = installed != null,
                isActive = installed != null && !installed.isBypassed,
                isGrantedByPlan = specification.module in entitlement.grantedModules,
                isRecommendedForPreset = specification.module in recommended,
                nodeId = installed?.nodeId
            )
        }

        // Custom plugin modules exist only as tenant data, so they are enumerated from the
        // graph itself rather than from the built-in catalogue.
        val customPlugins = installedByModuleId.values
            .filter { it.isCustomPlugin }
            .map { node ->
                TenantModuleAvailability(
                    moduleId = node.moduleId,
                    displayName = node.customDisplayName,
                    tenantDisplayName = node.customDisplayName,
                    archetype = node.archetype,
                    standardModule = null,
                    isCustomPlugin = true,
                    isInstalled = true,
                    isActive = !node.isBypassed,
                    isGrantedByPlan = entitlement.allowsCustomPlugins &&
                        node.moduleId in entitlement.grantedCustomModuleIds,
                    isRecommendedForPreset = false,
                    nodeId = node.nodeId
                )
            }

        builtIns + customPlugins
    }
}

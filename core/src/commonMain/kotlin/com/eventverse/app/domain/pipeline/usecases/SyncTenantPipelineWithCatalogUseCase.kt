package com.eventverse.app.domain.pipeline.usecases

import com.eventverse.app.domain.pipeline.CustomTenantPipeline
import com.eventverse.app.domain.pipeline.GarmentBusinessPreset
import com.eventverse.app.domain.pipeline.PipelineCatalogReconciler
import com.eventverse.app.domain.pipeline.TenantModuleEntitlement
import com.eventverse.app.domain.pipeline.TenantPipelineRepository
import com.eventverse.app.domain.tenant.TenantId

/**
 * Reads a tenant's pipeline and adds any entitled catalogue module it is missing, so the
 * factory canvas always reflects the modules the product actually ships.
 *
 * Writes only when something was added; an up-to-date pipeline costs a single read.
 * The entitlement check is skipped on purpose: synced modules arrive bypassed, and a
 * bypassed module occupies no licence.
 */
class SyncTenantPipelineWithCatalogUseCase(
    private val pipelineRepository: TenantPipelineRepository,
    private val getPipelineUseCase: GetTenantPipelineUseCase = GetTenantPipelineUseCase(pipelineRepository)
) {
    suspend operator fun invoke(
        tenantId: TenantId,
        entitlement: TenantModuleEntitlement,
        fallbackPreset: GarmentBusinessPreset = GarmentBusinessPreset.DEFAULT
    ): Result<CustomTenantPipeline> = runCatching {
        val pipeline = getPipelineUseCase(tenantId, fallbackPreset).getOrThrow()
        val reconciled = PipelineCatalogReconciler.reconcile(pipeline, entitlement.grantedModules)

        if (reconciled === pipeline) pipeline else pipelineRepository.save(reconciled).getOrThrow()
    }
}

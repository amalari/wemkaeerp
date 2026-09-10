package com.eventverse.app.domain.pipeline.usecases

import com.eventverse.app.domain.pipeline.CustomTenantPipeline
import com.eventverse.app.domain.pipeline.GarmentBusinessPreset
import com.eventverse.app.domain.pipeline.TenantPipelineRepository
import com.eventverse.app.domain.tenant.TenantId

/**
 * UseCase for retrieving a tenant's active workflow pipeline.
 * If the tenant does not have a saved custom pipeline, synthesizes a starting pipeline
 * based on the fallback preset, saves it to the repository, and returns it.
 */
class GetTenantPipelineUseCase(
    private val pipelineRepository: TenantPipelineRepository
) {
    suspend operator fun invoke(
        tenantId: TenantId,
        fallbackPreset: GarmentBusinessPreset = GarmentBusinessPreset.DEFAULT
    ): Result<CustomTenantPipeline> = runCatching {
        val existing = pipelineRepository.findByTenantId(tenantId)
        if (existing != null) {
            existing
        } else {
            val initialPipeline = CustomTenantPipeline.fromPreset(tenantId, fallbackPreset)
            pipelineRepository.save(initialPipeline).getOrThrow()
        }
    }
}

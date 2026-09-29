package com.eventverse.app.domain.pipeline.usecases

import com.eventverse.app.domain.blueprint.Blueprint

import com.eventverse.app.domain.pack.GarmentBlueprints

import com.eventverse.app.domain.pipeline.CustomTenantPipeline
import com.eventverse.app.domain.pipeline.TenantPipelineRepository
import com.eventverse.app.domain.tenant.TenantId

/**
 * UseCase to reset a tenant's custom pipeline back to a standard starter preset
 * (FOB Full Package, CMT Makloon, or Brand D2C).
 */
class ResetTenantPipelineUseCase(
    private val pipelineRepository: TenantPipelineRepository
) {
    suspend operator fun invoke(
        tenantId: TenantId,
        targetPreset: Blueprint = GarmentBlueprints.DEFAULT
    ): Result<CustomTenantPipeline> = runCatching {
        val freshPipeline = CustomTenantPipeline.fromPreset(tenantId, targetPreset)
        pipelineRepository.save(freshPipeline).getOrThrow()
    }
}

package com.eventverse.app.domain.pipeline.usecases

import com.eventverse.app.domain.pipeline.CustomTenantPipeline
import com.eventverse.app.domain.pipeline.GarmentBusinessPreset
import com.eventverse.app.domain.pipeline.TenantPipelineRepository
import com.eventverse.app.domain.tenant.TenantId

/**
 * UseCase for retrieving a tenant's active workflow pipeline.
 *
 * Provisions a starting topology from [fallbackPreset] when the tenant has none yet, then
 * persists it so subsequent reads are stable.
 *
 * A row may exist while carrying no nodes — for instance a tenant seeded by a migration
 * before its topology was written. That is an unprovisioned tenant, not a valid empty
 * workflow, so it is provisioned here too; returning it as-is previously left the factory
 * canvas blank.
 */
class GetTenantPipelineUseCase(
    private val pipelineRepository: TenantPipelineRepository
) {
    suspend operator fun invoke(
        tenantId: TenantId,
        fallbackPreset: GarmentBusinessPreset = GarmentBusinessPreset.DEFAULT
    ): Result<CustomTenantPipeline> = runCatching {
        val existing = pipelineRepository.findByTenantId(tenantId)

        if (existing != null && !existing.isEmpty) return@runCatching existing

        // Preserve whatever the stored row already decided about naming and preset, so
        // provisioning a half-seeded tenant does not discard curated metadata.
        val preset = existing?.baseStarterPreset ?: fallbackPreset
        val provisioned = CustomTenantPipeline.fromPreset(tenantId, preset).let { fresh ->
            val storedName = existing?.pipelineName?.takeIf { it.isNotBlank() }
            if (storedName != null) fresh.copy(pipelineName = storedName) else fresh
        }

        pipelineRepository.save(provisioned).getOrThrow()
    }
}

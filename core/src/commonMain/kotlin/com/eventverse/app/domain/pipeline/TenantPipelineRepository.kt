package com.eventverse.app.domain.pipeline

import com.eventverse.app.domain.tenant.TenantId

/**
 * Repository interface for persisting and retrieving custom tenant workflow pipelines.
 * Adheres to DDD: pure Kotlin domain interface without framework dependencies.
 */
interface TenantPipelineRepository {
    suspend fun findByTenantId(tenantId: TenantId): CustomTenantPipeline?
    suspend fun save(pipeline: CustomTenantPipeline): Result<CustomTenantPipeline>
    suspend fun deleteByTenantId(tenantId: TenantId): Result<Unit>
}

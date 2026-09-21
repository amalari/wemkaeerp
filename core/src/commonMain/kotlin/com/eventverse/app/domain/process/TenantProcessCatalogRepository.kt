package com.eventverse.app.domain.process

import com.eventverse.app.domain.tenant.TenantId

/**
 * Repository interface untuk katalog proses opsional per tenant.
 * DDD: interface domain murni tanpa dependensi framework; implementasi di infrastructure.
 */
interface TenantProcessCatalogRepository {
    suspend fun findByTenantId(tenantId: TenantId): TenantProcessCatalog?
    suspend fun save(catalog: TenantProcessCatalog): Result<TenantProcessCatalog>
    suspend fun deleteProcess(tenantId: TenantId, processId: String): Result<Unit>
}
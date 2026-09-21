package com.eventverse.app.domain.process.usecases

import com.eventverse.app.domain.process.TenantProcessCatalog
import com.eventverse.app.domain.process.TenantProcessCatalogRepository
import com.eventverse.app.domain.tenant.TenantId

data class GetTenantProcessCatalogQuery(
    val tenantId: TenantId
)

/**
 * Mengambil katalog proses opsional tenant. Tenant tanpa katalog (belum pernah
 * adjust flow) mendapat katalog kosong yang sah — bukan error.
 */
class GetTenantProcessCatalogUseCase(
    private val repository: TenantProcessCatalogRepository
) {
    suspend operator fun invoke(query: GetTenantProcessCatalogQuery): Result<TenantProcessCatalog> = runCatching {
        repository.findByTenantId(query.tenantId) ?: TenantProcessCatalog(tenantId = query.tenantId)
    }
}
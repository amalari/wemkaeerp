package com.eventverse.app.domain.masterdata.usecases

import com.eventverse.app.domain.masterdata.MaterialCatalogPage
import com.eventverse.app.domain.masterdata.MaterialCatalogQuery
import com.eventverse.app.domain.masterdata.MaterialItemRepository
import com.eventverse.app.domain.tenant.TenantId

class GetMaterialCatalogUseCase(
    private val materialRepository: MaterialItemRepository
) {
    suspend operator fun invoke(tenantId: TenantId, query: MaterialCatalogQuery): Result<MaterialCatalogPage> = runCatching {
        val safeQuery = query.copy(
            page = kotlin.math.max(1, query.page),
            pageSize = query.pageSize.coerceIn(1, 100)
        )
        materialRepository.searchCatalog(tenantId, safeQuery)
    }
}

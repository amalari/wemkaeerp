package com.eventverse.app.domain.masterdata

import com.eventverse.app.domain.pipeline.StockOwnershipSemantics
import com.eventverse.app.domain.tenant.TenantId

data class MaterialCatalogQuery(
    val queryText: String? = null,
    val category: MaterialCategory? = null,
    val ownership: StockOwnershipSemantics? = null,
    val includeArchived: Boolean = false,
    val page: Int = 1,
    val pageSize: Int = 20
)

data class MaterialCatalogPage(
    val items: List<MaterialItem>,
    val totalCount: Long,
    val page: Int,
    val pageSize: Int
) {
    val totalPages: Int get() = if (pageSize <= 0) 1 else kotlin.math.max(1, ((totalCount + pageSize - 1) / pageSize).toInt())
}

interface MaterialItemRepository {
    suspend fun findById(tenantId: TenantId, id: MaterialId): MaterialItem?
    suspend fun findByCode(tenantId: TenantId, code: MaterialCode): MaterialItem?
    suspend fun findAllByIds(tenantId: TenantId, ids: Collection<MaterialId>): List<MaterialItem>
    suspend fun searchCatalog(tenantId: TenantId, query: MaterialCatalogQuery): MaterialCatalogPage
    suspend fun matchByFreeText(tenantId: TenantId, freeText: String, category: MaterialCategory? = null): List<MaterialItem>
    suspend fun save(material: MaterialItem): MaterialItem
    suspend fun reserveNextCode(tenantId: TenantId, category: MaterialCategory): MaterialCode
    suspend fun archive(tenantId: TenantId, id: MaterialId): Boolean
}

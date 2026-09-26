package com.eventverse.app.infrastructure

import com.eventverse.app.domain.masterdata.*
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Clock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

class InMemoryMaterialItemRepository : MaterialItemRepository {
    private val storage = ConcurrentHashMap<String, MaterialItem>()
    private val sequences = ConcurrentHashMap<String, AtomicLong>()

    override suspend fun findById(tenantId: TenantId, id: MaterialId): MaterialItem? =
        storage["${tenantId.value}:${id.value}"]

    override suspend fun findByCode(tenantId: TenantId, code: MaterialCode): MaterialItem? =
        storage.values.find { it.tenantId == tenantId && it.code == code }

    override suspend fun findAllByIds(tenantId: TenantId, ids: Collection<MaterialId>): List<MaterialItem> {
        val idSet = ids.map { it.value }.toSet()
        return storage.values.filter { it.tenantId == tenantId && it.id.value in idSet }
    }

    override suspend fun searchCatalog(tenantId: TenantId, query: MaterialCatalogQuery): MaterialCatalogPage {
        val filtered = storage.values.filter { item ->
            if (item.tenantId != tenantId) return@filter false
            if (!query.includeArchived && item.archivedAt != null) return@filter false
            if (query.category != null && item.category != query.category) return@filter false
            if (query.ownership != null && item.defaultOwnership != query.ownership) return@filter false
            val q = query.queryText
            if (q != null && q.isNotBlank()) {
                val matches = item.name.contains(q, ignoreCase = true) ||
                    item.code.value.contains(q, ignoreCase = true) ||
                    item.description.contains(q, ignoreCase = true)
                if (!matches) return@filter false
            }
            true
        }.sortedBy { it.code.value }

        val totalCount = filtered.size.toLong()
        val startIndex = (query.page - 1) * query.pageSize
        val items = if (startIndex >= filtered.size) {
            emptyList()
        } else {
            filtered.drop(startIndex).take(query.pageSize)
        }

        return MaterialCatalogPage(
            items = items,
            totalCount = totalCount,
            page = query.page,
            pageSize = query.pageSize
        )
    }

    override suspend fun matchByFreeText(
        tenantId: TenantId,
        freeText: String,
        category: MaterialCategory?
    ): List<MaterialItem> {
        return storage.values.filter { item ->
            if (item.tenantId != tenantId) return@filter false
            if (item.archivedAt != null) return@filter false
            if (category != null && item.category != category) return@filter false
            item.name.contains(freeText, ignoreCase = true) ||
                item.code.value.contains(freeText, ignoreCase = true)
        }.take(10)
    }

    override suspend fun save(material: MaterialItem): MaterialItem {
        storage["${material.tenantId.value}:${material.id.value}"] = material
        return material
    }

    override suspend fun reserveNextCode(tenantId: TenantId, category: MaterialCategory): MaterialCode {
        val seqKey = "${tenantId.value}:${category.code}"
        val seq = sequences.computeIfAbsent(seqKey) { AtomicLong(0L) }.incrementAndGet()
        val formatted = seq.toString().padStart(4, '0')
        return MaterialCode("${category.codePrefix}-$formatted")
    }

    override suspend fun archive(tenantId: TenantId, id: MaterialId): Boolean {
        val existing = findById(tenantId, id) ?: return false
        val archived = existing.copy(archivedAt = Clock.System.now())
        save(archived)
        return true
    }
}

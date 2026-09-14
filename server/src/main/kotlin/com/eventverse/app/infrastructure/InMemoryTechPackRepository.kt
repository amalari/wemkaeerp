package com.eventverse.app.infrastructure

import com.eventverse.app.domain.techpack.*
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Clock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

class InMemoryTechPackRepository : TechPackRepository {
    private val storage = ConcurrentHashMap<String, TechPack>()
    private val sequences = ConcurrentHashMap<String, AtomicLong>()

    override suspend fun findById(tenantId: TenantId, id: TechPackId): TechPack? =
        storage["${tenantId.value}:${id.value}"]

    override suspend fun findVersions(tenantId: TenantId, styleCode: StyleCode): List<TechPack> =
        storage.values
            .filter { it.tenantId == tenantId && it.styleCode == styleCode }
            .sortedByDescending { it.version }

    override suspend fun findLatestReleased(tenantId: TenantId, styleCode: StyleCode): TechPack? =
        storage.values
            .filter { it.tenantId == tenantId && it.styleCode == styleCode && it.status == TechPackStatus.RELEASED && it.archivedAt == null }
            .maxByOrNull { it.version }

    override suspend fun findBySourceSample(tenantId: TenantId, sourceSampleSpecId: String): TechPack? =
        storage.values
            .filter { it.tenantId == tenantId && it.sourceSampleSpecId == sourceSampleSpecId && it.archivedAt == null }
            .maxByOrNull { it.version }

    override suspend fun search(tenantId: TenantId, query: TechPackQuery): TechPackPage {
        var items = storage.values.filter { it.tenantId == tenantId }

        if (!query.includeArchived) {
            items = items.filter { it.archivedAt == null }
        }

        val qStatus = query.status
        if (qStatus != null) {
            items = items.filter { it.status == qStatus }
        }

        val qStyle = query.styleCode
        if (qStyle != null) {
            items = items.filter { it.styleCode == qStyle }
        }

        val qText = query.queryText
        if (!qText.isNullOrBlank()) {
            val q = qText.trim()
            items = items.filter {
                it.styleCode.value.contains(q, ignoreCase = true) ||
                it.styleName.contains(q, ignoreCase = true) ||
                it.clientName.contains(q, ignoreCase = true)
            }
        }

        if (query.latestVersionOnly) {
            items = items.groupBy { it.styleCode }
                .mapNotNull { (_, versions) -> versions.maxByOrNull { it.version } }
        }

        val sorted = items.sortedByDescending { it.updatedAt }
        val totalCount = sorted.size.toLong()
        val startIndex = ((query.page - 1).coerceAtLeast(0)) * query.pageSize
        val pageItems = if (startIndex >= sorted.size) {
            emptyList()
        } else {
            sorted.drop(startIndex).take(query.pageSize)
        }

        return TechPackPage(
            items = pageItems,
            totalCount = totalCount,
            page = query.page,
            pageSize = query.pageSize
        )
    }

    override suspend fun save(techPack: TechPack): TechPack {
        storage["${techPack.tenantId.value}:${techPack.id.value}"] = techPack
        return techPack
    }

    override suspend fun saveRevision(oldVersion: TechPack, newVersion: TechPack): TechPack {
        storage["${oldVersion.tenantId.value}:${oldVersion.id.value}"] = oldVersion
        storage["${newVersion.tenantId.value}:${newVersion.id.value}"] = newVersion
        return newVersion
    }

    override suspend fun archive(tenantId: TenantId, id: TechPackId): Boolean {
        val existing = storage["${tenantId.value}:${id.value}"] ?: return false
        val now = Clock.System.now()
        storage["${tenantId.value}:${id.value}"] = existing.copy(archivedAt = now, updatedAt = now)
        return true
    }

    override suspend fun reserveNextStyleCode(tenantId: TenantId, prefix: String): StyleCode {
        val cleanPrefix = prefix.trim().ifBlank { "STY" }
        val seq = sequences.computeIfAbsent("${tenantId.value}:$cleanPrefix") { AtomicLong(0L) }
            .incrementAndGet()
        return StyleCode("$cleanPrefix-${seq.toString().padStart(4, '0')}")
    }
}

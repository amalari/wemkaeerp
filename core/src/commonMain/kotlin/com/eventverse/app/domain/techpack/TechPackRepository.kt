package com.eventverse.app.domain.techpack

import com.eventverse.app.domain.tenant.TenantId

data class TechPackQuery(
    val queryText: String? = null,
    val status: TechPackStatus? = null,
    val styleCode: StyleCode? = null,
    val includeArchived: Boolean = false,
    val latestVersionOnly: Boolean = true,
    val page: Int = 1,
    val pageSize: Int = 20
)

data class TechPackPage(
    val items: List<TechPack>,
    val totalCount: Long,
    val page: Int,
    val pageSize: Int
) {
    val totalPages: Int
        get() = if (pageSize <= 0) 1 else kotlin.math.max(1, ((totalCount + pageSize - 1) / pageSize).toInt())
}

interface TechPackRepository {
    suspend fun findById(tenantId: TenantId, id: TechPackId): TechPack?
    suspend fun findVersions(tenantId: TenantId, styleCode: StyleCode): List<TechPack>
    suspend fun findLatestReleased(tenantId: TenantId, styleCode: StyleCode): TechPack?
    suspend fun findBySourceSample(tenantId: TenantId, sourceSampleSpecId: String): TechPack?
    suspend fun search(tenantId: TenantId, query: TechPackQuery): TechPackPage
    suspend fun save(techPack: TechPack): TechPack
    suspend fun saveRevision(oldVersion: TechPack, newVersion: TechPack): TechPack
    suspend fun archive(tenantId: TenantId, id: TechPackId): Boolean
    suspend fun reserveNextStyleCode(tenantId: TenantId, prefix: String = "STY"): StyleCode
}

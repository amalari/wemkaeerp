package com.eventverse.app.domain.process

import com.eventverse.app.domain.tenant.TenantId

/**
 * Template tag fase per tenant — default yang diwarisi setiap desain baru.
 * Tenant tanpa baris tersimpan memakai [StagePhaseTags.DEFAULT] (kedua fase).
 */
interface TenantStagePhaseTagsRepository {
    suspend fun findByTenantId(tenantId: TenantId): StagePhaseTags
    suspend fun save(tenantId: TenantId, tags: StagePhaseTags): Result<StagePhaseTags>
}

package com.eventverse.app.infrastructure

import com.eventverse.app.domain.auth.UserId
import com.eventverse.app.domain.discovery.DiscoveryDraftId
import com.eventverse.app.domain.discovery.DiscoveryDraftRepository
import com.eventverse.app.domain.discovery.StoredDiscoveryDraft
import com.eventverse.app.domain.tenant.TenantId

/** Untuk test API; aturan status & kepemilikan milik use case, bukan repository. */
class InMemoryDiscoveryDraftRepository : DiscoveryDraftRepository {

    private val rows = linkedMapOf<DiscoveryDraftId, StoredDiscoveryDraft>()

    override suspend fun findById(id: DiscoveryDraftId): StoredDiscoveryDraft? = rows[id]

    override suspend fun findByOwner(ownerUserId: UserId): List<StoredDiscoveryDraft> =
        rows.values.filter { it.ownerUserId == ownerUserId }.reversed()

    override suspend fun findByTenant(tenantId: TenantId): StoredDiscoveryDraft? =
        rows.values.lastOrNull { it.tenantId == tenantId }

    override suspend fun findAll(): List<StoredDiscoveryDraft> = rows.values.toList()

    override suspend fun save(stored: StoredDiscoveryDraft): StoredDiscoveryDraft {
        rows[stored.id] = stored
        return stored
    }

    fun clear() = rows.clear()
}

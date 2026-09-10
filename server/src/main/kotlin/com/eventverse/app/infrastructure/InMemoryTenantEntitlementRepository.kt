package com.eventverse.app.infrastructure

import com.eventverse.app.domain.pipeline.TenantEntitlementGrants
import com.eventverse.app.domain.pipeline.TenantEntitlementRepository
import com.eventverse.app.domain.tenant.TenantId
import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe in-memory implementation of [TenantEntitlementRepository], for tests and
 * local runs without a database.
 */
class InMemoryTenantEntitlementRepository : TenantEntitlementRepository {
    private val grants = ConcurrentHashMap<TenantId, TenantEntitlementGrants>()

    override suspend fun findByTenantId(tenantId: TenantId): TenantEntitlementGrants? =
        grants[tenantId]

    override suspend fun save(
        tenantId: TenantId,
        grants: TenantEntitlementGrants
    ): Result<Unit> {
        this.grants[tenantId] = grants
        return Result.success(Unit)
    }
}

package com.eventverse.app.domain.pipeline

import com.eventverse.app.domain.tenant.TenantId

/**
 * Persistence for per-tenant module grants. Defined in the domain, implemented in
 * infrastructure.
 */
interface TenantEntitlementRepository {

    /** Returns the tenant's overrides, or null when it runs on plan defaults only. */
    suspend fun findByTenantId(tenantId: TenantId): TenantEntitlementGrants?

    suspend fun save(tenantId: TenantId, grants: TenantEntitlementGrants): Result<Unit>
}

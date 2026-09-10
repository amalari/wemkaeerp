package com.eventverse.app.domain.audit

import com.eventverse.app.domain.tenant.TenantId

/**
 * Persistence for the platform audit trail. Defined in the domain, implemented in
 * infrastructure — the entries themselves are append-only, so this deliberately exposes no
 * update or delete.
 */
interface AuditLogRepository {

    suspend fun record(entry: AuditLogEntry): Result<Unit>

    /** Most recent entries for one tenant first. */
    suspend fun findByTenant(tenantId: TenantId, limit: Int = 50): List<AuditLogEntry>
}

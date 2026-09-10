package com.eventverse.app.infrastructure

import com.eventverse.app.domain.audit.AuditLogEntry
import com.eventverse.app.domain.audit.AuditLogRepository
import com.eventverse.app.domain.tenant.TenantId
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Thread-safe in-memory implementation of [AuditLogRepository], for tests and local runs
 * without a database.
 */
class InMemoryAuditLogRepository : AuditLogRepository {
    private val entries = ConcurrentLinkedQueue<AuditLogEntry>()

    override suspend fun record(entry: AuditLogEntry): Result<Unit> {
        entries += entry
        return Result.success(Unit)
    }

    override suspend fun findByTenant(tenantId: TenantId, limit: Int): List<AuditLogEntry> =
        entries
            .filter { it.targetTenantId == tenantId }
            .sortedByDescending { it.occurredAt }
            .take(limit)
}

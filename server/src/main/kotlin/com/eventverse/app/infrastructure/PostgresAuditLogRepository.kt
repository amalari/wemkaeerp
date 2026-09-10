package com.eventverse.app.infrastructure

import com.eventverse.app.domain.audit.AuditAction
import com.eventverse.app.domain.audit.AuditLogEntry
import com.eventverse.app.domain.audit.AuditLogRepository
import com.eventverse.app.domain.auth.Role
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.infrastructure.tables.AuditLogsTable
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll

/**
 * PostgreSQL implementation of [AuditLogRepository]. Entries are append-only: there is
 * deliberately no update or delete.
 */
class PostgresAuditLogRepository : AuditLogRepository {

    override suspend fun record(entry: AuditLogEntry): Result<Unit> = runCatching {
        DatabaseFactory.dbQuery(entry.targetTenantId) {
            AuditLogsTable.insert {
                it[id] = entry.id
                it[actorUserId] = entry.actorUserId
                it[actorRole] = entry.actorRole.name
                it[tenantId] = entry.targetTenantId.value
                it[action] = entry.action.code
                it[summary] = entry.summary
                it[occurredAt] = entry.occurredAt
            }
        }
    }

    override suspend fun findByTenant(tenantId: TenantId, limit: Int): List<AuditLogEntry> =
        DatabaseFactory.dbQuery(tenantId) {
            AuditLogsTable.selectAll()
                .where { AuditLogsTable.tenantId eq tenantId.value }
                .orderBy(AuditLogsTable.occurredAt, SortOrder.DESC)
                .limit(limit)
                .map { toEntry(it) }
        }

    private fun toEntry(row: ResultRow): AuditLogEntry = AuditLogEntry(
        id = row[AuditLogsTable.id],
        actorUserId = row[AuditLogsTable.actorUserId],
        actorRole = runCatching { Role.valueOf(row[AuditLogsTable.actorRole]) }
            .getOrDefault(Role.TENANT_ADMIN),
        targetTenantId = TenantId(row[AuditLogsTable.tenantId]),
        action = requireNotNull(AuditAction.fromCode(row[AuditLogsTable.action])) {
            "Unknown audit action code in database: ${row[AuditLogsTable.action]}"
        },
        summary = row[AuditLogsTable.summary],
        occurredAt = row[AuditLogsTable.occurredAt]
    )
}

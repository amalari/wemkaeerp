package com.eventverse.app.infrastructure.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

/**
 * Append-only record of platform-level actions taken on a tenant's data — most importantly
 * a superadmin changing another factory's module entitlements or subscription tier.
 */
object AuditLogsTable : Table("audit_logs") {
    val id = varchar("id", 64)
    val actorUserId = varchar("actor_user_id", 64)
    val actorRole = varchar("actor_role", 30)

    /**
     * Named `tenant_id` (not `target_tenant_id`) to match [apply_tenant_rls]'s hardcoded
     * column name, same as every other tenant-scoped table in this schema.
     */
    val tenantId = varchar("tenant_id", 64).references(TenantsTable.id)

    val action = varchar("action", 60)
    val summary = text("summary")
    val occurredAt = timestamp("occurred_at")

    override val primaryKey = PrimaryKey(id)
}

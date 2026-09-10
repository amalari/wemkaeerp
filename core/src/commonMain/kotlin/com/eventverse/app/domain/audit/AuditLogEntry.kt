package com.eventverse.app.domain.audit

import com.eventverse.app.domain.auth.Role
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Instant

/**
 * One recorded instance of a platform superadmin acting on a tenant's data.
 *
 * A superadmin can act as any tenant via [com.eventverse.app.domain.auth.Role.PLATFORM_SUPERADMIN]
 * — that capability only stays legitimate for as long as every use of it leaves a trail the
 * affected factory (or another operator) can review afterwards.
 */
data class AuditLogEntry(
    val id: String,
    val actorUserId: String,
    val actorRole: Role,
    val targetTenantId: TenantId,
    val action: AuditAction,
    /** Human-readable description of what changed, safe to show verbatim in an audit view. */
    val summary: String,
    val occurredAt: Instant
) {
    init {
        require(id.isNotBlank()) { "AuditLogEntry.id cannot be blank" }
        require(actorUserId.isNotBlank()) { "AuditLogEntry.actorUserId cannot be blank" }
        require(summary.isNotBlank()) { "AuditLogEntry.summary cannot be blank" }
    }
}

package com.eventverse.app.domain.audit

/**
 * Platform-level actions worth recording for accountability.
 *
 * These are actions a superadmin takes *on a tenant's data* — the kind of action a factory
 * owner cannot see happening from inside their own workspace, so there has to be a durable
 * trail of who did what and when.
 */
enum class AuditAction(val code: String) {
    TENANT_ENTITLEMENT_UPDATED("tenant_entitlement_updated"),
    TENANT_TIER_UPDATED("tenant_tier_updated");

    companion object {
        fun fromCode(code: String?): AuditAction? = entries.firstOrNull { it.code == code }
    }
}

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
    TENANT_TIER_UPDATED("tenant_tier_updated"),

    /** Vertikal (Domain Pack) sebuah tenant ditetapkan (B7): mengubah seluruh kosakata modul tenant itu. */
    TENANT_DOMAIN_PACK_ASSIGNED("tenant_domain_pack_assigned"),

    /**
     * A build was closed with its actual hours and cost.
     *
     * Recorded because that cost figure is what a subscription price is derived from, and a price
     * runs for the length of a contract. If the number is ever disputed, there has to be a record
     * of who entered it and when.
     */
    MODULE_BUILD_RECORDED("module_build_recorded"),

    /** A monthly price was issued for a module. Same reason: money, and it outlives the session. */
    MODULE_PRICE_QUOTED("module_price_quoted");

    companion object {
        fun fromCode(code: String?): AuditAction? = entries.firstOrNull { it.code == code }
    }
}

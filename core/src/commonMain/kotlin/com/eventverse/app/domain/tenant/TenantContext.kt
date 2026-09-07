package com.eventverse.app.domain.tenant

/**
 * Value context representing the active tenant within the current execution scope.
 */
data class TenantContext(
    val tenantId: TenantId,
    val slug: TenantSlug,
    val tier: SubscriptionTier,
    val isAccessible: Boolean
) {
    companion object {
        fun fromTenant(tenant: Tenant): TenantContext = TenantContext(
            tenantId = tenant.id,
            slug = tenant.slug,
            tier = tenant.tier,
            isAccessible = tenant.isAccessible
        )
    }
}

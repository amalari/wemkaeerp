package com.eventverse.app.domain.tenant

import com.eventverse.app.domain.pipeline.GarmentBusinessPreset
import com.eventverse.app.domain.pipeline.TenantModuleEntitlement

/**
 * Value context representing the active tenant within the current execution scope.
 */
data class TenantContext(
    val tenantId: TenantId,
    val slug: TenantSlug,
    val tier: SubscriptionTier,
    val isAccessible: Boolean,
    /**
     * The tenant's garment business model. Carried here so request handlers provision a new
     * pipeline from the factory's own model instead of falling back to a global default.
     */
    val businessPreset: GarmentBusinessPreset = GarmentBusinessPreset.DEFAULT
) {
    /** Which operational modules this tenant's subscription plan grants. */
    val moduleEntitlement: TenantModuleEntitlement get() = TenantModuleEntitlement.forTier(tier)

    companion object {
        fun fromTenant(tenant: Tenant): TenantContext = TenantContext(
            tenantId = tenant.id,
            slug = tenant.slug,
            tier = tenant.tier,
            isAccessible = tenant.isAccessible,
            businessPreset = tenant.businessPreset
        )
    }
}

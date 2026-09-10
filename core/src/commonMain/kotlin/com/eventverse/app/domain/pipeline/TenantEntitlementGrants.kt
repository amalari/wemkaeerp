package com.eventverse.app.domain.pipeline

import com.eventverse.app.domain.rbac.BusinessModule

/**
 * Per-tenant overrides on top of what the subscription plan grants by default.
 *
 * Exists because plan tier alone cannot express everything billing decides. A custom plugin
 * module in particular is provisioned to one specific factory, so the grant has to be
 * durable — deriving it from the tier would either forbid every custom module or allow all
 * of them.
 */
data class TenantEntitlementGrants(
    /**
     * Built-in modules this tenant may run. `null` means "whatever the plan tier grants by
     * default", which is the normal case; a non-null set narrows it.
     */
    val grantedModules: Set<BusinessModule>? = null,
    /** Custom plugin module ids explicitly provisioned to this tenant. */
    val grantedCustomModuleIds: Set<String> = emptySet()
) {
    val isEmpty: Boolean get() = grantedModules == null && grantedCustomModuleIds.isEmpty()

    fun grantCustomModule(moduleId: String): TenantEntitlementGrants {
        require(moduleId.isNotBlank()) { "Custom module id cannot be blank" }
        return copy(grantedCustomModuleIds = grantedCustomModuleIds + moduleId)
    }

    fun revokeCustomModule(moduleId: String): TenantEntitlementGrants =
        copy(grantedCustomModuleIds = grantedCustomModuleIds - moduleId)

    companion object {
        val NONE = TenantEntitlementGrants()
    }
}

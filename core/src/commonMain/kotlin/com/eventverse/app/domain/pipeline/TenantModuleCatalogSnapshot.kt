package com.eventverse.app.domain.pipeline

import com.eventverse.app.domain.tenant.SubscriptionTier

/**
 * The module catalogue as it stands for one tenant, together with the plan limits that
 * govern it. Returned by `GET /api/tenant/pipeline/modules`.
 */
data class TenantModuleCatalogSnapshot(
    val tier: SubscriptionTier,
    val maxActiveModules: Int,
    val allowsCustomPlugins: Boolean,
    val modules: List<TenantModuleAvailability>
) {
    val activeModuleCount: Int get() = modules.count { it.isActive }

    val installedModules: List<TenantModuleAvailability> get() = modules.filter { it.isInstalled }

    val remainingModuleSlots: Int
        get() = if (maxActiveModules == Int.MAX_VALUE) {
            Int.MAX_VALUE
        } else {
            (maxActiveModules - activeModuleCount).coerceAtLeast(0)
        }

    val hasReachedPlanLimit: Boolean get() = remainingModuleSlots == 0

    /** Modules blocked only by the subscription plan — the upgrade prompt. */
    val planBlockedModules: List<TenantModuleAvailability>
        get() = modules.filter { it.requiresPlanUpgrade }

    companion object {
        val EMPTY = TenantModuleCatalogSnapshot(
            tier = SubscriptionTier.PRO,
            maxActiveModules = 0,
            allowsCustomPlugins = false,
            modules = emptyList()
        )
    }
}

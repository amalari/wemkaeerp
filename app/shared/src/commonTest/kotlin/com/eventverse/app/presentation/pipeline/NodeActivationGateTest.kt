package com.eventverse.app.presentation.pipeline

import com.eventverse.app.domain.pack.GarmentSlots
import com.eventverse.app.domain.pipeline.TenantModuleAvailability
import com.eventverse.app.domain.pipeline.TenantModuleCatalogSnapshot
import com.eventverse.app.domain.tenant.SubscriptionTier
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NodeActivationGateTest {

    private fun module(id: String, active: Boolean, granted: Boolean) = TenantModuleAvailability(
        moduleId = id, displayName = id, tenantDisplayName = null, archetype = GarmentSlots.forModuleCode(id),
        standardModule = null, isCustomPlugin = false, isInstalled = true, isActive = active,
        isGrantedByPlan = granted, isRecommendedForPreset = false
    )

    private fun catalog(max: Int, vararg modules: TenantModuleAvailability) = TenantModuleCatalogSnapshot(
        tier = SubscriptionTier.PRO, maxActiveModules = max, allowsCustomPlugins = false, modules = modules.toList()
    )

    @Test
    fun canActivate_grantedModuleWithFreeSlot_isAllowed() {
        val c = catalog(2, module("crm_sales", active = false, granted = true), module("inventory", active = true, granted = true))
        assertTrue(c.canActivateFromCanvas("crm_sales"))
    }

    @Test
    fun canActivate_whenPlanQuotaIsFull_isRefused() {
        val c = catalog(1, module("crm_sales", active = false, granted = true), module("inventory", active = true, granted = true))
        assertFalse(c.canActivateFromCanvas("crm_sales"))
    }

    @Test
    fun canActivate_moduleOutsidePlan_isRefused() {
        val c = catalog(5, module("fulfillment", active = false, granted = false))
        assertFalse(c.canActivateFromCanvas("fulfillment"))
    }

    @Test
    fun canActivate_unknownModule_isRefused() {
        assertFalse(catalog(5).canActivateFromCanvas("tidak_ada"))
    }
}

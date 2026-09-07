package com.eventverse.app.domain.tenant

import kotlin.test.*

class TenantTest {

    @Test
    fun valid_tenant_slug_creation_should_succeed() {
        val slug = TenantSlug("garment-wemade")
        assertEquals("garment-wemade", slug.value)
    }

    @Test
    fun invalid_tenant_slug_with_uppercase_should_fail() {
        assertFailsWith<IllegalArgumentException> {
            TenantSlug("GarmentWemade")
        }
    }

    @Test
    fun reserved_subdomain_should_be_forbidden() {
        val exception = assertFailsWith<IllegalArgumentException> {
            TenantSlug("admin")
        }
        assertTrue(exception.message?.contains("reserved system keyword") == true)
    }

    @Test
    fun tenant_initial_trial_status_should_be_accessible() {
        val tenant = Tenant(
            id = TenantId("ten-101"),
            slug = TenantSlug("konveksi-berkah"),
            name = TenantName("PT Berkah Konveksi"),
            status = TenantStatus.TRIAL,
            tier = SubscriptionTier.PRO
        )

        assertTrue(tenant.isAccessible)
        assertTrue(tenant.canAccessPlatform())
    }

    @Test
    fun suspended_tenant_should_not_be_accessible() {
        val tenant = Tenant(
            id = TenantId("ten-101"),
            slug = TenantSlug("konveksi-berkah"),
            name = TenantName("PT Berkah Konveksi"),
            status = TenantStatus.ACTIVE,
            tier = SubscriptionTier.PRO
        ).suspend()

        assertEquals(TenantStatus.SUSPENDED, tenant.status)
        assertFalse(tenant.isAccessible)
    }

    @Test
    fun adding_machines_within_tier_limit_should_succeed() {
        val starterTenant = Tenant(
            id = TenantId("ten-starter"),
            slug = TenantSlug("starter-workshop"),
            name = TenantName("Workshop Starter"),
            tier = SubscriptionTier.STARTER // Max 5 machines
        )

        val updatedTenant = starterTenant.updateActiveMachineCount(4)
        assertEquals(4, updatedTenant.activeMachineCount)
        assertTrue(updatedTenant.canAddMachine())
    }

    @Test
    fun exceeding_machine_tier_limit_should_throw_exception() {
        val starterTenant = Tenant(
            id = TenantId("ten-starter"),
            slug = TenantSlug("starter-workshop"),
            name = TenantName("Workshop Starter"),
            tier = SubscriptionTier.STARTER // Max 5 machines
        )

        assertFailsWith<IllegalArgumentException> {
            starterTenant.updateActiveMachineCount(6)
        }
    }
}

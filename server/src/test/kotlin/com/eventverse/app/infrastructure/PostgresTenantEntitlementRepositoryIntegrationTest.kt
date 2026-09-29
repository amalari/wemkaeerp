package com.eventverse.app.infrastructure

import com.eventverse.app.domain.pack.GarmentBlueprints

import com.eventverse.app.domain.pipeline.TenantEntitlementGrants
import com.eventverse.app.domain.rbac.BusinessModule
import com.eventverse.app.domain.tenant.*
import kotlinx.coroutines.runBlocking
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Integration coverage for entitlement persistence against the local docker-compose
 * PostgreSQL, including the `JSONB` array columns.
 */
class PostgresTenantEntitlementRepositoryIntegrationTest {

    private lateinit var tenantRepo: PostgresTenantRepository
    private lateinit var entitlementRepo: PostgresTenantEntitlementRepository

    @BeforeTest
    fun setup() {
        DatabaseFactory.init()
        tenantRepo = PostgresTenantRepository()
        entitlementRepo = PostgresTenantEntitlementRepository()
    }

    private fun createTenant(): Tenant {
        val suffix = kotlin.math.abs(System.nanoTime() % 1_000_000).toString()
        val tenant = Tenant(
            id = TenantId("ten-ent-$suffix"),
            slug = TenantSlug("ent-$suffix"),
            name = TenantName("PT Entitlement $suffix"),
            status = TenantStatus.ACTIVE,
            tier = SubscriptionTier.ENTERPRISE,
            businessPreset = GarmentBlueprints.BRAND_D2C
        )
        runBlocking { tenantRepo.save(tenant).getOrThrow() }
        return tenant
    }

    @Test
    fun saveAndFind_shouldRoundTripCustomModuleGrants() = runBlocking<Unit> {
        val tenant = createTenant()
        val grants = TenantEntitlementGrants(
            grantedModules = null,
            grantedCustomModuleIds = setOf("sablon_bordir_custom", "integrasi_marketplace")
        )

        entitlementRepo.save(tenant.id, grants).getOrThrow()
        val fetched = entitlementRepo.findByTenantId(tenant.id)

        assertNotNull(fetched)
        assertNull(fetched.grantedModules, "null harus berarti 'semua modul bawaan sesuai paket'")
        assertEquals(grants.grantedCustomModuleIds, fetched.grantedCustomModuleIds)
    }

    @Test
    fun saveAndFind_shouldRoundTripNarrowedBuiltInCatalogue() = runBlocking<Unit> {
        val tenant = createTenant()
        val grants = TenantEntitlementGrants(
            grantedModules = setOf(BusinessModule.CRM_SALES, BusinessModule.OPERATOR_EXEC),
            grantedCustomModuleIds = emptySet()
        )

        entitlementRepo.save(tenant.id, grants).getOrThrow()
        val fetched = entitlementRepo.findByTenantId(tenant.id)

        assertNotNull(fetched)
        assertEquals(grants.grantedModules, fetched.grantedModules)
        assertTrue(fetched.grantedCustomModuleIds.isEmpty())
    }

    @Test
    fun save_calledTwice_shouldUpdateInPlaceNotDuplicate() = runBlocking<Unit> {
        val tenant = createTenant()

        entitlementRepo.save(
            tenant.id,
            TenantEntitlementGrants(grantedCustomModuleIds = setOf("plugin_a"))
        ).getOrThrow()
        entitlementRepo.save(
            tenant.id,
            TenantEntitlementGrants(grantedCustomModuleIds = setOf("plugin_a", "plugin_b"))
        ).getOrThrow()

        // A duplicate row would break findByTenantId, which requires a single result.
        val fetched = entitlementRepo.findByTenantId(tenant.id)
        assertNotNull(fetched)
        assertEquals(setOf("plugin_a", "plugin_b"), fetched.grantedCustomModuleIds)
    }

    @Test
    fun findByTenantId_forTenantWithoutOverrides_shouldReturnNull() = runBlocking<Unit> {
        val tenant = createTenant()

        assertNull(entitlementRepo.findByTenantId(tenant.id))
    }

    @Test
    fun seededD2cTenant_shouldAlreadyOwnItsPluginGrant() = runBlocking<Unit> {
        // V10 seeds a custom plugin into the D2C demo tenant's pipeline; V11 must record the
        // matching grant, otherwise that tenant cannot edit its own flow.
        val grants = entitlementRepo.findByTenantId(TenantId("ten-demo-d2c"))

        assertNotNull(grants, "Tenant demo D2C harus punya baris entitlement hasil seed")
        assertTrue(
            grants.grantedCustomModuleIds.contains("sablon_bordir_custom"),
            "Grant plugin sablon/bordir harus tersimpan"
        )
    }
}

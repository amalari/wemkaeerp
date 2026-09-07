package com.eventverse.app.infrastructure

import com.eventverse.app.domain.tenant.SubscriptionTier
import com.eventverse.app.domain.tenant.TenantContext
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.domain.tenant.TenantSlug
import kotlin.test.*

class TenantScopedRepositoryTest {

    private class SampleItem(val id: String, val tenantId: TenantId, val data: String)

    private class SampleRepository : TenantScopedRepository<SampleItem>() {
        private val items = mutableListOf<SampleItem>()

        fun addItem(context: TenantContext?, item: SampleItem) = withTenantScope(context) { tenantId ->
            validateTenantOwnership(item.tenantId, tenantId)
            items.add(item)
        }

        fun getItems(context: TenantContext?): List<SampleItem> = withTenantScope(context) { tenantId ->
            items.filter { it.tenantId == tenantId }
        }
    }

    private val tenantA = TenantContext(
        tenantId = TenantId("ten-a"),
        slug = TenantSlug("pabrik-a"),
        tier = SubscriptionTier.PRO,
        isAccessible = true
    )

    private val tenantB = TenantContext(
        tenantId = TenantId("ten-b"),
        slug = TenantSlug("pabrik-b"),
        tier = SubscriptionTier.PRO,
        isAccessible = true
    )

    private val suspendedTenant = TenantContext(
        tenantId = TenantId("ten-c"),
        slug = TenantSlug("pabrik-c"),
        tier = SubscriptionTier.STARTER,
        isAccessible = false
    )

    @Test
    fun operations_without_tenant_context_must_fail() {
        val repo = SampleRepository()
        assertFailsWith<IllegalStateException> {
            repo.getItems(null)
        }
    }

    @Test
    fun operations_with_suspended_tenant_must_fail() {
        val repo = SampleRepository()
        assertFailsWith<IllegalStateException> {
            repo.getItems(suspendedTenant)
        }
    }

    @Test
    fun items_are_strictly_isolated_between_tenants() {
        val repo = SampleRepository()

        repo.addItem(tenantA, SampleItem("1", tenantA.tenantId, "Data Pabrik A"))
        repo.addItem(tenantB, SampleItem("2", tenantB.tenantId, "Data Pabrik B"))

        val itemsA = repo.getItems(tenantA)
        assertEquals(1, itemsA.size)
        assertEquals("Data Pabrik A", itemsA.first().data)

        val itemsB = repo.getItems(tenantB)
        assertEquals(1, itemsB.size)
        assertEquals("Data Pabrik B", itemsB.first().data)
    }

    @Test
    fun cross_tenant_data_insertion_must_throw_security_exception() {
        val repo = SampleRepository()
        assertFailsWith<SecurityException> {
            // Tenant A context attempting to insert Tenant B's data
            repo.addItem(tenantA, SampleItem("3", tenantB.tenantId, "Illegal Data"))
        }
    }
}

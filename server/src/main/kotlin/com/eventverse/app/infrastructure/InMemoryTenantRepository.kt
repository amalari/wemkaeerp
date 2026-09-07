package com.eventverse.app.infrastructure

import com.eventverse.app.domain.tenant.*
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory thread-safe implementation of TenantRepository.
 * Useful for development, testing, and initial bootstrapping.
 */
class InMemoryTenantRepository : TenantRepository {
    private val tenants = ConcurrentHashMap<TenantId, Tenant>()

    init {
        // Seed default demo tenant for initial development
        val demoTenant = Tenant(
            id = TenantId("ten-demo-001"),
            slug = TenantSlug("wemade-demo"),
            name = TenantName("PT WeMade Convection Demo"),
            status = TenantStatus.ACTIVE,
            tier = SubscriptionTier.PRO,
            activeMachineCount = 10
        )
        tenants[demoTenant.id] = demoTenant
    }

    override suspend fun findById(id: TenantId): Tenant? = tenants[id]

    override suspend fun findBySlug(slug: TenantSlug): Tenant? =
        tenants.values.firstOrNull { it.slug == slug }

    override suspend fun save(tenant: Tenant): Result<Tenant> {
        tenants[tenant.id] = tenant
        return Result.success(tenant)
    }

    override suspend fun existsBySlug(slug: TenantSlug): Boolean =
        tenants.values.any { it.slug == slug }

    override suspend fun findAll(): List<Tenant> = tenants.values.toList()
}

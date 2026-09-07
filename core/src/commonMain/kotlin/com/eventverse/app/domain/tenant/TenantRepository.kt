package com.eventverse.app.domain.tenant

/**
 * Domain repository contract for Tenant management.
 * Implementation resides in infrastructure / server layer.
 */
interface TenantRepository {
    suspend fun findById(id: TenantId): Tenant?
    suspend fun findBySlug(slug: TenantSlug): Tenant?
    suspend fun save(tenant: Tenant): Result<Tenant>
    suspend fun existsBySlug(slug: TenantSlug): Boolean
    suspend fun findAll(): List<Tenant>
}

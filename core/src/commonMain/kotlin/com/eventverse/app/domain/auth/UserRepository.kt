package com.eventverse.app.domain.auth

import com.eventverse.app.domain.tenant.TenantId

/**
 * Domain repository contract for User management with tenant scoping.
 */
interface UserRepository {
    suspend fun findById(id: UserId): User?
    suspend fun findByUsername(tenantId: TenantId?, username: Username): User?
    suspend fun findByEmail(email: EmailAddress): User?
    suspend fun save(user: User): Result<User>
    suspend fun findAllByTenant(tenantId: TenantId): List<User>
}

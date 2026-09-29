package com.eventverse.app.domain.rbac

import com.eventverse.app.domain.tenant.TenantId

/**
 * Domain repository interface for RBAC CustomRole entity.
 * Pure Kotlin contract adhering to Domain-Driven Design rules.
 */
interface RoleRepository {
    suspend fun findById(tenantId: TenantId, id: RoleId): CustomRole?
    suspend fun findAllByTenant(tenantId: TenantId): List<CustomRole>
    suspend fun save(role: CustomRole): Result<CustomRole>
    suspend fun delete(tenantId: TenantId, id: RoleId): Result<Unit>
    suspend fun restoreDefaultPresets(tenantId: TenantId, pack: com.eventverse.app.domain.pack.DomainPack): Result<List<CustomRole>>
    suspend fun countUsersWithRole(tenantId: TenantId, id: RoleId): Int
}

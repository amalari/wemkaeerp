package com.eventverse.app.infrastructure

import com.eventverse.app.domain.rbac.CustomRole
import com.eventverse.app.domain.rbac.RoleId
import com.eventverse.app.domain.rbac.RoleRepository
import com.eventverse.app.domain.tenant.TenantId
import java.util.concurrent.ConcurrentHashMap

class InMemoryRoleRepository : RoleRepository {
    private val storage = ConcurrentHashMap<String, CustomRole>()
    private val userCounts = ConcurrentHashMap<String, Int>()

    override suspend fun findById(tenantId: TenantId, id: RoleId): CustomRole? =
        storage[key(tenantId, id)]

    override suspend fun findAllByTenant(tenantId: TenantId): List<CustomRole> =
        storage.values.filter { it.tenantId == tenantId }

    override suspend fun save(role: CustomRole): Result<CustomRole> {
        val tId = role.tenantId ?: error("TenantId required")
        storage[key(tId, role.id)] = role
        return Result.success(role)
    }

    override suspend fun delete(tenantId: TenantId, id: RoleId): Result<Unit> {
        storage.remove(key(tenantId, id))
        return Result.success(Unit)
    }

    override suspend fun restoreDefaultPresets(tenantId: TenantId): Result<List<CustomRole>> {
        val presets = CustomRole.createFactoryPresets(tenantId)
        presets.forEach { save(it) }
        return Result.success(presets)
    }

    override suspend fun countUsersWithRole(tenantId: TenantId, id: RoleId): Int =
        userCounts[key(tenantId, id)] ?: 0

    fun setUserCount(tenantId: TenantId, id: RoleId, count: Int) {
        userCounts[key(tenantId, id)] = count
    }

    private fun key(tenantId: TenantId, id: RoleId) = "${tenantId.value}:${id.value}"
}

package com.eventverse.app.domain.rbac.usecases

import com.eventverse.app.domain.rbac.CustomRole
import com.eventverse.app.domain.rbac.RoleId
import com.eventverse.app.domain.rbac.RoleRepository
import com.eventverse.app.domain.tenant.TenantId

class GetRolesUseCase(
    private val roleRepository: RoleRepository
) {
    suspend fun getAll(tenantId: TenantId): Result<List<CustomRole>> = runCatching {
        roleRepository.findAllByTenant(tenantId)
    }

    suspend fun getById(tenantId: TenantId, roleId: RoleId): Result<CustomRole?> = runCatching {
        roleRepository.findById(tenantId, roleId)
    }
}

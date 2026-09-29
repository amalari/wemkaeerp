package com.eventverse.app.domain.rbac.usecases

import com.eventverse.app.domain.pack.DomainPack
import com.eventverse.app.domain.rbac.CustomRole
import com.eventverse.app.domain.rbac.RoleRepository
import com.eventverse.app.domain.tenant.TenantId

class RestoreDefaultRolesUseCase(
    private val roleRepository: RoleRepository
) {
    suspend operator fun invoke(tenantId: TenantId, pack: DomainPack): Result<List<CustomRole>> = runCatching {
        roleRepository.restoreDefaultPresets(tenantId, pack).getOrThrow()
    }
}

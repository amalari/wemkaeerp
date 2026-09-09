package com.eventverse.app.domain.rbac.usecases

import com.eventverse.app.domain.rbac.RoleId
import com.eventverse.app.domain.rbac.RoleRepository
import com.eventverse.app.domain.tenant.TenantId

class DeleteRoleUseCase(
    private val roleRepository: RoleRepository
) {
    suspend operator fun invoke(tenantId: TenantId, roleId: RoleId): Result<Unit> = runCatching {
        val existingRole = roleRepository.findById(tenantId, roleId)
            ?: error("Jabatan dengan ID '${roleId.value}' tidak ditemukan")

        require(!existingRole.isSystemDefault) {
            "Jabatan bawaan sistem '${existingRole.name}' tidak dapat dihapus"
        }

        val assignedUsersCount = roleRepository.countUsersWithRole(tenantId, roleId)
        require(assignedUsersCount == 0) {
            "Tidak dapat menghapus jabatan '${existingRole.name}' karena masih digunakan oleh $assignedUsersCount pengguna"
        }

        roleRepository.delete(tenantId, roleId).getOrThrow()
    }
}

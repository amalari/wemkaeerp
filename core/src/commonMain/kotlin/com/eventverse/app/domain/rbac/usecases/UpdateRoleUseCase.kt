package com.eventverse.app.domain.rbac.usecases

import com.eventverse.app.domain.rbac.*
import com.eventverse.app.domain.tenant.TenantId

data class UpdateRoleCommand(
    val tenantId: TenantId,
    val roleId: RoleId,
    val name: String? = null,
    val description: String? = null,
    val modulePermissions: Map<BusinessModule, ModuleAccessConfig>? = null
)

class UpdateRoleUseCase(
    private val roleRepository: RoleRepository
) {
    suspend operator fun invoke(command: UpdateRoleCommand): Result<CustomRole> = runCatching {
        val existingRole = roleRepository.findById(command.tenantId, command.roleId)
            ?: error("Jabatan dengan ID '${command.roleId.value}' tidak ditemukan")

        var updated = existingRole

        if (command.name != null || command.description != null) {
            val newName = command.name?.trim() ?: existingRole.name
            val newDesc = command.description?.trim() ?: existingRole.description
            updated = updated.updateMetadata(newName, newDesc)
        }

        if (command.modulePermissions != null) {
            updated = updated.copy(modulePermissions = command.modulePermissions)
        }

        roleRepository.save(updated).getOrThrow()
    }
}

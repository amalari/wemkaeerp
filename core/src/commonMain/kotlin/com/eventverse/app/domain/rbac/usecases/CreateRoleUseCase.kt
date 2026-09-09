package com.eventverse.app.domain.rbac.usecases

import com.eventverse.app.domain.rbac.*
import com.eventverse.app.domain.tenant.TenantId

data class CreateRoleCommand(
    val tenantId: TenantId,
    val name: String,
    val description: String = "",
    val modulePermissions: Map<BusinessModule, ModuleAccessConfig> = emptyMap()
)

class CreateRoleUseCase(
    private val roleRepository: RoleRepository
) {
    suspend operator fun invoke(command: CreateRoleCommand): Result<CustomRole> = runCatching {
        val trimmedName = command.name.trim()
        require(trimmedName.isNotBlank()) { "Nama jabatan tidak boleh kosong" }

        val slug = trimmedName.lowercase()
            .replace("[^a-z0-9]+".toRegex(), "-")
            .trim('-')
            .ifBlank { "custom" }

        val prefix = if (command.tenantId.value == "ten-demo-001") "" else "${command.tenantId.value}-"
        val roleId = RoleId("role-${prefix}$slug-${(100..999).random()}")

        val role = CustomRole(
            id = roleId,
            tenantId = command.tenantId,
            name = trimmedName,
            description = command.description.trim(),
            isSystemDefault = false,
            modulePermissions = command.modulePermissions,
            userCount = 0
        )

        roleRepository.save(role).getOrThrow()
    }
}

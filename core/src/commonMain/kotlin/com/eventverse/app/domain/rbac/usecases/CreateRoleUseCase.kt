package com.eventverse.app.domain.rbac.usecases

import com.eventverse.app.domain.rbac.isScopeSupported

import com.eventverse.app.domain.rbac.isFoundation

import com.eventverse.app.domain.rbac.isOperational

import com.eventverse.app.domain.rbac.isGovernance

import com.eventverse.app.domain.rbac.isHierarchical

import com.eventverse.app.domain.rbac.isGlobalOnly


import com.eventverse.app.domain.rbac.supportedScopes

import com.eventverse.app.domain.rbac.kind

import com.eventverse.app.domain.rbac.scopeCapability

import com.eventverse.app.domain.rbac.iconKey

import com.eventverse.app.domain.rbac.description

import com.eventverse.app.domain.rbac.displayName

import com.eventverse.app.domain.rbac.name

import com.eventverse.app.domain.rbac.code

import com.eventverse.app.domain.rbac.*
import com.eventverse.app.domain.tenant.TenantId

data class CreateRoleCommand(
    val tenantId: TenantId,
    val name: String,
    val description: String = "",
    val modulePermissions: Map<BusinessModule, ModuleAccessConfig> = emptyMap(),
    /**
     * Divisi pemilik jabatan ini. Null berarti jabatan lintas divisi (mis. direksi).
     *
     * Tanpa ini, jabatan yang baru dibuat admin selamanya tak punya divisi — dan jalur wewenang
     * lewat divisi tidak akan pernah berlaku baginya, meski admin sudah mengatur penugasan
     * divisinya. Gejalanya membingungkan: jabatan lama bekerja, jabatan baru tidak.
     */
    val departmentId: String? = null
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
            userCount = 0,
            departmentId = command.departmentId?.takeIf { it.isNotBlank() }
        )

        roleRepository.save(role).getOrThrow()
    }
}

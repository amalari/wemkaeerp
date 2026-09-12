package com.eventverse.app.domain.auth

import com.eventverse.app.domain.tenant.TenantId

/**
 * Core User entity with multi-tenant ownership and Role-Based Access Control (RBAC).
 */
data class User(
    val id: UserId,
    val tenantId: TenantId?,
    val username: Username,
    val email: EmailAddress,
    val role: Role,
    val customPermissions: Set<Permission> = emptySet(),
    val isActive: Boolean = true,
    /**
     * Divisi tempat pengguna bekerja, sebagaimana dikonfigurasi tenant.
     *
     * Disimpan sebagai id mentah, bukan `DepartmentId`, supaya lapisan autentikasi tidak
     * menarik ketergantungan ke agregat Org Chart. Null untuk pengguna platform.
     */
    val departmentId: String? = null,
    /**
     * Jabatan rakitan tenant (`custom_roles.id`).
     *
     * [role] di atas adalah enum tetap milik platform — ia menjawab "boleh menyentuh apa di
     * tingkat sistem". Kolom ini menjawab "melihat modul apa di layar", dan hanya tenant yang
     * menentukannya. Keduanya hidup berdampingan; tidak satu pun menggantikan yang lain.
     */
    val customRoleId: String? = null
) {
    init {
        if (role != Role.PLATFORM_SUPERADMIN) {
            require(tenantId != null) { "TenantId is required for tenant user role: ${role.name}" }
        }
    }

    val effectivePermissions: Set<Permission>
        get() = role.defaultPermissions + customPermissions

    fun hasPermission(permission: Permission): Boolean {
        if (!isActive) return false
        return effectivePermissions.contains(permission)
    }

    fun activate(): User = copy(isActive = true)

    fun deactivate(): User = copy(isActive = false)

    fun updateRole(newRole: Role): User = copy(role = newRole)

    fun addCustomPermission(permission: Permission): User =
        copy(customPermissions = customPermissions + permission)

    fun removeCustomPermission(permission: Permission): User =
        copy(customPermissions = customPermissions - permission)
}

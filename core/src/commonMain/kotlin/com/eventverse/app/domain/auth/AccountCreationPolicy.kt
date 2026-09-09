package com.eventverse.app.domain.auth

import com.eventverse.app.domain.orgchart.Department
import com.eventverse.app.domain.orgchart.HierarchyLevel
import com.eventverse.app.domain.orgchart.OrgNode
import com.eventverse.app.domain.orgchart.OrgNodeId

/**
 * Minimal employee account model.
 * Contains only the essential fields needed for login and factory operations.
 */
data class SimpleEmployeeAccount(
    val email: EmailAddress,
    val name: String,
    val phone: String = "",
    val department: Department,
    val level: HierarchyLevel,
    val roleTitle: String,
    val reportsToId: OrgNodeId? = null
) {
    init {
        require(name.isNotBlank()) { "Employee name cannot be blank" }
        require(roleTitle.isNotBlank()) { "Role title cannot be blank" }
    }
}

/**
 * Domain policy enforcing rules for account provisioning:
 * 1. Owner & Tenant Admin can create any account across all departments and levels.
 * 2. Department Head can ONLY create subordinate staff under their own department.
 * 3. Regular Staff Operator cannot create accounts.
 */
object AccountCreationPolicy {

    fun validateCreation(
        creatorRole: Role,
        creatorNode: OrgNode?,
        target: SimpleEmployeeAccount
    ): Result<Unit> = runCatching {
        // 1. Owner or Tenant Admin has full unrestricted creation privileges
        if (creatorRole == Role.PLATFORM_SUPERADMIN || creatorRole == Role.TENANT_ADMIN) {
            return@runCatching
        }

        // 2. If creator is a Department Head
        if (creatorNode != null && creatorNode.level == HierarchyLevel.HEAD_OF_DEPARTMENT) {
            require(target.department == creatorNode.department) {
                "Kepala Divisi hanya diizinkan membuat akun untuk divisinya sendiri (${creatorNode.department?.displayName ?: ""}), bukan ${target.department?.displayName ?: ""}."
            }
            require(target.level == HierarchyLevel.STAFF_OPERATOR) {
                "Kepala Divisi hanya diizinkan membuat akun untuk tingkat Staf Pelaksana, bukan ${target.level.displayName}."
            }
            require(target.reportsToId == creatorNode.id) {
                "Akun staf baru yang dibuat oleh Kepala Divisi wajib melapor langsung ke Kepala Divisi bersangkutan."
            }
            return@runCatching
        }

        // 3. Regular staff or unauthorized roles are forbidden
        error("Pengguna dengan peran '${creatorRole.name}' tidak memiliki wewenang untuk membuat akun karyawan baru.")
    }
}

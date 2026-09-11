package com.eventverse.app.domain.rbac

/**
 * Domain entity representing an assignment of a [BusinessModule] to a Department (Divisi)
 * with optional specific Roles (Jabatan).
 *
 * If [specificRoleIds] is empty, it means ALL positions/roles in that department
 * ("Per Divisi - Seluruh Jabatan") receive this module access.
 */
data class DepartmentModuleAssignment(
    val departmentId: String,
    val departmentName: String,
    val accessLevel: AccessLevel = AccessLevel.OPERATE,
    val specificRoleIds: Set<String> = emptySet(),
    val scope: DataScope = DataScope.ALL_TENANT_DATA,
    val id: String = ""
) {
    val appliesToAllRoles: Boolean get() = specificRoleIds.isEmpty()

    val assignmentKey: String
        get() = if (id.isNotBlank()) id else "${departmentId}_${if (appliesToAllRoles) "all" else specificRoleIds.sorted().joinToString("_")}"

    fun updateAccess(
        level: AccessLevel,
        newScope: DataScope = scope,
        roleIds: Set<String> = specificRoleIds
    ): DepartmentModuleAssignment {
        return copy(
            accessLevel = level,
            scope = newScope,
            specificRoleIds = roleIds
        )
    }
}

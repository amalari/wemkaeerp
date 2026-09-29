package com.eventverse.app.infrastructure.tables

import org.jetbrains.exposed.sql.Table

object CustomRolesTable : Table("dynamic_rbac.custom_roles") {
    val id = varchar("id", 64)
    val tenantId = varchar("tenant_id", 64).references(TenantsTable.id)
    val name = varchar("name", 100)
    val description = text("description").default("")
    val isSystemDefault = bool("is_system_default").default(false)
    /** RBAC matrix. `JSONB` in the schema — see [jsonbText]. */
    val modulePermissions = jsonbText("module_permissions").default("{}")
    val userCount = integer("user_count").default(0)

    /** Divisi pemilik jabatan ini. Dibutuhkan mesin keputusan akses untuk menyatukan hak role dan divisi. */
    val departmentId = varchar("department_id", 64).references(DepartmentsTable.id).nullable()

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("uq_tenant_role_name", tenantId, name)
    }
}

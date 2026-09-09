package com.eventverse.app.infrastructure.tables

import org.jetbrains.exposed.sql.Table

object CustomRolesTable : Table("custom_roles") {
    val id = varchar("id", 64)
    val tenantId = varchar("tenant_id", 64).references(TenantsTable.id)
    val name = varchar("name", 100)
    val description = text("description").default("")
    val isSystemDefault = bool("is_system_default").default(false)
    val modulePermissions = text("module_permissions").default("{}")
    val userCount = integer("user_count").default(0)

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("uq_tenant_role_name", tenantId, name)
    }
}

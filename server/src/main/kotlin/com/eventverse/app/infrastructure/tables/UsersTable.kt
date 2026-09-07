package com.eventverse.app.infrastructure.tables

import org.jetbrains.exposed.sql.Table

object UsersTable : Table("users") {
    val id = varchar("id", 64)
    val tenantId = varchar("tenant_id", 64).references(TenantsTable.id).nullable()
    val username = varchar("username", 50)
    val email = varchar("email", 150).uniqueIndex()
    val role = varchar("role", 50)
    val isActive = bool("is_active").default(true)

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("uq_tenant_username", tenantId, username)
    }
}

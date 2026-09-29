package com.eventverse.app.infrastructure.tables

import org.jetbrains.exposed.sql.Table

object DepartmentsTable : Table("org_chart.departments") {
    val id = varchar("id", 64)
    val tenantId = varchar("tenant_id", 64).references(TenantsTable.id)
    val code = varchar("code", 50)
    val displayName = varchar("display_name", 100)
    val shortName = varchar("short_name", 50)
    val colorHex = long("color_hex")
    val isCustom = bool("is_custom").default(false)
    val archivedAt = varchar("archived_at", 50).nullable()

    override val primaryKey = PrimaryKey(id)
}

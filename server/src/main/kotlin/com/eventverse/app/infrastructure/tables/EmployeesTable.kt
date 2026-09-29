package com.eventverse.app.infrastructure.tables

import org.jetbrains.exposed.sql.Table

object EmployeesTable : Table("org_chart.employees") {
    val id = varchar("id", 64)
    val tenantId = varchar("tenant_id", 64).references(TenantsTable.id)
    val name = varchar("name", 100)
    val email = varchar("email", 150)
    val departmentId = varchar("department_id", 64).references(DepartmentsTable.id).nullable()
    val level = varchar("level", 50)
    val roleTitle = varchar("role_title", 100)
    val reportsToId = varchar("reports_to_id", 64).references(EmployeesTable.id).nullable()
    val phone = varchar("phone", 50).default("")
    val archivedAt = varchar("archived_at", 50).nullable()

    override val primaryKey = PrimaryKey(id)
}

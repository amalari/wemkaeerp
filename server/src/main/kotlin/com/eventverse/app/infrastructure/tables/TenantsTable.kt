package com.eventverse.app.infrastructure.tables

import org.jetbrains.exposed.sql.Table

object TenantsTable : Table("tenants") {
    val id = varchar("id", 64)
    val slug = varchar("slug", 30).uniqueIndex()
    val name = varchar("name", 100)
    val status = varchar("status", 20).default("TRIAL")
    val tier = varchar("tier", 20).default("PRO")
    val activeMachineCount = integer("active_machine_count").default(0)

    override val primaryKey = PrimaryKey(id)
}

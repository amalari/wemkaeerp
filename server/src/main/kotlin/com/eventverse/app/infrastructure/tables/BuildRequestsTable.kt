package com.eventverse.app.infrastructure.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

/** Antrian Pembuatan (V83, FR-M2-4) — milik tenant, dikelola superadmin. */
object BuildRequestsTable : Table("builder.build_requests") {
    val id = varchar("id", 140)
    val tenantId = varchar("tenant_id", 64).references(TenantsTable.id)
    val moduleId = varchar("module_id", 80)
    val reason = text("reason")
    val status = varchar("status", 20)
    val quoteId = varchar("quote_id", 64).nullable()
    val deploymentId = varchar("deployment_id", 80).nullable()
    val createdAt = timestamp("created_at")

    override val primaryKey = PrimaryKey(id)
}

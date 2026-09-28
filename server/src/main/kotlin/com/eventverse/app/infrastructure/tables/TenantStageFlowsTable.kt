package com.eventverse.app.infrastructure.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

/** Kerangka tahap alur per tenant (V72). Satu baris per tenant; `stages` ditulis utuh. */
object TenantStageFlowsTable : Table("tenant_stage_flows") {
    val tenantId = varchar("tenant_id", 64).references(TenantsTable.id)
    val templateCode = varchar("template_code", 32)
    val stages = jsonbText("stages")
    val updatedAt = timestamp("updated_at")

    override val primaryKey = PrimaryKey(tenantId)
}

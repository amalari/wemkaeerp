package com.eventverse.app.infrastructure.tables

import org.jetbrains.exposed.sql.Table

object TenantPipelinesTable : Table("factory_flow.tenant_pipelines") {
    val id = varchar("id", 64)
    val tenantId = varchar("tenant_id", 64).references(TenantsTable.id)
    val pipelineName = varchar("pipeline_name", 100)
    val basePreset = varchar("base_preset", 50).nullable()

    /** Node/edge topology of the tenant's workflow. `JSONB` in the schema — see [jsonbText]. */
    val graphData = jsonbText("graph_data").default("{}")

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("uq_tenant_pipeline", tenantId)
    }
}

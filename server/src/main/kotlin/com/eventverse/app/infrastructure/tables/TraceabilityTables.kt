package com.eventverse.app.infrastructure.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

/**
 * Pemetaan Exposed untuk telusur QR. Cermin migrasi V52.
 *
 * `tenantId` muncul juga di tabel tautan dan tally walau secara relasional berlebihan: tanpa kolom
 * itu, Row Level Security tidak punya apa pun untuk difilter dan isinya bocor lintas tenant.
 */
object TraceWorkOrdersTable : Table("trace_work_orders") {
    val id = varchar("id", 64)
    val tenantId = varchar("tenant_id", 64).references(TenantsTable.id)
    val ordinal = integer("ordinal")
    val workOrderKind = varchar("work_order_kind", 10)
    val samplingOrderId = varchar("sampling_order_id", 64).references(SamplingOrdersTable.id).nullable()
    val bulkWorkOrderId = varchar("bulk_work_order_id", 64).nullable()
    val sizesJson = jsonbText("sizes_json").default("[]")
    val createdAt = timestamp("created_at")

    override val primaryKey = PrimaryKey(id)
}

object TraceTenantOrdinalsTable : Table("trace_tenant_ordinals") {
    val tenantId = varchar("tenant_id", 64)
    val ordinal = integer("ordinal")
    val createdAt = timestamp("created_at")

    override val primaryKey = PrimaryKey(tenantId)
}

object TraceContainersTable : Table("trace_containers") {
    val id = varchar("id", 64)
    val tenantId = varchar("tenant_id", 64).references(TenantsTable.id)
    val code = varchar("code", 32)
    val workOrderKind = varchar("work_order_kind", 10)
    val workOrderId = varchar("work_order_id", 64)
    val tier = varchar("tier", 20)
    val sizeLabel = varchar("size_label", 60)
    val colorway = varchar("colorway", 120).default("")
    val state = varchar("state", 20).default("OPENED")
    val declaredPcs = integer("declared_pcs").default(0)
    val weightKg = double("weight_kg").default(0.0)
    val operatorName = varchar("operator_name", 150).default("")
    val shiftLabel = varchar("shift_label", 40).default("")
    val recordedAt = timestamp("recorded_at")
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
    val notes = text("notes").default("")

    override val primaryKey = PrimaryKey(id)
}

object TraceContainerPanelTalliesTable : Table("trace_container_panel_tallies") {
    val containerId = varchar("container_id", 64).references(TraceContainersTable.id)
    val tenantId = varchar("tenant_id", 64)
    val panel = varchar("panel", 40)
    val pieces = integer("pieces").default(0)

    override val primaryKey = PrimaryKey(containerId, panel)
}

object TraceContainerLinksTable : Table("trace_container_links") {
    val parentId = varchar("parent_id", 64).references(TraceContainersTable.id)
    val childId = varchar("child_id", 64).references(TraceContainersTable.id)
    val tenantId = varchar("tenant_id", 64)
    val consumedPcs = integer("consumed_pcs")
    val linkedAt = timestamp("linked_at")

    override val primaryKey = PrimaryKey(parentId, childId)
}

object TraceAllocationsTable : Table("trace_allocations") {
    val id = varchar("id", 64)
    val tenantId = varchar("tenant_id", 64).references(TenantsTable.id)
    val workOrderKind = varchar("work_order_kind", 10)
    val workOrderId = varchar("work_order_id", 64)
    val setsPerBundle = integer("sets_per_bundle").default(20)
    val pcsPerSack = integer("pcs_per_sack").default(60)
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")

    override val primaryKey = PrimaryKey(id)
}

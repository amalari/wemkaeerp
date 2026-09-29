package com.eventverse.app.infrastructure.tables

import com.eventverse.app.domain.rbac.isScopeSupported

import com.eventverse.app.domain.rbac.isFoundation

import com.eventverse.app.domain.rbac.isOperational

import com.eventverse.app.domain.rbac.isGovernance

import com.eventverse.app.domain.rbac.isHierarchical

import com.eventverse.app.domain.rbac.isGlobalOnly


import com.eventverse.app.domain.rbac.supportedScopes

import com.eventverse.app.domain.rbac.kind

import com.eventverse.app.domain.rbac.scopeCapability

import com.eventverse.app.domain.rbac.iconKey

import com.eventverse.app.domain.rbac.description

import com.eventverse.app.domain.rbac.displayName

import com.eventverse.app.domain.rbac.name

import com.eventverse.app.domain.rbac.code

import com.eventverse.app.domain.pack.GarmentModules

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.date
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

/**
 * Exposed mapping untuk `GarmentModules.PRODUCTION_MRP`.
 * Memetakan 1-ke-1 ke tabel migrasi V42.
 */
object BulkWorkOrdersTable : Table("production_mrp.bulk_work_orders") {
    val id = varchar("id", 64)
    val tenantId = varchar("tenant_id", 64).references(TenantsTable.id)
    val spkNumber = varchar("spk_number", 50)

    val clientName = varchar("client_name", 150).default("")
    val styleName = varchar("style_name", 150).default("")
    val status = varchar("status", 30).default("DRAFT")

    val dealId = varchar("deal_id", 64).references(DealsTable.id).nullable()
    val goldenSampleOrderId = varchar("golden_sample_order_id", 64)
        .references(SamplingOrdersTable.id).nullable()

    val stockOwnership = varchar("stock_ownership", 40).default("OWNED_RAW_MATERIAL")

    val sizeBreakdown = jsonbText("size_breakdown").default("[]")
    val sizeLabel = varchar("size_label", 60).nullable()
    val lineAllocations = jsonbText("line_allocations").default("[]")
    val stageProgress = jsonbText("stage_progress").default("[]")

    val targetOutputPerDay = integer("target_output_per_day").default(0)
    val plannedStartDate = date("planned_start_date").nullable()
    val plannedFinishDate = date("planned_finish_date").nullable()
    val notes = text("notes").default("")

    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
    val releasedAt = timestamp("released_at").nullable()
    val archivedAt = timestamp("archived_at").nullable()

    override val primaryKey = PrimaryKey(id)
}

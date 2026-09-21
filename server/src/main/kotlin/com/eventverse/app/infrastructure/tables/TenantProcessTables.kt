package com.eventverse.app.infrastructure.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

/**
 * Pemetaan Exposed untuk katalog proses opsional per tenant. Cermin V56.
 *
 * Setiap baris = satu tahapan opsional (Bordir, Sablon, dst.) milik satu tenant,
 * beserta jangkar posisinya di flow sampling dan/atau line workqueue.
 */
object TenantOptionalProcessesTable : Table("tenant_optional_processes") {
    val id = varchar("id", 64)
    val tenantId = varchar("tenant_id", 64).references(TenantsTable.id)
    val processCode = varchar("process_code", 64)
    val displayName = varchar("display_name", 150)
    val archetype = varchar("archetype", 40)
    val samplingAnchorAfter = varchar("sampling_anchor_after", 40).nullable()
    val stationAnchorAfter = varchar("station_anchor_after", 50).nullable()
    val executionMode = varchar("execution_mode", 30).default("IN_HOUSE")
    val vendorRef = varchar("vendor_ref", 150).nullable()
    val piecerateTariffIdr = long("piecerate_tariff_idr").default(0)
    val standardMinutesPerPiece = double("standard_minutes_per_piece").default(0.0)
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")

    override val primaryKey = PrimaryKey(id)
}
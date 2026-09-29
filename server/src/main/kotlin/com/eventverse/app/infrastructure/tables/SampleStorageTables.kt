package com.eventverse.app.infrastructure.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

/** Kustodi penyimpanan sampel. Cermin V69. */
object SampleStorageRecordsTable : Table("sampling_order.sample_storage_records") {
    val id = varchar("id", 64)
    val tenantId = varchar("tenant_id", 64).references(TenantsTable.id)
    val samplingOrderId = varchar("sampling_order_id", 64)
    val dealId = varchar("deal_id", 64).nullable()
    val locationLabel = varchar("location_label", 80)
    val qtyPcs = integer("qty_pcs")
    val storedByEmail = varchar("stored_by_email", 255).default("")
    val storedByName = varchar("stored_by_name", 255).default("")
    val storedAt = timestamp("stored_at")
    val releasedByEmail = varchar("released_by_email", 255).nullable()
    val releasedByName = varchar("released_by_name", 255).nullable()
    val releasedAt = timestamp("released_at").nullable()
    val partialReason = text("partial_reason").nullable()

    override val primaryKey = PrimaryKey(id)
}

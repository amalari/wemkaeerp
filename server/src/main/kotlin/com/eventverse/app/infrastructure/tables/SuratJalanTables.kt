package com.eventverse.app.infrastructure.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.date
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

/**
 * Pemetaan Exposed untuk lokasi fisik multi-site tenant. Cermin V55.
 */
object TenantLocationsTable : Table("factory_flow.tenant_locations") {
    val id = varchar("id", 64)
    val tenantId = varchar("tenant_id", 64).references(TenantsTable.id)
    val name = varchar("name", 150)
    val code = varchar("code", 50)
    val address = text("address").default("")
    val isActive = bool("is_active").default(true)
    val createdAt = timestamp("created_at")

    override val primaryKey = PrimaryKey(id)
}

/**
 * Pemetaan Exposed untuk manifes Surat Jalan. Cermin V55.
 */
object SuratJalanManifestsTable : Table("fulfillment.surat_jalan_manifests") {
    val id = varchar("id", 64)
    val tenantId = varchar("tenant_id", 64).references(TenantsTable.id)
    val sjNumber = varchar("sj_number", 100)
    val transferType = varchar("transfer_type", 40)
    val subjectKind = varchar("subject_kind", 30)
    val subjectId = varchar("subject_id", 64)
    val orderNumber = varchar("order_number", 100)
    val articleName = varchar("article_name", 150)
    val originLocationId = varchar("origin_location_id", 64).nullable()
    val destinationLocationId = varchar("destination_location_id", 64).nullable()
    val vendorRef = varchar("vendor_ref", 150).nullable()
    val customerName = varchar("customer_name", 150).nullable()
    val customerAddress = text("customer_address").nullable()
    val carrierName = varchar("carrier_name", 100).nullable()
    val driverName = varchar("driver_name", 100).nullable()
    val vehiclePlate = varchar("vehicle_plate", 50).nullable()
    val status = varchar("status", 30).default("DRAFT")

    /** Leg alur yang dokumen ini layani. NULL untuk penerbitan manual. Cermin V58. */
    val legKey = varchar("leg_key", 120).nullable()
    val unitServiceFeeIdr = long("unit_service_fee_idr").default(0L)
    val expectedReturnDate = date("expected_return_date").nullable()
    val dispatchedAt = timestamp("dispatched_at").nullable()
    val receivedAt = timestamp("received_at").nullable()
    val notes = text("notes").default("")

    override val primaryKey = PrimaryKey(id)
}

/**
 * Pemetaan Exposed untuk baris rincian barang Surat Jalan. Cermin V55.
 */
object SuratJalanItemsTable : Table("fulfillment.surat_jalan_items") {
    val id = varchar("id", 64)
    val manifestId = varchar("manifest_id", 64).references(SuratJalanManifestsTable.id)
    val workCardId = varchar("work_card_id", 64).nullable()
    val bundleNo = integer("bundle_no").nullable()
    val cartonId = varchar("carton_id", 64).nullable()
    val sizeLabel = varchar("size_label", 60)
    val colorway = varchar("colorway", 120).default("")
    val qtyPcs = integer("qty_pcs")
    val notes = text("notes").default("")

    override val primaryKey = PrimaryKey(id)
}

/**
 * Saklar lokasi per tenant. Cermin V59.
 */
object TenantLocationSettingsTable : Table("factory_flow.tenant_location_settings") {
    val tenantId = varchar("tenant_id", 64)
    val isMultiSiteEnabled = bool("is_multi_site_enabled").default(false)
    val requireCustomerDispatchSj = bool("require_customer_dispatch_sj").default(true)
    val updatedAt = timestamp("updated_at")

    override val primaryKey = PrimaryKey(tenantId)
}

/**
 * Pemetaan simpul alur ke gedung tempatnya dikerjakan. Cermin V59.
 *
 * Satu tabel untuk ketiga lapisan alur ([nodeKind] = STAGE / PROC / STATION), bukan satu tabel
 * per lapisan — lihat KDoc `FlowNodeRef` untuk alasannya.
 */
object TenantFlowNodeLocationsTable : Table("factory_flow.tenant_flow_node_locations") {
    val tenantId = varchar("tenant_id", 64)
    val nodeKind = varchar("node_kind", 16)
    val nodeKey = varchar("node_key", 64)
    val locationId = varchar("location_id", 64).references(TenantLocationsTable.id)
    val updatedAt = timestamp("updated_at")

    override val primaryKey = PrimaryKey(tenantId, nodeKind, nodeKey)
}

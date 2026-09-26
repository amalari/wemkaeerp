package com.eventverse.app.infrastructure.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.date
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

/** Pemetaan Exposed kontak vendor. Cermin V64. */
object VendorsTable : Table("vendors") {
    val id = varchar("id", 64)
    val tenantId = varchar("tenant_id", 64).references(TenantsTable.id)
    val name = varchar("name", 150)
    val phone = varchar("phone", 40).default("")
    val address = text("address").default("")
    val notes = text("notes").default("")
    val rates = text("rates").default("[]")
    val isActive = bool("is_active").default(true)
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")

    override val primaryKey = PrimaryKey(id)
}

/** Pemetaan Exposed penugasan vendor ke proses subkon. Cermin V64. */
object VendorAssignmentsTable : Table("vendor_assignments") {
    val id = varchar("id", 64)
    val tenantId = varchar("tenant_id", 64).references(TenantsTable.id)
    val subjectId = varchar("subject_id", 64)
    val subjectLabel = varchar("subject_label", 64).default("")
    val processCode = varchar("process_code", 64)
    val processName = varchar("process_name", 150)
    val vendorId = varchar("vendor_id", 64).references(VendorsTable.id)
    val vendorName = varchar("vendor_name", 150)
    val vendorPhone = varchar("vendor_phone", 40).default("")
    val pricePerUnitIdr = long("price_per_unit_idr")
    val priceUnit = varchar("price_unit", 30)
    val quantityPcs = integer("quantity_pcs")
    val unitsPerPiece = integer("units_per_piece").default(1)
    val priceSource = varchar("price_source", 30)
    val expectedReturnAt = date("expected_return_at").nullable()
    val notes = text("notes").default("")
    val status = varchar("status", 20).default("ASSIGNED")
    val assignedByUserId = varchar("assigned_by_user_id", 64).default("")
    val assignedAt = timestamp("assigned_at")
    val cancelledAt = timestamp("cancelled_at").nullable()

    override val primaryKey = PrimaryKey(id)
}

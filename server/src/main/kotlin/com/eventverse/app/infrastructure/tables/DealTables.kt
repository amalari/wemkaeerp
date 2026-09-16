package com.eventverse.app.infrastructure.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.date
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

/**
 * Contacts, deals, and deal purchase orders — the transaction side of `BusinessModule.CRM_SALES`.
 * See V34 for the full design rationale (contacts as find-or-create customer master keyed by
 * phone; deals as the qualification output whose `source_lead_id` is the idempotency anchor).
 */
object CrmContactsTable : Table("crm_contacts") {
    val id = varchar("id", 64)
    val tenantId = varchar("tenant_id", 64).references(TenantsTable.id)

    val name = varchar("name", 150).default("")
    val brandName = varchar("brand_name", 150).default("")
    val phone = varchar("phone", 20).default("")
    val email = varchar("email", 150).default("")
    val address = text("address").default("")
    val taxId = varchar("tax_id", 50).default("")
    val sourceLeadId = varchar("source_lead_id", 64).nullable()

    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")

    override val primaryKey = PrimaryKey(id)
}

object DealsTable : Table("deals") {
    val id = varchar("id", 64)
    val tenantId = varchar("tenant_id", 64).references(TenantsTable.id)
    val contactId = varchar("contact_id", 64).references(CrmContactsTable.id)
    val sourceLeadId = varchar("source_lead_id", 64).nullable()

    val title = varchar("title", 150)
    val stage = varchar("stage", 30).default("OPEN")
    val estimatedValueIdr = long("estimated_value_idr").nullable()
    val ownerEmployeeId = varchar("owner_employee_id", 64).references(EmployeesTable.id).nullable()
    val expectedCloseDate = date("expected_close_date").nullable()

    val notes = text("notes").default("")
    val createdByUserId = varchar("created_by_user_id", 64).references(UsersTable.id).nullable()
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
    val archivedAt = timestamp("archived_at").nullable()

    override val primaryKey = PrimaryKey(id)
}

object DealPurchaseOrdersTable : Table("deal_purchase_orders") {
    val id = varchar("id", 64)
    val tenantId = varchar("tenant_id", 64).references(TenantsTable.id)
    val dealId = varchar("deal_id", 64).references(DealsTable.id)

    val poNumber = varchar("po_number", 64)
    val poDate = date("po_date")
    val origin = varchar("origin", 20).default("MANUAL")

    val fileName = varchar("file_name", 255).nullable()
    val mimeType = varchar("mime_type", 100).nullable()
    val fileSizeBytes = long("file_size_bytes").nullable()
    val storageKey = text("storage_key").nullable()

    val manualLines = jsonbText("manual_lines").default("[]")
    val notes = text("notes").default("")
    val recordedBy = varchar("recorded_by", 150).default("system")
    val createdAt = timestamp("created_at")

    override val primaryKey = PrimaryKey(id)
}

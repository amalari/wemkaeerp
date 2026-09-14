package com.eventverse.app.infrastructure.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

object MaterialCodeSequencesTable : Table("material_code_sequences") {
    val tenantId = varchar("tenant_id", 64).references(TenantsTable.id)
    val categoryCode = varchar("category_code", 32)
    val currentSeq = long("current_seq").default(0L)

    override val primaryKey = PrimaryKey(tenantId, categoryCode)
}

object MaterialItemsTable : Table("material_items") {
    val id = varchar("id", 64)
    val tenantId = varchar("tenant_id", 64).references(TenantsTable.id)
    val code = varchar("code", 32)
    val name = varchar("name", 200)
    val category = varchar("category", 50)
    val baseUom = varchar("base_uom", 20)
    val alternateUoms = jsonbText("alternate_uoms")
    val defaultOwnership = varchar("default_ownership", 50).default("owned_raw_material")
    val description = text("description").default("")
    val customAttributes = jsonbText("custom_attributes")
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
    val archivedAt = timestamp("archived_at").nullable()

    override val primaryKey = PrimaryKey(id)
}

object MaterialPricesTable : Table("material_prices") {
    val id = varchar("id", 64)
    val tenantId = varchar("tenant_id", 64).references(TenantsTable.id)
    val materialId = varchar("material_id", 64).references(MaterialItemsTable.id)
    val amountMinor = long("amount_minor")
    val currency = varchar("currency", 10).default("IDR")
    val perQuantityMicros = long("per_quantity_micros")
    val perUom = varchar("per_uom", 20)
    val priceSource = varchar("source", 50).default("STANDARD")
    val effectiveFrom = timestamp("effective_from")
    val note = text("note").default("")
    val recordedByUserId = varchar("recorded_by_user_id", 64).references(UsersTable.id).nullable()
    val recordedAt = timestamp("recorded_at")

    override val primaryKey = PrimaryKey(id)
}

object MaterialPricePoliciesTable : Table("material_price_policies") {
    val tenantId = varchar("tenant_id", 64).references(TenantsTable.id)
    val preferenceOrder = jsonbText("preference_order")
    val fallbackToStandard = bool("fallback_to_standard").default(true)
    val updatedAt = timestamp("updated_at")

    override val primaryKey = PrimaryKey(tenantId)
}

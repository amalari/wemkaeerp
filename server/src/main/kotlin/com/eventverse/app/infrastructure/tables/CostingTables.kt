package com.eventverse.app.infrastructure.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

object CostingRateCardsTable : Table("costing_hpp.costing_rate_cards") {
    val id = varchar("id", 64)
    val tenantId = varchar("tenant_id", 64).references(TenantsTable.id)
    val behavior = varchar("behavior", 64)
    val version = integer("version").default(1)
    val effectiveFrom = timestamp("effective_from")
    val effectiveTo = timestamp("effective_to").nullable()
    val description = text("description").default("")
    val laborRateMinorUnits = long("labor_rate_minor_units").nullable()
    val subcontractRateMinorUnits = long("subcontract_rate_minor_units").nullable()
    val serviceFeeMinorUnits = long("service_fee_minor_units").nullable()
    val overheadMinorUnits = long("overhead_minor_units").nullable()
    val packingUnitMinorUnits = long("packing_unit_minor_units").nullable()
    val packingOrderMinorUnits = long("packing_order_minor_units").nullable()
    val marginRatioMicros = long("margin_ratio_micros").nullable()
    val retailMarkupMicros = long("retail_markup_micros").nullable()
    val marketplaceFeeMicros = long("marketplace_fee_micros").nullable()
    val fabricWasteMicros = long("fabric_waste_micros").nullable()
    val includeFabricCost = bool("include_fabric_cost").nullable()
    val seededFromNodeId = varchar("seeded_from_node_id", 64).nullable()
    val createdByUserId = varchar("created_by_user_id", 64).nullable()
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")

    override val primaryKey = PrimaryKey(id)
}

object CostingSheetsTable : Table("costing_hpp.costing_sheets") {
    val id = varchar("id", 64)
    val tenantId = varchar("tenant_id", 64).references(TenantsTable.id)
    val number = varchar("number", 64)
    val techPackId = varchar("tech_pack_id", 64)
    val orderQuantity = long("order_quantity")
    val behavior = varchar("behavior", 64)
    val status = varchar("status", 32).default("DRAFT")
    val pricingAsOf = timestamp("pricing_as_of")
    val parameterOverrides = jsonbText("parameter_overrides")
    val latestResult = jsonbText("latest_result").nullable()
    val approvedSnapshot = jsonbText("approved_snapshot").nullable()
    val rejectionReason = text("rejection_reason").nullable()
    val notes = text("notes").default("")
    val linkedSpkNumber = varchar("linked_spk_number", 64).nullable()
    val createdByUserId = varchar("created_by_user_id", 64).nullable()
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")

    override val primaryKey = PrimaryKey(id)
}

object CostingSheetBucketsTable : Table("costing_hpp.costing_sheet_buckets") {
    val id = varchar("id", 64)
    val tenantId = varchar("tenant_id", 64).references(TenantsTable.id)
    val sheetId = varchar("sheet_id", 64).references(CostingSheetsTable.id)
    val kind = varchar("kind", 32)
    val label = varchar("label", 150)
    val amountPerUnitMinorUnits = long("amount_per_unit_minor_units")
    val ownership = varchar("ownership", 64)
    val isBillable = bool("is_billable").default(true)
    val sourceRefs = jsonbText("source_refs")
    val createdAt = timestamp("created_at")

    override val primaryKey = PrimaryKey(id)
}

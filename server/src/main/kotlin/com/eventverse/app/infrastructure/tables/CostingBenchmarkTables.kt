package com.eventverse.app.infrastructure.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

/** Pemetaan Exposed untuk DDL `costing_product_benchmarks` (V36). */
object CostingProductBenchmarksTable : Table("costing_product_benchmarks") {
    val id = varchar("id", 64)
    val tenantId = varchar("tenant_id", 64).references(TenantsTable.id)
    val styleName = varchar("style_name", 255)
    val clientName = varchar("client_name", 255).default("")
    val category = varchar("category", 32).default("OTHER")
    val knitType = varchar("knit_type", 120).default("")
    val yarnType = varchar("yarn_type", 120).default("")
    val gauge = integer("gauge").nullable()
    val netWeightGrams = decimal("net_weight_grams", 10, 2)
    val knittingMinutes = integer("knitting_minutes").nullable()
    val buttonCount = integer("button_count").default(0)
    val mockupImageUrl = text("mockup_image_url").nullable()
    val featuresJson = jsonbText("features_json")
    val costBreakdownJson = jsonbText("cost_breakdown_json")
    val hppPerUnitMinor = long("hpp_per_unit_minor")
    val sellingPriceMinor = long("selling_price_minor").nullable()
    val sourceSheetId = varchar("source_sheet_id", 64).nullable()
    val sourceFileName = varchar("source_file_name", 512).default("")
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")

    override val primaryKey = PrimaryKey(id)
}

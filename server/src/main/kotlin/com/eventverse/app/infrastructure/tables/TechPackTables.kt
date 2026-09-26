package com.eventverse.app.infrastructure.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

object TechPackStyleSequencesTable : Table("tech_pack_style_sequences") {
    val tenantId = varchar("tenant_id", 64).references(TenantsTable.id)
    val prefix = varchar("prefix", 32).default("STY")
    val currentSeq = long("current_seq").default(0L)

    override val primaryKey = PrimaryKey(tenantId, prefix)
}

object TechPacksTable : Table("tech_packs") {
    val id = varchar("id", 64)
    val tenantId = varchar("tenant_id", 64).references(TenantsTable.id)
    val styleCode = varchar("style_code", 32)
    val styleName = varchar("style_name", 150)
    val clientName = varchar("client_name", 150).default("")
    val status = varchar("status", 30).default("DRAFT")
    val version = integer("version").default(1)
    val sourceSampleSpecId = varchar("source_sample_spec_id", 64).references(SamplingOrdersTable.id).nullable()
    val sourceSpkNumber = varchar("source_spk_number", 50).default("")
    val customAttributes = jsonbText("custom_attributes").default("{}")
    val notes = text("notes").default("")
    val createdByUserId = varchar("created_by_user_id", 64).references(UsersTable.id).nullable()
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
    val releasedAt = timestamp("released_at").nullable()
    val archivedAt = timestamp("archived_at").nullable()

    override val primaryKey = PrimaryKey(id)
}

object TechPackBomLinesTable : Table("tech_pack_bom_lines") {
    val id = varchar("id", 64)
    val tenantId = varchar("tenant_id", 64).references(TenantsTable.id)
    val techPackId = varchar("tech_pack_id", 64).references(TechPacksTable.id)
    val lineId = varchar("line_id", 64)
    val materialFreeText = text("material_free_text")
    val materialId = varchar("material_id", 64).references(MaterialItemsTable.id).nullable()
    val materialCode = varchar("material_code", 50).nullable()
    val materialName = varchar("material_name", 150).nullable()
    val category = varchar("category", 40)
    val netQuantityMicros = long("net_quantity_micros")
    val netUom = varchar("net_uom", 20)
    val wasteNumerator = long("waste_numerator").default(0L)
    val wasteDenominator = long("waste_denominator").default(1L)
    val ownership = varchar("ownership", 40).default("owned_raw_material")
    val notes = text("notes").default("")
    val sortOrder = integer("sort_order").default(0)

    override val primaryKey = PrimaryKey(id)
}

object TechPackLaborOperationsTable : Table("tech_pack_labor_operations") {
    val id = varchar("id", 64)
    val tenantId = varchar("tenant_id", 64).references(TenantsTable.id)
    val techPackId = varchar("tech_pack_id", 64).references(TechPacksTable.id)
    val operationId = varchar("operation_id", 64)
    val name = varchar("name", 150)
    val samNumerator = long("sam_numerator").default(0L)
    val samDenominator = long("sam_denominator").default(1L)
    val workstation = varchar("workstation", 100).default("")
    val isSubcontracted = bool("is_subcontracted").default(false)
    val sortOrder = integer("sort_order").default(0)

    override val primaryKey = PrimaryKey(id)
}

object TechPackSizeYieldsTable : Table("tech_pack_size_yields") {
    val id = varchar("id", 64)
    val tenantId = varchar("tenant_id", 64).references(TenantsTable.id)
    val techPackId = varchar("tech_pack_id", 64).references(TechPacksTable.id)
    val sizeLabel = varchar("size_label", 30)
    val scaleNumerator = long("scale_numerator").default(1L)
    val scaleDenominator = long("scale_denominator").default(1L)
    val orderedQuantity = long("ordered_quantity").default(0L)

    override val primaryKey = PrimaryKey(id)
}

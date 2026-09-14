package com.eventverse.app.infrastructure.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.date
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

/**
 * Exposed table mappings for `BusinessModule.SAMPLING_ORDER`.
 * Maps 1-to-1 to V23 migration tables.
 */
object SamplingOrdersTable : Table("sampling_orders") {
    val id = varchar("id", 64)
    val tenantId = varchar("tenant_id", 64).references(TenantsTable.id)
    val spkNumber = varchar("spk_number", 50)

    val clientName = varchar("client_name", 150)
    val styleName = varchar("style_name", 150)
    val status = varchar("status", 30).default("DRAFT")
    val sizeMode = varchar("size_mode", 20).default("ALL_SIZE")

    val deadlineProgram = date("deadline_program").nullable()
    val deadlineFinishing = date("deadline_finishing").nullable()
    val deadlineDelivery = date("deadline_delivery").nullable()

    val leadId = varchar("lead_id", 64).references(CrmLeadsTable.id).nullable()
    val accNotes = text("acc_notes").default("")
    val notes = text("notes").default("")

    val createdByUserId = varchar("created_by_user_id", 64).references(UsersTable.id).nullable()
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
    val archivedAt = timestamp("archived_at").nullable()

    override val primaryKey = PrimaryKey(id)
}

object SamplingKnitSpecsTable : Table("sampling_knit_specs") {
    val id = varchar("id", 64)
    val tenantId = varchar("tenant_id", 64).references(TenantsTable.id)
    val samplingOrderId = varchar("sampling_order_id", 64).references(SamplingOrdersTable.id)

    val yarnType = varchar("yarn_type", 100).default("")
    val knitType = varchar("knit_type", 100).default("")
    val ribSpec = varchar("rib_spec", 100).default("")
    val collarSpec = varchar("collar_spec", 100).default("")
    val placketSpec = varchar("placket_spec", 100).default("")
    val colorwayNotes = text("colorway_notes").default("")
    val mockupImageUrls = jsonbText("mockup_image_urls").default("[]")

    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")

    override val primaryKey = PrimaryKey(id)
}

object SamplingSizeChartsTable : Table("sampling_size_charts") {
    val id = varchar("id", 64)
    val tenantId = varchar("tenant_id", 64).references(TenantsTable.id)
    val samplingOrderId = varchar("sampling_order_id", 64).references(SamplingOrdersTable.id)

    val category = varchar("category", 30) // FINISHED_SIZE | KNIT_RAW_SIZE
    val sizeLabel = varchar("size_label", 30).default("ALL SIZE")

    val bodyLength = double("body_length").default(0.0)
    val bodyWidth = double("body_width").default(0.0)
    val sleeveLength = double("sleeve_length").default(0.0)
    val armHole = double("arm_hole").default(0.0)
    val neckDrop = double("neck_drop").default(0.0)
    val neckWidth = double("neck_width").default(0.0)
    val shoulderWidth = double("shoulder_width").default(0.0)
    val ribHeight = double("rib_height").default(0.0)
    val collarHeight = double("collar_height").default(0.0)
    val placketWidth = double("placket_width").default(0.0)
    val sleeveOpening = double("sleeve_opening").default(0.0)

    val createdAt = timestamp("created_at")

    override val primaryKey = PrimaryKey(id)
}

object SamplingMachineProgramsTable : Table("sampling_machine_programs") {
    val id = varchar("id", 64)
    val tenantId = varchar("tenant_id", 64).references(TenantsTable.id)
    val samplingOrderId = varchar("sampling_order_id", 64).references(SamplingOrdersTable.id)

    val programFront = varchar("program_front", 100).default("")
    val programBack = varchar("program_back", 100).default("")
    val programSleeve = varchar("program_sleeve", 100).default("")
    val programCollar = varchar("program_collar", 100).default("")
    val programPlacket = varchar("program_placket", 100).default("")

    val feederInstructions = jsonbText("feeder_instructions").default("[]")
    val patternFormulas = jsonbText("pattern_formulas").default("{}")
    val tensionSettings = jsonbText("tension_settings").default("{}")

    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")

    override val primaryKey = PrimaryKey(id)
}

object SamplingYieldTimingsTable : Table("sampling_yield_timings") {
    val id = varchar("id", 64)
    val tenantId = varchar("tenant_id", 64).references(TenantsTable.id)
    val samplingOrderId = varchar("sampling_order_id", 64).references(SamplingOrdersTable.id)

    val panelWeightsGrams = jsonbText("panel_weights_grams").default("{}")
    val panelKnittingMinutes = jsonbText("panel_knitting_minutes").default("{}")
    val linkingNotes = text("linking_notes").default("")
    val additionalProcess = text("additional_process").default("")
    val isWashed = bool("is_washed").default(false)
    val estimatedHppIdr = long("estimated_hpp_idr").default(0L)

    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")

    override val primaryKey = PrimaryKey(id)
}

object SamplingMilestonesTable : Table("sampling_milestones") {
    val id = varchar("id", 64)
    val tenantId = varchar("tenant_id", 64).references(TenantsTable.id)
    val samplingOrderId = varchar("sampling_order_id", 64).references(SamplingOrdersTable.id)

    val stepName = varchar("step_name", 30) // PROGRAM | RAJUT | PROSES_TAMBAHAN | LINKING | WASHING | KIRIM | HPP
    val isCompleted = bool("is_completed").default(false)
    val completedAt = date("completed_at").nullable()
    val stepOrder = integer("step_order").default(0)
    val notes = text("notes").default("")

    override val primaryKey = PrimaryKey(id)
}

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
    val pipelineStage = varchar("pipeline_stage", 50).default("NEW_INTAKE")
    val finishingPath = varchar("finishing_path", 50).default("INTERNAL")
    val vendorName = varchar("vendor_name", 150).nullable()
    val vendorPhone = varchar("vendor_phone", 50).nullable()
    val vendorSentAt = date("vendor_sent_at").nullable()
    val vendorTargetAt = date("vendor_target_at").nullable()
    val vendorReturnedAt = date("vendor_returned_at").nullable()
    val vendorCostPerPcs = long("vendor_cost_per_pcs").default(0L)
    val vendorStatus = varchar("vendor_status", 50).default("NONE")
    val vendorNotes = text("vendor_notes").default("")
    val sizeMode = varchar("size_mode", 20).default("ALL_SIZE")

    val deadlineProgram = date("deadline_program").nullable()
    val deadlineFinishing = date("deadline_finishing").nullable()
    val deadlineDelivery = date("deadline_delivery").nullable()

    val leadId = varchar("lead_id", 64).references(CrmLeadsTable.id).nullable()
    val dealId = varchar("deal_id", 64).references(DealsTable.id).nullable()
    val sampleQuantity = integer("sample_quantity").default(2)
    val courierTracking = varchar("courier_tracking", 150).nullable()
    val samplingFeeIdr = long("sampling_fee_idr").default(0L)
    val revisionCount = integer("revision_count").default(0)
    val revisionHistory = jsonbText("revision_history").default("[]")
    val sizeMatrix = jsonbText("size_matrix").default("[]")
    val stageInputs = jsonbText("stage_inputs").default("{}")
    val stageHistory = jsonbText("stage_history").default("[]")
    val customFlowProcesses = jsonbText("custom_flow_processes").nullable()
    val isCustomFlow = bool("is_custom_flow").default(false)
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
    val tenselityEntries = jsonbText("tenselity_entries").default("[]")

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
    /** Gramasi/waktu/program per ukuran; kosong berarti seluruh ukuran memakai angka acuan di atas. */
    val panelSizeSpecs = jsonbText("panel_size_specs").default("[]")
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

object SamplingFinishingDepositsTable : Table("sampling_finishing_deposits") {
    val id = varchar("id", 64)
    val tenantId = varchar("tenant_id", 64).references(TenantsTable.id)
    val samplingOrderId = varchar("sampling_order_id", 64).references(SamplingOrdersTable.id)

    val depositDate = date("deposit_date")
    val qtyPcs = integer("qty_pcs")
    val weightKg = double("weight_kg").default(0.0)
    val scalePhotoKey = text("scale_photo_key").nullable()
    val garmentPhotoKey = text("garment_photo_key").nullable()
    val operatorName = varchar("operator_name", 100).default("")
    val notes = text("notes").default("")
    val createdAt = timestamp("created_at")

    override val primaryKey = PrimaryKey(id)
}

object SamplingQcInspectionsTable : Table("sampling_qc_inspections") {
    val id = varchar("id", 64)
    val tenantId = varchar("tenant_id", 64).references(TenantsTable.id)
    val samplingOrderId = varchar("sampling_order_id", 64).references(SamplingOrdersTable.id)

    val kind = varchar("kind", 30).default("FINISHING")
    val inspectorName = varchar("inspector_name", 100).default("")
    val inspectedAt = timestamp("inspected_at")
    val inspectedQty = integer("inspected_qty").default(1)
    val pieceNo = integer("piece_no").default(1)
    val measuredPomValues = jsonbText("measured_pom_values").default("[]")
    val defectsFound = jsonbText("defects_found").default("[]")
    val qcResult = varchar("qc_result", 30).default("PASSED")
    val qcNotes = text("qc_notes").default("")
    val verifiedPhotoFrontKey = text("verified_photo_front_key").nullable()
    val verifiedPhotoBackKey = text("verified_photo_back_key").nullable()

    override val primaryKey = PrimaryKey(id)
}

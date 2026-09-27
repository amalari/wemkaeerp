package com.eventverse.app.infrastructure.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

/**
 * Pemetaan Exposed untuk kartu antrean kerja stasiun (Work Cards). Cermin V55.
 */
object WorkCardsTable : Table("work_cards") {
    val id = varchar("id", 64)
    val tenantId = varchar("tenant_id", 64).references(TenantsTable.id)
    val subjectKind = varchar("subject_kind", 30)
    val subjectId = varchar("subject_id", 64)
    val orderNumber = varchar("order_number", 100)
    val articleName = varchar("article_name", 150)
    val stationCode = varchar("station_code", 50)
    val sizeLabel = varchar("size_label", 60)
    val bundleNo = integer("bundle_no").nullable()
    val queuedPcs = integer("queued_pcs")
    val wipPcs = integer("wip_pcs")
    val scrapPcs = integer("scrap_pcs").default(0)
    val reworkPcs = integer("rework_pcs").default(0)
    val trackingUnit = varchar("tracking_unit", 30).default("BUNDLE")
    val status = varchar("status", 30).default("QUEUED")
    val executionMode = varchar("execution_mode", 30).default("IN_HOUSE")
    val vendorRef = varchar("vendor_ref", 150).nullable()
    val createdAt = timestamp("created_at")
    val completedAt = timestamp("completed_at").nullable()

    override val primaryKey = PrimaryKey(id)
}

/**
 * Pemetaan Exposed untuk setoran borongan operator (Work Deposits). Cermin V55.
 */
object WorkDepositsTable : Table("work_deposits") {
    val id = varchar("id", 64)
    val tenantId = varchar("tenant_id", 64).references(TenantsTable.id)
    val workCardId = varchar("work_card_id", 64).references(WorkCardsTable.id)
    val operatorId = varchar("operator_id", 64)
    val operatorName = varchar("operator_name", 150)
    val qtyPcs = integer("qty_pcs")
    val tariffSnapshotIdr = long("tariff_snapshot_idr").default(0L)
    val isReworkDeposit = bool("is_rework_deposit").default(false)
    val notes = text("notes").default("")
    val verifiedPhotoKey = text("verified_photo_key").nullable()
    val submittedAt = timestamp("submitted_at")

    override val primaryKey = PrimaryKey(id)
}

/**
 * Pemetaan Exposed untuk tiket perbaikan cacat (Rework Tickets). Cermin V55.
 */
object ReworkTicketsTable : Table("rework_tickets") {
    val id = varchar("id", 64)
    val tenantId = varchar("tenant_id", 64).references(TenantsTable.id)
    val workCardId = varchar("work_card_id", 64).references(WorkCardsTable.id)
    val subjectKind = varchar("subject_kind", 30)
    val subjectId = varchar("subject_id", 64)
    val orderNumber = varchar("order_number", 100)
    val articleName = varchar("article_name", 150)
    val defectCode = varchar("defect_code", 64)
    val defectDisplayName = varchar("defect_display_name", 150)
    val liability = varchar("liability", 40)
    val qtyPcs = integer("qty_pcs")
    val sizeLabel = varchar("size_label", 60)
    val targetStationCode = varchar("target_station_code", 50)
    val responsibleOperatorId = varchar("responsible_operator_id", 64).nullable()
    val assignedRepairOperatorId = varchar("assigned_repair_operator_id", 64).nullable()
    val status = varchar("status", 40).default("REWORK_ISSUED")
    val qcNotes = text("qc_notes").default("")
    val repairNotes = text("repair_notes").default("")
    val scrapReason = text("scrap_reason").nullable()
    val issuedAt = timestamp("issued_at")
    val inRepairAt = timestamp("in_repair_at").nullable()
    val readyForRecheckAt = timestamp("ready_for_recheck_at").nullable()
    val closedAt = timestamp("closed_at").nullable()

    override val primaryKey = PrimaryKey(id)
}

/**
 * Pemetaan Exposed untuk sesi drum cuci masal (Washing Batches). Cermin V70.
 */
object WashingBatchesTable : Table("washing_batches") {
    val id = varchar("id", 64)
    val tenantId = varchar("tenant_id", 64).references(TenantsTable.id)
    val batchCode = varchar("batch_code", 50)
    val machineDrumNo = varchar("machine_drum_no", 50).default("")
    val washRecipe = varchar("wash_recipe", 100).default("")
    val operatorName = varchar("operator_name", 150).default("")
    val totalBundles = integer("total_bundles").default(0)
    val totalInputPcs = integer("total_input_pcs").default(0)
    val totalOutputPcs = integer("total_output_pcs").default(0)
    val missingPcs = integer("missing_pcs").default(0)
    val status = varchar("status", 30).default("IN_WASHER")
    val notes = text("notes").default("")
    val createdAt = timestamp("created_at")
    val completedAt = timestamp("completed_at").nullable()

    override val primaryKey = PrimaryKey(id)
}

/**
 * Pemetaan Exposed untuk rincian bundle dalam batch cuci dengan foto bukti fisik. Cermin V70.
 */
object WashingBatchItemsTable : Table("washing_batch_items") {
    val id = varchar("id", 64)
    val tenantId = varchar("tenant_id", 64).references(TenantsTable.id)
    val batchId = varchar("batch_id", 64).references(WashingBatchesTable.id)
    val workCardId = varchar("work_card_id", 64).references(WorkCardsTable.id)
    val subjectId = varchar("subject_id", 64)
    val orderNumber = varchar("order_number", 100)
    val articleName = varchar("article_name", 150).default("")
    val bundleNo = integer("bundle_no")
    val sizeLabel = varchar("size_label", 60)
    val inputPcs = integer("input_pcs")
    val bundlePhotoKey = text("bundle_photo_key")
    val createdAt = timestamp("created_at")

    override val primaryKey = PrimaryKey(id)
}

/**
 * Pemetaan Exposed untuk hasil meja sortir pasca-dryer per PO & Ukuran. Cermin V70.
 */
object WashingBatchSortOutputsTable : Table("washing_batch_sort_outputs") {
    val id = varchar("id", 64)
    val tenantId = varchar("tenant_id", 64).references(TenantsTable.id)
    val batchId = varchar("batch_id", 64).references(WashingBatchesTable.id)
    val subjectId = varchar("subject_id", 64)
    val orderNumber = varchar("order_number", 100)
    val sizeLabel = varchar("size_label", 60)
    val outputPcs = integer("output_pcs")
    val scrapPcs = integer("scrap_pcs").default(0)
    val defectPcs = integer("defect_pcs").default(0)
    val notes = text("notes").default("")
    val createdAt = timestamp("created_at")

    override val primaryKey = PrimaryKey(id)
}


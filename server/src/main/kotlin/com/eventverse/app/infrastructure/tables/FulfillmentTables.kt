package com.eventverse.app.infrastructure.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

/**
 * Pemetaan Exposed untuk transfer karung antar divisi. Cermin migrasi V54.
 *
 * `sack_code` tidak jadi foreign key ke `trace_containers(tenant_id, code)` karena kombinasi
 * uniknya dua kolom; kekakuan "karung harus sudah ditutup" dijaga use case, bukan FK.
 */
object FulfillmentTransfersTable : Table("fulfillment_transfers") {
    val id = varchar("id", 64)
    val tenantId = varchar("tenant_id", 64).references(TenantsTable.id)
    val sackCode = varchar("sack_code", 32)
    val workOrderKind = varchar("work_order_kind", 10).nullable()
    val workOrderId = varchar("work_order_id", 64).nullable()
    val sizeLabel = varchar("size_label", 60)
    val colorway = varchar("colorway", 120).default("")
    val declaredPcs = integer("declared_pcs")
    val leg = varchar("leg", 40)
    val status = varchar("status", 20).default("MENUNGGU_ACC")

    val dispatchWeightKg = double("dispatch_weight_kg")
    val dispatchScalePhotoKey = text("dispatch_scale_photo_key")
    val requestedBy = varchar("requested_by", 150)
    val requestedAt = timestamp("requested_at")

    val approvedBy = varchar("approved_by", 150).nullable()
    val approvedAt = timestamp("approved_at").nullable()
    val approvalSignatureKey = text("approval_signature_key").nullable()
    val rejectedBy = varchar("rejected_by", 150).nullable()
    val rejectReason = text("reject_reason").nullable()

    val handoverType = varchar("handover_type", 20).nullable()
    val receiverName = varchar("receiver_name", 150).nullable()
    val receiverSignatureKey = text("receiver_signature_key").nullable()
    val carrier = varchar("carrier", 80).nullable()
    val trackingNumber = varchar("tracking_number", 80).nullable()
    val chargeableWeightKg = double("chargeable_weight_kg").nullable()
    val handoverPhotoKey = text("handover_photo_key").nullable()
    val receivedWeightKg = double("received_weight_kg").nullable()
    val receivedPcs = integer("received_pcs").nullable()
    val receivedAt = timestamp("received_at").nullable()

    val notes = text("notes").default("")
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")

    override val primaryKey = PrimaryKey(id)
}

object FulfillmentTransferEventsTable : Table("fulfillment_transfer_events") {
    val id = varchar("id", 64)
    val tenantId = varchar("tenant_id", 64)
    val transferId = varchar("transfer_id", 64).references(FulfillmentTransfersTable.id)
    val eventType = varchar("event_type", 30)
    val actor = varchar("actor", 150).default("")
    val detail = text("detail").default("")
    val occurredAt = timestamp("occurred_at")

    override val primaryKey = PrimaryKey(id)
}

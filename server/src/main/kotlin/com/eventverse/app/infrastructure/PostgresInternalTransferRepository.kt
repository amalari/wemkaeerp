package com.eventverse.app.infrastructure

import com.eventverse.app.domain.fulfillment.HandoverProof
import com.eventverse.app.domain.fulfillment.InternalTransfer
import com.eventverse.app.domain.fulfillment.InternalTransferRepository
import com.eventverse.app.domain.fulfillment.SackTransferId
import com.eventverse.app.domain.fulfillment.SackRoute
import com.eventverse.app.domain.fulfillment.SackTransferStatus
import com.eventverse.app.domain.fulfillment.WeightKg
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.domain.traceability.TraceCode
import com.eventverse.app.domain.traceability.TraceWorkOrderKind
import com.eventverse.app.domain.traceability.TraceWorkOrderRef
import com.eventverse.app.infrastructure.tables.FulfillmentTransferEventsTable
import com.eventverse.app.infrastructure.tables.FulfillmentTransfersTable
import kotlinx.datetime.Instant
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update

class PostgresInternalTransferRepository : InternalTransferRepository {

    override suspend fun findById(tenantId: TenantId, id: SackTransferId): InternalTransfer? =
        DatabaseFactory.dbQuery(tenantId) {
            FulfillmentTransfersTable.selectAll()
                .where {
                    (FulfillmentTransfersTable.tenantId eq tenantId.value) and
                        (FulfillmentTransfersTable.id eq id.value)
                }
                .singleOrNull()
                ?.let(::hydrate)
        }

    override suspend fun findAll(tenantId: TenantId): List<InternalTransfer> =
        DatabaseFactory.dbQuery(tenantId) {
            FulfillmentTransfersTable.selectAll()
                .where { FulfillmentTransfersTable.tenantId eq tenantId.value }
                .orderBy(FulfillmentTransfersTable.requestedAt, SortOrder.DESC)
                .toList()
                .map(::hydrate)
        }

    /**
     * Perjalanan yang masih hidup untuk satu karung. Definisi "aktif" di sini harus persis
     * sama dengan indeks parsial `uq_fulfillment_active_transfer` di V54 — kalau keduanya
     * berbeda, gerbang anti-ganda di use case dan gerbang di database saling mendustai.
     */
    override suspend fun findActiveBySack(tenantId: TenantId, sackCode: TraceCode): InternalTransfer? =
        DatabaseFactory.dbQuery(tenantId) {
            FulfillmentTransfersTable.selectAll()
                .where {
                    (FulfillmentTransfersTable.tenantId eq tenantId.value) and
                        (FulfillmentTransfersTable.sackCode eq sackCode.value) and
                        (FulfillmentTransfersTable.status inList listOf(
                            SackTransferStatus.MENUNGGU_ACC.name,
                            SackTransferStatus.DIANTAR.name,
                            SackTransferStatus.DITOLAK.name,
                            SackTransferStatus.DIPERIKSA.name
                        ))
                }
                .singleOrNull()
                ?.let(::hydrate)
        }

    override suspend fun save(transfer: InternalTransfer): InternalTransfer =
        DatabaseFactory.dbQuery(transfer.tenantId) {
            val updatedRows = FulfillmentTransfersTable.update(
                where = {
                    (FulfillmentTransfersTable.tenantId eq transfer.tenantId.value) and
                        (FulfillmentTransfersTable.id eq transfer.id.value)
                }
            ) { it.applyFrom(transfer) }

            if (updatedRows == 0) {
                FulfillmentTransfersTable.insert {
                    it[id] = transfer.id.value
                    it[tenantId] = transfer.tenantId.value
                    it[createdAt] = transfer.createdAt
                    it.applyFrom(transfer)
                }
            }
            transfer
        }

    override suspend fun recordEvent(
        tenantId: TenantId,
        transferId: SackTransferId,
        eventType: String,
        actor: String,
        detail: String,
        occurredAt: Instant
    ) {
        DatabaseFactory.dbQuery(tenantId) {
            FulfillmentTransferEventsTable.insert {
                it[id] = "fte_${occurredAt.toEpochMilliseconds()}_${transferId.value.hashCode()}"
                it[FulfillmentTransferEventsTable.tenantId] = tenantId.value
                it[FulfillmentTransferEventsTable.transferId] = transferId.value
                it[FulfillmentTransferEventsTable.eventType] = eventType
                it[FulfillmentTransferEventsTable.actor] = actor
                it[FulfillmentTransferEventsTable.detail] = detail
                it[FulfillmentTransferEventsTable.occurredAt] = occurredAt
            }
        }
    }

    private fun <T> T.applyFrom(t: InternalTransfer) where T : org.jetbrains.exposed.sql.statements.UpdateBuilder<*> {
        this[FulfillmentTransfersTable.sackCode] = t.sackCode.value
        this[FulfillmentTransfersTable.workOrderKind] = t.workOrder?.kind?.name
        this[FulfillmentTransfersTable.workOrderId] = t.workOrder?.id
        this[FulfillmentTransfersTable.sizeLabel] = t.sizeLabel
        this[FulfillmentTransfersTable.colorway] = t.colorway
        this[FulfillmentTransfersTable.declaredPcs] = t.declaredPcs
        this[FulfillmentTransfersTable.leg] = t.leg.name
        this[FulfillmentTransfersTable.status] = t.status.name
        this[FulfillmentTransfersTable.dispatchWeightKg] = t.dispatchWeightKg.value
        this[FulfillmentTransfersTable.dispatchScalePhotoKey] = t.dispatchScalePhotoKey
        this[FulfillmentTransfersTable.requestedBy] = t.requestedBy
        this[FulfillmentTransfersTable.requestedAt] = t.requestedAt
        this[FulfillmentTransfersTable.approvedBy] = t.approvedBy
        this[FulfillmentTransfersTable.approvedAt] = t.approvedAt
        this[FulfillmentTransfersTable.approvalSignatureKey] = t.approvalSignatureKey
        this[FulfillmentTransfersTable.rejectedBy] = t.rejectedBy
        this[FulfillmentTransfersTable.rejectReason] = t.rejectReason
        this[FulfillmentTransfersTable.handoverType] = when (t.handover) {
            is HandoverProof.ReceiverHandover -> "RECEIVER"
            is HandoverProof.CourierShipment -> "COURIER"
            null -> null
        }
        this[FulfillmentTransfersTable.receiverName] = (t.handover as? HandoverProof.ReceiverHandover)?.receiverName
        this[FulfillmentTransfersTable.receiverSignatureKey] =
            (t.handover as? HandoverProof.ReceiverHandover)?.signatureKey
        this[FulfillmentTransfersTable.carrier] = (t.handover as? HandoverProof.CourierShipment)?.carrier
        this[FulfillmentTransfersTable.trackingNumber] =
            (t.handover as? HandoverProof.CourierShipment)?.trackingNumber
        this[FulfillmentTransfersTable.chargeableWeightKg] =
            (t.handover as? HandoverProof.CourierShipment)?.chargeableWeightKg?.value
        this[FulfillmentTransfersTable.handoverPhotoKey] = t.handover?.evidencePhotoKey
        this[FulfillmentTransfersTable.receivedWeightKg] = t.receivedWeightKg?.value
        this[FulfillmentTransfersTable.receivedPcs] = t.receivedPcs
        this[FulfillmentTransfersTable.receivedAt] = t.receivedAt
        this[FulfillmentTransfersTable.notes] = t.notes
        this[FulfillmentTransfersTable.updatedAt] = t.updatedAt
    }

    private fun hydrate(row: ResultRow): InternalTransfer {
        val handover = when (row[FulfillmentTransfersTable.handoverType]) {
            "RECEIVER" -> HandoverProof.ReceiverHandover(
                receiverName = row[FulfillmentTransfersTable.receiverName].orEmpty(),
                signatureKey = row[FulfillmentTransfersTable.receiverSignatureKey].orEmpty(),
                evidencePhotoKey = row[FulfillmentTransfersTable.handoverPhotoKey].orEmpty()
            )
            "COURIER" -> {
                val weight = row[FulfillmentTransfersTable.chargeableWeightKg]?.toDouble() ?: 0.0
                if (weight <= 0.0) null else HandoverProof.CourierShipment(
                    carrier = row[FulfillmentTransfersTable.carrier].orEmpty(),
                    trackingNumber = row[FulfillmentTransfersTable.trackingNumber].orEmpty(),
                    chargeableWeightKg = WeightKg(weight),
                    evidencePhotoKey = row[FulfillmentTransfersTable.handoverPhotoKey].orEmpty()
                )
            }
            else -> null
        }

        val workOrder = row[FulfillmentTransfersTable.workOrderKind]?.let { kindRaw ->
            TraceWorkOrderKind.entries.firstOrNull { it.name == kindRaw }?.let { kind ->
                row[FulfillmentTransfersTable.workOrderId]?.let { TraceWorkOrderRef(kind, it) }
            }
        }

        return InternalTransfer(
            id = SackTransferId(row[FulfillmentTransfersTable.id]),
            tenantId = TenantId(row[FulfillmentTransfersTable.tenantId]),
            sackCode = TraceCode(row[FulfillmentTransfersTable.sackCode]),
            workOrder = workOrder,
            sizeLabel = row[FulfillmentTransfersTable.sizeLabel],
            colorway = row[FulfillmentTransfersTable.colorway],
            declaredPcs = row[FulfillmentTransfersTable.declaredPcs],
            leg = SackRoute.entries.firstOrNull { it.name == row[FulfillmentTransfersTable.leg] }
                ?: SackRoute.QC_RAJUT_TO_FINISHING,
            status = SackTransferStatus.entries.firstOrNull { it.name == row[FulfillmentTransfersTable.status] }
                ?: SackTransferStatus.MENUNGGU_ACC,
            dispatchWeightKg = WeightKg(row[FulfillmentTransfersTable.dispatchWeightKg].toDouble()),
            dispatchScalePhotoKey = row[FulfillmentTransfersTable.dispatchScalePhotoKey],
            requestedBy = row[FulfillmentTransfersTable.requestedBy],
            requestedAt = row[FulfillmentTransfersTable.requestedAt],
            approvedBy = row[FulfillmentTransfersTable.approvedBy],
            approvedAt = row[FulfillmentTransfersTable.approvedAt],
            approvalSignatureKey = row[FulfillmentTransfersTable.approvalSignatureKey],
            rejectedBy = row[FulfillmentTransfersTable.rejectedBy],
            rejectReason = row[FulfillmentTransfersTable.rejectReason],
            handover = handover,
            receivedWeightKg = row[FulfillmentTransfersTable.receivedWeightKg]?.toDouble()?.let(::WeightKg),
            receivedPcs = row[FulfillmentTransfersTable.receivedPcs],
            receivedAt = row[FulfillmentTransfersTable.receivedAt],
            createdAt = row[FulfillmentTransfersTable.createdAt],
            updatedAt = row[FulfillmentTransfersTable.updatedAt],
            notes = row[FulfillmentTransfersTable.notes]
        )
    }
}

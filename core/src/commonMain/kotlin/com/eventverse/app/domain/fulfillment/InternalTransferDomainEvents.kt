package com.eventverse.app.domain.fulfillment

import com.eventverse.app.domain.traceability.TraceCode
import kotlinx.datetime.Instant

/**
 * Kejadian domain perjalanan karung — semuanya past tense.
 *
 * Dipisah dari status entity karena status menjawab "di mana karung sekarang", sementara
 * kejadian menjawab "siapa melakukan apa dan kapan" — pertanyaan yang dijawab saat selisih
 * pcs diselidiki, bukan saat karung disortir.
 */
sealed interface InternalTransferEvent {
    val transferId: SackTransferId
    val sackCode: TraceCode
    val occurredAt: Instant
}

data class TransferSubmitted(
    override val transferId: SackTransferId,
    override val sackCode: TraceCode,
    val requestedBy: String,
    val leg: SackRoute,
    val dispatchWeightKg: WeightKg,
    override val occurredAt: Instant
) : InternalTransferEvent

data class TransferApproved(
    override val transferId: SackTransferId,
    override val sackCode: TraceCode,
    val approvedBy: String,
    override val occurredAt: Instant
) : InternalTransferEvent

data class TransferRejected(
    override val transferId: SackTransferId,
    override val sackCode: TraceCode,
    val rejectedBy: String,
    val reason: String,
    override val occurredAt: Instant
) : InternalTransferEvent

data class TransferResubmitted(
    override val transferId: SackTransferId,
    override val sackCode: TraceCode,
    val requestedBy: String,
    override val occurredAt: Instant
) : InternalTransferEvent

data class TransferReceived(
    override val transferId: SackTransferId,
    override val sackCode: TraceCode,
    val receivedBy: String,
    val withDiscrepancy: Boolean,
    override val occurredAt: Instant
) : InternalTransferEvent

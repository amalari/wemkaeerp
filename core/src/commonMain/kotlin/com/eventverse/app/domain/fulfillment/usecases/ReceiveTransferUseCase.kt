package com.eventverse.app.domain.fulfillment.usecases

import com.eventverse.app.domain.fulfillment.HandoverProof
import com.eventverse.app.domain.fulfillment.InternalTransfer
import com.eventverse.app.domain.fulfillment.InternalTransferRepository
import com.eventverse.app.domain.fulfillment.TransferId
import com.eventverse.app.domain.fulfillment.WeightKg
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Instant

/**
 * Mencatat penerimaan karung di ujung tujuan — menutup lingkaran perjalanan.
 *
 * Yang membuka aplikasi tetap orang internal kita; penerima tidak pernah butuh akun. Jalur
 * [HandoverProof.ReceiverHandover] menggantikan kertas TTD, jalur [HandoverProof.CourierShipment]
 * menyalin resi kurir beserta berat tertagihnya yang eksak sampai koma.
 *
 * Selisih pcs atau berat tidak menolak penerimaan — karung tetap sah masuk, tetapi statusnya
 * `DITERIMA_SELISIH` supaya rekonsiliasi tahu ke mana harus menoleh dulu.
 */
class ReceiveTransferUseCase(
    private val transfers: InternalTransferRepository
) {
    suspend operator fun invoke(
        tenantId: TenantId,
        transferId: TransferId,
        proof: HandoverProof,
        receivedWeightKg: WeightKg?,
        receivedPcs: Int?,
        recordedBy: String,
        now: Instant
    ): Result<InternalTransfer> = runCatching {
        val existing = transfers.findById(tenantId, transferId)
            ?: error("Transfer tidak ditemukan.")
        val received = existing.receive(proof, receivedWeightKg, receivedPcs, now)
        transfers.save(received)
        transfers.recordEvent(
            tenantId = tenantId,
            transferId = transferId,
            eventType = "RECEIVED",
            actor = receivedBy(proof, recordedBy),
            detail = "status=${received.status.name}",
            occurredAt = now
        )
        received
    }

    private fun receivedBy(proof: HandoverProof, recordedBy: String): String = when (proof) {
        is HandoverProof.ReceiverHandover -> proof.receiverName
        is HandoverProof.CourierShipment -> proof.trackingNumber
    }.ifBlank { recordedBy }
}

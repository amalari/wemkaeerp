package com.eventverse.app.domain.fulfillment.usecases

import com.eventverse.app.domain.fulfillment.InternalTransfer
import com.eventverse.app.domain.fulfillment.InternalTransferRepository
import com.eventverse.app.domain.fulfillment.TransferId
import com.eventverse.app.domain.fulfillment.TransferLeg
import com.eventverse.app.domain.fulfillment.WeightKg
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.domain.traceability.TraceCodec
import com.eventverse.app.domain.traceability.TraceContainerRepository
import kotlinx.datetime.Instant

/**
 * Mengajukan perjalanan karung: scan karung, timbang, foto timbangan, ajukan.
 *
 * Gerbang di sini, bukan di UI: karung harus sudah ditutup di bounded context telusur
 * (isinya terkunci), belum punya perjalanan aktif lain, dan bukti timbang dispatch wajib
 * lengkap sebelum admin produksi ditanya setuju atau tidak.
 */
class SubmitTransferUseCase(
    private val transfers: InternalTransferRepository,
    private val containers: TraceContainerRepository
) {
    suspend operator fun invoke(
        tenantId: TenantId,
        rawSackPayload: String,
        leg: TransferLeg,
        dispatchWeightKg: WeightKg,
        dispatchScalePhotoKey: String,
        requestedBy: String,
        now: Instant,
        notes: String = ""
    ): Result<InternalTransfer> = runCatching {
        val code = TraceCodec.fromScanPayload(rawSackPayload)
            ?: error("Kode karung tidak dikenali. Pindai QR atau ketik kodenya dengan benar.")
        val sack = containers.findByCode(tenantId, code)
            ?: error("Karung ${TraceCodec.grouped(code)} belum terdaftar — tutup dulu karungnya di modul telusur.")
        require(sack.isSack) { "Kartu ${TraceCodec.grouped(code)} adalah kartu bundel, bukan karung — cukup karung yang dikirim." }
        require(sack.state == com.eventverse.app.domain.traceability.TraceContainerState.CLOSED) {
            "Karung ${TraceCodec.grouped(code)} belum ditutup — hitung dan tutup dulu isinya sebelum dikirim."
        }
        transfers.findActiveBySack(tenantId, code)?.let {
            error("Karung ${TraceCodec.grouped(code)} sudah punya perjalanan aktif (${it.status.displayName}).")
        }

        val transfer = InternalTransfer(
            id = TransferId("trf_${code.value}_${now.toEpochMilliseconds()}"),
            tenantId = tenantId,
            sackCode = code,
            workOrder = sack.workOrder,
            sizeLabel = sack.sizeLabel,
            colorway = sack.colorway,
            declaredPcs = sack.declaredPcs,
            leg = leg,
            dispatchWeightKg = dispatchWeightKg,
            dispatchScalePhotoKey = dispatchScalePhotoKey,
            requestedBy = requestedBy,
            requestedAt = now,
            createdAt = now,
            updatedAt = now,
            notes = notes
        )
        transfers.save(transfer)
        transfers.recordEvent(tenantId, transfer.id, "SUBMITTED", requestedBy, "leg=${leg.name}", now)
        transfer
    }
}

/** Mengajukan ulang karung yang ditolak, dengan bukti timbang terbaru. */
class ResubmitTransferUseCase(
    private val transfers: InternalTransferRepository
) {
    suspend operator fun invoke(
        tenantId: TenantId,
        transferId: TransferId,
        dispatchWeightKg: WeightKg,
        dispatchScalePhotoKey: String,
        requestedBy: String,
        now: Instant
    ): Result<InternalTransfer> = runCatching {
        val existing = transfers.findById(tenantId, transferId)
            ?: error("Transfer tidak ditemukan.")
        val updated = existing.resubmit(dispatchWeightKg, dispatchScalePhotoKey, requestedBy, now)
        transfers.save(updated)
        transfers.recordEvent(tenantId, transferId, "RESUBMITTED", requestedBy, "", now)
        updated
    }
}

/**
 * ACC admin produksi — satu tanda tangan, karung resmi berangkat.
 *
 * Wewenang diperiksa di lapisan route (RBAC); yang dijaga di sini adalah aturan domainnya:
 * hanya pengajuan berstatus menunggu, dan ACC tanpa tanda tangan tidak sah.
 */
class ApproveTransferUseCase(
    private val transfers: InternalTransferRepository
) {
    suspend operator fun invoke(
        tenantId: TenantId,
        transferId: TransferId,
        approverName: String,
        signatureKey: String,
        now: Instant
    ): Result<InternalTransfer> = runCatching {
        val existing = transfers.findById(tenantId, transferId)
            ?: error("Transfer tidak ditemukan.")
        val approved = existing.approve(approverName, signatureKey, now)
        transfers.save(approved)
        transfers.recordEvent(tenantId, transferId, "APPROVED", approverName, "", now)
        approved
    }
}

/** Menolak pengajuan. Alasan wajib — inilah yang dibaca saat karung diperiksa ulang. */
class RejectTransferUseCase(
    private val transfers: InternalTransferRepository
) {
    suspend operator fun invoke(
        tenantId: TenantId,
        transferId: TransferId,
        reason: String,
        rejectedBy: String,
        now: Instant
    ): Result<InternalTransfer> = runCatching {
        val existing = transfers.findById(tenantId, transferId)
            ?: error("Transfer tidak ditemukan.")
        val rejected = existing.reject(reason, rejectedBy, now)
        transfers.save(rejected)
        transfers.recordEvent(tenantId, transferId, "REJECTED", rejectedBy, reason, now)
        rejected
    }
}

/** Daftar transfer untuk beranda kurir dan antrean admin — filter status opsional. */
class ListTransfersUseCase(
    private val transfers: InternalTransferRepository
) {
    suspend operator fun invoke(
        tenantId: TenantId,
        statuses: Set<com.eventverse.app.domain.fulfillment.TransferStatus> = emptySet()
    ): Result<List<InternalTransfer>> = runCatching {
        val all = transfers.findAll(tenantId).sortedByDescending { it.requestedAt }
        if (statuses.isEmpty()) all else all.filter { it.status in statuses }
    }
}

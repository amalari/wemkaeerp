package com.eventverse.app.domain.fulfillment.usecases

import com.eventverse.app.domain.fulfillment.FulfillmentRouteConfig
import com.eventverse.app.domain.fulfillment.FulfillmentRouteConfigRepository
import com.eventverse.app.domain.fulfillment.HandoverMode
import com.eventverse.app.domain.fulfillment.InternalTransfer
import com.eventverse.app.domain.fulfillment.InternalTransferRepository
import com.eventverse.app.domain.fulfillment.SackTransferId
import com.eventverse.app.domain.fulfillment.SackTransferStatus
import com.eventverse.app.domain.fulfillment.HandoverRouteCode
import com.eventverse.app.domain.fulfillment.TenantHandoverRoutes
import com.eventverse.app.domain.fulfillment.legacySackRoutes
import com.eventverse.app.domain.fulfillment.WeightKg
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.domain.traceability.TraceCodec
import com.eventverse.app.domain.traceability.TraceContainerRepository
import com.eventverse.app.domain.traceability.TraceContainerState
import kotlinx.datetime.Instant

/**
 * Mengajukan perjalanan wadah: pindai, lalu ajukan sesuai pola rutenya.
 *
 * Gerbang di sini, bukan di UI. Yang dituntut berbeda menurut [HandoverMode] rute:
 *
 * - [HandoverMode.ADMIN_HUB] — wajib karung yang sudah **ditutup** di bounded context telusur
 *   (isinya terkunci), berikut berat dan foto timbangan. Berangkat sebagai `MENUNGGU_ACC`.
 * - [HandoverMode.DIRECT] — menerima kartu **bundel** yang sudah dihitung (`TALLIED`) maupun
 *   karung tertutup, tanpa timbangan. Berangkat langsung sebagai `DIANTAR`; tidak ada meja
 *   admin yang perlu menyetujui, dan menahannya di antrean ACC berarti menunggu selamanya.
 *
 * Keduanya sama-sama menolak wadah yang masih punya perjalanan aktif.
 */
class SubmitTransferUseCase(
    private val transfers: InternalTransferRepository,
    private val containers: TraceContainerRepository,
    private val routeConfig: FulfillmentRouteConfigRepository,
    /** Rute yang sah untuk tenant. Bawaan = isi `SackRoute` (jembatan S0–S2); Track B memasang rute per tenant. */
    private val knownRoutes: suspend (TenantId) -> TenantHandoverRoutes = { legacySackRoutes(it) }
) {
    suspend operator fun invoke(
        tenantId: TenantId,
        rawSackPayload: String,
        route: HandoverRouteCode,
        dispatchWeightKg: WeightKg?,
        dispatchScalePhotoKey: String?,
        requestedBy: String,
        now: Instant,
        notes: String = "",
        declaredPcsOverride: Int? = null
    ): Result<InternalTransfer> = runCatching {
        val known = requireNotNull(knownRoutes(tenantId).find(route)?.takeIf { it.active }) {
            "Rute '${route.value}' tidak dikenal atau sudah dinonaktifkan untuk tenant ini."
        }
        val mode = (routeConfig.findByTenantId(tenantId) ?: FulfillmentRouteConfig(tenantId)).modeFor(route)

        val code = TraceCodec.fromScanPayload(rawSackPayload)
            ?: error("Kode wadah tidak dikenali. Pindai QR atau ketik kodenya dengan benar.")
        val container = containers.findByCode(tenantId, code)
            ?: error("Wadah ${TraceCodec.grouped(code)} belum terdaftar di modul telusur.")

        when (mode) {
            HandoverMode.ADMIN_HUB -> {
                require(container.isSack) {
                    "Rute ${known.label} lewat meja admin, jadi yang dikirim harus karung - " +
                        "${TraceCodec.grouped(code)} adalah kartu bundel. Tuang dulu ke karung."
                }
                require(container.state == TraceContainerState.CLOSED) {
                    "Karung ${TraceCodec.grouped(code)} belum ditutup - hitung dan tutup dulu isinya sebelum dikirim."
                }
            }
            HandoverMode.DIRECT -> {
                val siap = (container.isSack && container.state == TraceContainerState.CLOSED) ||
                    (container.isBundle && container.state == TraceContainerState.TALLIED)
                require(siap) {
                    "Wadah ${TraceCodec.grouped(code)} berstatus ${container.state.displayName} - " +
                        "bundel harus sudah dihitung, atau karung harus sudah ditutup, sebelum diantar."
                }
            }
        }

        transfers.findActiveBySack(tenantId, code)?.let {
            error("Wadah ${TraceCodec.grouped(code)} sudah punya perjalanan aktif (${it.status.displayName}).")
        }

        // Bundel menghitung lembar panel, bukan pcs baju — `declaredPcs`-nya selalu 0 dan tidak
        // bisa dipakai. Sumber jujurnya hitungan setoran operator, yang dipasok pemanggil.
        val declaredPcs = declaredPcsOverride
            ?: container.declaredPcs.takeIf { it > 0 }
            ?: error("Jumlah pcs wajib diisi untuk ${TraceCodec.grouped(code)} - kartu bundel tidak menyimpan hitungan baju jadi.")

        val transfer = InternalTransfer(
            id = SackTransferId("trf_${code.value}_${now.toEpochMilliseconds()}"),
            tenantId = tenantId,
            sackCode = code,
            workOrder = container.workOrder,
            sizeLabel = container.sizeLabel,
            colorway = container.colorway,
            declaredPcs = declaredPcs,
            route = route,
            handoverMode = mode,
            status = when (mode) {
                HandoverMode.ADMIN_HUB -> SackTransferStatus.MENUNGGU_ACC
                HandoverMode.DIRECT -> SackTransferStatus.DIANTAR
            },
            dispatchWeightKg = dispatchWeightKg.takeIf { mode == HandoverMode.ADMIN_HUB },
            dispatchScalePhotoKey = dispatchScalePhotoKey.takeIf { mode == HandoverMode.ADMIN_HUB },
            requestedBy = requestedBy,
            requestedAt = now,
            createdAt = now,
            updatedAt = now,
            notes = notes
        )
        transfers.save(transfer)
        transfers.recordEvent(
            tenantId, transfer.id, "SUBMITTED", requestedBy, "route=${route.value} mode=${mode.name}", now
        )
        transfer
    }
}

/** Mengajukan ulang karung yang ditolak, dengan bukti timbang terbaru. */
class ResubmitTransferUseCase(
    private val transfers: InternalTransferRepository
) {
    suspend operator fun invoke(
        tenantId: TenantId,
        transferId: SackTransferId,
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
        transferId: SackTransferId,
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
        transferId: SackTransferId,
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
        statuses: Set<com.eventverse.app.domain.fulfillment.SackTransferStatus> = emptySet()
    ): Result<List<InternalTransfer>> = runCatching {
        val all = transfers.findAll(tenantId).sortedByDescending { it.requestedAt }
        if (statuses.isEmpty()) all else all.filter { it.status in statuses }
    }
}

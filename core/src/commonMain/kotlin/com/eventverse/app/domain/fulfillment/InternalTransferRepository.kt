package com.eventverse.app.domain.fulfillment

import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.domain.traceability.TraceCode

/**
 * Operasi domain transfer karung — bukan DAO generik.
 *
 * Tidak ada `delete`: pengajuan yang salah diselesaikan lewat penolakan dan pengajuan ulang,
 * bukan dengan menghapus jejaknya. Setiap perubahan status wajib meninggalkan event
 * (lihat [recordEvent]) karena pengawasan adalah alasan modul ini ada.
 */
interface InternalTransferRepository {

    suspend fun findById(tenantId: TenantId, id: TransferId): InternalTransfer?

    suspend fun findAll(tenantId: TenantId): List<InternalTransfer>

    /**
     * Transfer yang masih hidup untuk satu karung (menunggu ACC, diantar, atau ditolak).
     * Dipakai sebagai gerbang anti-ganda: satu karung cuma boleh ada satu perjalanan aktif.
     */
    suspend fun findActiveBySack(tenantId: TenantId, sackCode: TraceCode): InternalTransfer?

    suspend fun save(transfer: InternalTransfer): InternalTransfer

    /** Jejak audit per perubahan status — aktor, kejadian, dan kapan. */
    suspend fun recordEvent(
        tenantId: TenantId,
        transferId: TransferId,
        eventType: String,
        actor: String,
        detail: String,
        occurredAt: kotlinx.datetime.Instant
    )
}

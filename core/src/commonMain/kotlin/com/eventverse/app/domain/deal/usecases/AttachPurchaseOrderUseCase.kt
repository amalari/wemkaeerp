package com.eventverse.app.domain.deal.usecases

import com.eventverse.app.domain.deal.DealRepository
import com.eventverse.app.domain.deal.DealStage
import com.eventverse.app.domain.deal.PoOrigin
import com.eventverse.app.domain.deal.PoNumber
import com.eventverse.app.domain.deal.PurchaseOrder
import com.eventverse.app.domain.deal.PurchaseOrderId
import com.eventverse.app.domain.deal.PurchaseOrderLine
import com.eventverse.app.domain.deal.storage.PoFileStorage
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate

/**
 * Menempelkan satu purchase order klien ke sebuah deal.
 *
 * Dua asal dokumen:
 * - [PoOrigin.MANUAL]: admin mengetik nomor + rincian PO — wajib punya minimal satu baris.
 * - [PoOrigin.UPLOADED]: berkas scan/foto — bytes-nya ditulis ke [PoFileStorage] dan hanya
 *   metadata + storage key yang masuk database.
 *
 * Sementara menempelkan PO, deal yang masih OPEN otomatis naik ke [DealStage.PO_RECEIVED]
 * (invariant bisnis: ada PO berarti deal sudah terkonfirmasi).
 */
class AttachPurchaseOrderUseCase(
    private val dealRepository: DealRepository,
    private val fileStorage: PoFileStorage? = null,
) {
    data class Command(
        val tenantId: TenantId,
        val dealId: com.eventverse.app.domain.deal.DealId,
        val poNumber: String,
        val poDate: LocalDate,
        val origin: PoOrigin,
        val lines: List<PurchaseOrderLine> = emptyList(),
        val notes: String = "",
        val recordedBy: String,
        val fileBytes: ByteArray? = null,
        val fileName: String? = null,
        val mimeType: String? = null,
        val now: Instant = Clock.System.now(),
        val newId: () -> String
    )

    suspend operator fun invoke(command: Command): Result<PurchaseOrder> = runCatching {
        val deal = requireNotNull(dealRepository.findById(command.tenantId, command.dealId)) {
            "Deal tidak ditemukan: ${command.dealId.value}"
        }
        require(!deal.isArchived) { "Deal ${deal.title.value} sudah diarsipkan" }

        val poId = PurchaseOrderId(command.newId())
        val poNumber = PoNumber(command.poNumber)

        val storageKey: String? = when (command.origin) {
            PoOrigin.MANUAL -> {
                require(command.fileBytes == null) { "PO manual tidak boleh menyertakan berkas" }
                null
            }
            PoOrigin.UPLOADED -> {
                val bytes = requireNotNull(command.fileBytes) { "PO upload wajib menyertakan berkas" }
                val fileName = requireNotNull(command.fileName) { "PO upload wajib memiliki nama berkas" }
                val storage = requireNotNull(fileStorage) { "Object storage belum dikonfigurasi di server" }
                require(storage.isConfigured) { "Object storage belum dikonfigurasi di server" }
                val key = "${command.tenantId.value}/${deal.id.value}/$poId-$fileName"
                storage.put(key, bytes, command.mimeType ?: "application/octet-stream").getOrThrow()
                key
            }
        }

        val po = PurchaseOrder(
            id = poId,
            tenantId = command.tenantId,
            dealId = deal.id,
            poNumber = poNumber,
            poDate = command.poDate,
            origin = command.origin,
            fileName = command.fileName,
            mimeType = command.mimeType,
            fileSizeBytes = command.fileBytes?.size?.toLong(),
            storageKey = storageKey,
            lines = command.lines,
            notes = command.notes,
            recordedBy = command.recordedBy,
            createdAt = command.now
        )
        dealRepository.savePurchaseOrder(po).getOrThrow()

        if (deal.isOpen) {
            deal.transitionTo(DealStage.PO_RECEIVED, command.now)
                .onSuccess { dealRepository.save(it).getOrThrow() }
        }

        po
    }
}

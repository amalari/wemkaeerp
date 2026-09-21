package com.eventverse.app.domain.transfer.usecases

import com.eventverse.app.domain.transfer.LocationId
import com.eventverse.app.domain.transfer.SuratJalanId
import com.eventverse.app.domain.transfer.SuratJalanItem
import com.eventverse.app.domain.transfer.SuratJalanManifest
import com.eventverse.app.domain.transfer.SuratJalanNumber
import com.eventverse.app.domain.transfer.SuratJalanRepository
import com.eventverse.app.domain.transfer.TransferStatus
import com.eventverse.app.domain.transfer.TransferType
import com.eventverse.app.domain.workqueue.WorkCardId
import com.eventverse.app.domain.workqueue.WorkCardRepository
import com.eventverse.app.domain.workqueue.WorkSubjectRef
import kotlinx.datetime.Instant

data class CreateInternalTransferCommand(
    val tenantId: String,
    val sjNumber: SuratJalanNumber,
    val subject: WorkSubjectRef,
    val originLocationId: LocationId,
    val destinationLocationId: LocationId,
    val cardIdsToTransfer: List<WorkCardId>,
    val carrierName: String? = null,
    val driverName: String? = null,
    val vehiclePlate: String? = null,
    val notes: String = "",
    val now: Instant
)

/**
 * Menerbitkan Surat Jalan Mutasi Internal antar-gedung/lokasi pabrik.
 *
 * Menjamin bahwa identitas bundle (#01, #02, dst.) TETAP DITERUSKAN secara utuh,
 * sehingga saat mobil pickup tiba di gedung tujuan, operator langsung melanjutkan progres per bundle.
 */
class CreateInternalTransferSuratJalanUseCase(
    private val cardRepository: WorkCardRepository,
    private val suratJalanRepository: SuratJalanRepository
) {
    suspend operator fun invoke(command: CreateInternalTransferCommand): Result<SuratJalanManifest> = runCatching {
        require(command.cardIdsToTransfer.isNotEmpty()) {
            "Internal transfer requires at least one work card to transfer"
        }
        require(command.originLocationId != command.destinationLocationId) {
            "Origin and destination locations must be different"
        }

        val cards = command.cardIdsToTransfer.mapNotNull { cardRepository.findById(it) }
        require(cards.size == command.cardIdsToTransfer.size) {
            "One or more WorkCards could not be found"
        }

        // Invarian: Seluruh kartu internal transfer wajib membawa nomor bundle
        val items = cards.mapIndexed { index, card ->
            val bundleNumber = card.bundleNo
                ?: error("Card ${card.id.value} does not have bundleNo. Internal transfer requires bundle tracking.")

            SuratJalanItem(
                id = "item-it-${command.sjNumber.value}-$index",
                workCardId = card.id,
                bundleNo = bundleNumber,
                sizeLabel = card.sizeLabel,
                qtyPcs = if (card.wipPcs > 0) card.wipPcs else card.queuedPcs,
                notes = "Bundle #$bundleNumber"
            )
        }

        val manifestId = SuratJalanId("sj-it-${command.now.toEpochMilliseconds()}")
        val manifest = SuratJalanManifest(
            id = manifestId,
            tenantId = command.tenantId,
            sjNumber = command.sjNumber,
            transferType = TransferType.INTERNAL_SITE_TRANSFER,
            subject = command.subject,
            originLocationId = command.originLocationId,
            destinationLocationId = command.destinationLocationId,
            carrierName = command.carrierName,
            driverName = command.driverName,
            vehiclePlate = command.vehiclePlate,
            status = TransferStatus.DISPATCHED,
            items = items,
            dispatchedAt = command.now,
            notes = command.notes
        )

        suratJalanRepository.save(manifest)
        manifest
    }
}

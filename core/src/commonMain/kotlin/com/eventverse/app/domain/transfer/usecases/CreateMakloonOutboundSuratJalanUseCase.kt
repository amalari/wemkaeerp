package com.eventverse.app.domain.transfer.usecases

import com.eventverse.app.domain.transfer.SuratJalanId
import com.eventverse.app.domain.transfer.SuratJalanItem
import com.eventverse.app.domain.transfer.SuratJalanManifest
import com.eventverse.app.domain.transfer.SuratJalanNumber
import com.eventverse.app.domain.transfer.SuratJalanRepository
import com.eventverse.app.domain.transfer.TransferStatus
import com.eventverse.app.domain.transfer.TransferType
import com.eventverse.app.domain.workqueue.WorkCardId
import com.eventverse.app.domain.workqueue.WorkCardRepository
import com.eventverse.app.domain.workqueue.WorkExecutionMode
import com.eventverse.app.domain.workqueue.WorkSubjectRef
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate

data class CreateMakloonOutboundCommand(
    val tenantId: String,
    val sjNumber: SuratJalanNumber,
    val subject: WorkSubjectRef,
    val vendorRef: String,
    val cardIdsToSubcontract: List<WorkCardId>,
    val unitServiceFeeIdr: Long,
    val expectedReturnDate: LocalDate,
    val carrierName: String? = null,
    val driverName: String? = null,
    val vehiclePlate: String? = null,
    val notes: String = "",
    val now: Instant
)

/**
 * Menerbitkan Surat Jalan Makloon untuk pengiriman pekerjaan ke mitra vendor luar.
 *
 * Berbeda dari mutasi internal, kartu-kartu bundle kecil (misal per 20 pcs) DIGABUNG
 * menjadi kuantitas masal (lot) per ukuran agar vendor menerima angka bulat resmi.
 */
class CreateMakloonOutboundSuratJalanUseCase(
    private val cardRepository: WorkCardRepository,
    private val suratJalanRepository: SuratJalanRepository
) {
    suspend operator fun invoke(command: CreateMakloonOutboundCommand): Result<SuratJalanManifest> = runCatching {
        require(command.cardIdsToSubcontract.isNotEmpty()) {
            "Subcontract transfer requires at least one work card"
        }
        require(command.vendorRef.isNotBlank()) { "vendorRef cannot be blank" }

        val cards = command.cardIdsToSubcontract.mapNotNull { cardRepository.findById(it) }
        require(cards.size == command.cardIdsToSubcontract.size) {
            "One or more WorkCards could not be found"
        }

        // Agregasi kuantitas bundle per ukuran (size) menjadi baris lot masal
        val aggregatedBySize = cards.groupBy { it.sizeLabel }
            .mapValues { (_, cardList) -> cardList.sumOf { if (it.wipPcs > 0) it.wipPcs else it.queuedPcs } }

        val items = aggregatedBySize.entries.mapIndexed { index, (sizeLabel, totalPcs) ->
            SuratJalanItem(
                id = "item-sub-${command.sjNumber.value}-$index",
                workCardId = null, // Digabung masal
                bundleNo = null,   // Bundle dilebur untuk vendor
                cartonId = null,
                sizeLabel = sizeLabel,
                qtyPcs = totalPcs,
                notes = "Lot Makloon - $totalPcs pcs (dari ${cards.count { it.sizeLabel == sizeLabel }} bundle)"
            )
        }

        // Tandai seluruh kartu kerja yang dikirim menjadi SUBCONTRACTED
        val updatedCards = cards.map { card ->
            card.copy(
                executionMode = WorkExecutionMode.SUBCONTRACTED,
                vendorRef = command.vendorRef
            )
        }
        cardRepository.saveAll(updatedCards)

        val manifestId = SuratJalanId("sj-sub-${command.now.toEpochMilliseconds()}")
        val manifest = SuratJalanManifest(
            id = manifestId,
            tenantId = command.tenantId,
            sjNumber = command.sjNumber,
            transferType = TransferType.SUBCONTRACT_OUTBOUND,
            subject = command.subject,
            vendorRef = command.vendorRef,
            carrierName = command.carrierName,
            driverName = command.driverName,
            vehiclePlate = command.vehiclePlate,
            status = TransferStatus.DISPATCHED,
            items = items,
            unitServiceFeeIdr = command.unitServiceFeeIdr,
            expectedReturnDate = command.expectedReturnDate,
            dispatchedAt = command.now,
            notes = command.notes
        )

        suratJalanRepository.save(manifest)
        manifest
    }
}

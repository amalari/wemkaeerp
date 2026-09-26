package com.eventverse.app.domain.transfer.usecases

import com.eventverse.app.domain.transfer.CartonId
import com.eventverse.app.domain.transfer.SuratJalanId
import com.eventverse.app.domain.transfer.SuratJalanItem
import com.eventverse.app.domain.transfer.SuratJalanManifest
import com.eventverse.app.domain.transfer.SuratJalanNumber
import com.eventverse.app.domain.transfer.SuratJalanRepository
import com.eventverse.app.domain.transfer.TransferStatus
import com.eventverse.app.domain.transfer.TransferType
import com.eventverse.app.domain.workqueue.WorkSubjectRef
import kotlinx.datetime.Instant

data class CustomerDispatchCartonInput(
    val cartonId: CartonId,
    val sizeLabel: String,
    val colorway: String = "",
    val qtyPcs: Int,
    val notes: String = ""
)

data class CreatePartialCustomerShipmentCommand(
    val tenantId: String,
    val sjNumber: SuratJalanNumber,
    val subject: WorkSubjectRef,
    val customerName: String,
    val customerAddress: String,
    val totalOrderedPcs: Int,
    val previouslyShippedPcs: Int = 0,
    val cartons: List<CustomerDispatchCartonInput>,
    val carrierName: String? = null,
    val driverName: String? = null,
    val vehiclePlate: String? = null,
    val notes: String = "",
    val now: Instant
)

data class CustomerShipmentResult(
    val manifest: SuratJalanManifest,
    val thisShipmentPcs: Int,
    val totalShippedPcs: Int,
    val totalOrderedPcs: Int,
    val remainingBacklogPcs: Int,
    val isFullyShipped: Boolean
)

/**
 * Menerbitkan Surat Jalan Pengiriman ke Buyer/Klien berbasis dus/karung.
 * Mendukung pengiriman bertahap (partial delivery) dengan pemantauan sisa backlog pesanan.
 */
class CreatePartialCustomerShipmentUseCase(
    private val suratJalanRepository: SuratJalanRepository
) {
    suspend operator fun invoke(command: CreatePartialCustomerShipmentCommand): Result<CustomerShipmentResult> = runCatching {
        require(command.cartons.isNotEmpty()) { "Cannot dispatch shipment with zero cartons" }
        require(command.customerName.isNotBlank()) { "customerName cannot be blank" }

        val thisShipmentPcs = command.cartons.sumOf { it.qtyPcs }
        val newTotalShipped = command.previouslyShippedPcs + thisShipmentPcs
        val remainingBacklog = (command.totalOrderedPcs - newTotalShipped).coerceAtLeast(0)
        val isFullyShipped = newTotalShipped >= command.totalOrderedPcs

        val items = command.cartons.mapIndexed { index, carton ->
            SuratJalanItem(
                id = "item-cus-${command.sjNumber.value}-$index",
                workCardId = null,
                bundleNo = null,
                cartonId = carton.cartonId,
                sizeLabel = carton.sizeLabel,
                colorway = carton.colorway,
                qtyPcs = carton.qtyPcs,
                notes = if (carton.notes.isNotBlank()) carton.notes else "Karton ${carton.cartonId.value}"
            )
        }

        val manifestId = SuratJalanId("sj-cus-${command.now.toEpochMilliseconds()}")
        val manifest = SuratJalanManifest(
            id = manifestId,
            tenantId = command.tenantId,
            sjNumber = command.sjNumber,
            transferType = TransferType.CUSTOMER_DISPATCH,
            subject = command.subject,
            customerName = command.customerName,
            customerAddress = command.customerAddress,
            carrierName = command.carrierName,
            driverName = command.driverName,
            vehiclePlate = command.vehiclePlate,
            status = TransferStatus.DISPATCHED,
            items = items,
            dispatchedAt = command.now,
            notes = command.notes
        )

        suratJalanRepository.save(manifest)

        CustomerShipmentResult(
            manifest = manifest,
            thisShipmentPcs = thisShipmentPcs,
            totalShippedPcs = newTotalShipped,
            totalOrderedPcs = command.totalOrderedPcs,
            remainingBacklogPcs = remainingBacklog,
            isFullyShipped = isFullyShipped
        )
    }
}

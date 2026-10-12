package com.eventverse.app.domain.fulfillment.usecases

import com.eventverse.app.domain.fulfillment.ShipmentIssued
import com.eventverse.app.domain.fulfillment.carton.CartonId
import com.eventverse.app.domain.fulfillment.carton.CartonStatus
import com.eventverse.app.domain.fulfillment.carton.CartonRepository
import com.eventverse.app.domain.fulfillment.shipment.CarrierInfo
import com.eventverse.app.domain.fulfillment.shipment.DestinationInfo
import com.eventverse.app.domain.fulfillment.shipment.DispatchShipment
import com.eventverse.app.domain.fulfillment.shipment.ShipmentId
import com.eventverse.app.domain.fulfillment.shipment.ShipmentNumber
import com.eventverse.app.domain.fulfillment.shipment.ShipmentRepository
import com.eventverse.app.domain.fulfillment.shipment.StockOwnership
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * U5: Menerbitkan surat jalan (shipment).
 *
 * Membuat shipment baru dengan menggabungkan beberapa karton yang sudah disegel.
 * Setiap karton hanya bisa masuk ke satu shipment.
 */
class IssueShipmentUseCase(
    private val shipmentRepository: ShipmentRepository,
    private val cartonRepository: CartonRepository
) {
    suspend operator fun invoke(
        tenantId: TenantId,
        cartonIds: List<CartonId>,
        stockOwnership: StockOwnership,
        carrierInfo: CarrierInfo,
        destinationInfo: DestinationInfo,
        issuedBy: String
    ): Result<IssueShipmentResult> = runCatching {
        require(cartonIds.isNotEmpty()) { "Minimal harus ada 1 karton" }
        require(issuedBy.isNotBlank()) { "Nama issuer wajib diisi" }

        // Validate all cartons are SEALED and not already linked
        val cartons = cartonIds.map { cartonId ->
            val carton = cartonRepository.findById(tenantId, cartonId)
                ?: error("Karton tidak ditemukan: ${cartonId.value}")
            if (carton.status != CartonStatus.SEALED) {
                error("Karton ${carton.cartonNumber.value} belum disegel")
            }
            carton
        }

        // Check if any carton is already linked to another shipment
        for (carton in cartons) {
            val existingShipments = shipmentRepository.findByCartonId(tenantId, carton.id)
            if (existingShipments.isNotEmpty()) {
                error("Karton ${carton.cartonNumber.value} sudah terhubung dengan shipment lain")
            }
        }

        // Generate shipment number
        val now = Clock.System.now()
        val year = now.toLocalDateTime(TimeZone.currentSystemDefault()).year
        val sequence = shipmentRepository.getNextShipmentSequence(tenantId, year)
        val shipmentNumber = ShipmentNumber.generate(year, sequence)

        // Create shipment
        val shipment = DispatchShipment(
            id = ShipmentId("sj-${now.toEpochMilliseconds()}"),
            tenantId = tenantId,
            shipmentNumber = shipmentNumber,
            stockOwnership = stockOwnership,
            carrier = carrierInfo,
            destination = destinationInfo,
            status = com.eventverse.app.domain.fulfillment.shipment.ShipmentStatus.ISSUED,
            createdAt = now,
            updatedAt = now
        )

        val saved = shipmentRepository.save(shipment)

        // Link cartons to shipment
        shipmentRepository.linkCartons(tenantId, saved.id, cartonIds)

        // Record domain event
        shipmentRepository.recordEvent(
            tenantId = tenantId,
            shipmentId = saved.id,
            eventType = "ISSUED",
            actor = issuedBy,
            detail = "carrierMode=${carrierInfo.mode},expedition=${carrierInfo.expedition},destinationCity=${destinationInfo.city},cartonCount=${cartons.size},totalPieces=${cartons.sumOf { it.quantityPcs }}",
            occurredAt = now
        )

        IssueShipmentResult(
            shipment = saved,
            linkedCartonIds = cartonIds,
            event = ShipmentIssued(
                shipmentId = saved.id,
                shipmentNumber = saved.shipmentNumber,
                tenantId = tenantId,
                carrierMode = carrierInfo.mode,
                expedition = carrierInfo.expedition,
                destinationCity = destinationInfo.city,
                cartonCount = cartons.size,
                totalPieces = cartons.sumOf { it.quantityPcs },
                issuedBy = issuedBy,
                occurredAt = now
            )
        )
    }
}

data class IssueShipmentResult(
    val shipment: DispatchShipment,
    val linkedCartonIds: List<CartonId>,
    val event: ShipmentIssued
)

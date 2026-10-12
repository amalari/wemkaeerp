package com.eventverse.app.domain.fulfillment.usecases

import com.eventverse.app.domain.fulfillment.ShipmentInTransit
import com.eventverse.app.domain.fulfillment.shipment.ShipmentId
import com.eventverse.app.domain.fulfillment.shipment.ShipmentRepository
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Clock

/**
 * U6: Menandai shipment sedang dalam perjalanan.
 *
 * Dicatat saat kendaraan berangkat dari warehouse.
 */
class MarkInTransitUseCase(
    private val shipmentRepository: ShipmentRepository
) {
    suspend operator fun invoke(
        tenantId: TenantId,
        shipmentId: ShipmentId
    ): Result<MarkInTransitResult> = runCatching {
        val existing = shipmentRepository.findById(tenantId, shipmentId)
            ?: error("Shipment tidak ditemukan: ${shipmentId.value}")

        val now = Clock.System.now()
        val inTransit = existing.markInTransit(now)
        val saved = shipmentRepository.save(inTransit)

        // Record domain event
        shipmentRepository.recordEvent(
            tenantId = tenantId,
            shipmentId = saved.id,
            eventType = "IN_TRANSIT",
            actor = "system",
            detail = "carrierMode=${saved.carrier.mode},expedition=${saved.carrier.expedition},trackingNumber=${saved.carrier.trackingNumber}",
            occurredAt = now
        )

        MarkInTransitResult(
            shipment = saved,
            event = ShipmentInTransit(
                shipmentId = saved.id,
                shipmentNumber = saved.shipmentNumber,
                carrierMode = saved.carrier.mode,
                expedition = saved.carrier.expedition,
                trackingNumber = saved.carrier.trackingNumber.takeIf { it.isNotBlank() },
                departedAt = now,
                occurredAt = now
            )
        )
    }
}

data class MarkInTransitResult(
    val shipment: com.eventverse.app.domain.fulfillment.shipment.DispatchShipment,
    val event: ShipmentInTransit
)

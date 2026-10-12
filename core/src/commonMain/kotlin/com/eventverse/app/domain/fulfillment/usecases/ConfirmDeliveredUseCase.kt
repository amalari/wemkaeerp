package com.eventverse.app.domain.fulfillment.usecases

import com.eventverse.app.domain.fulfillment.ShipmentDelivered
import com.eventverse.app.domain.fulfillment.shipment.ShipmentId
import com.eventverse.app.domain.fulfillment.shipment.ShipmentRepository
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Clock

/**
 * U7: Mengkonfirmasi shipment telah diterima.
 *
 * Dicatat saat penerima mengkonfirmasi penerimaan barang di tujuan.
 * Ini adalah status akhir (final) untuk shipment.
 */
class ConfirmDeliveredUseCase(
    private val shipmentRepository: ShipmentRepository
) {
    suspend operator fun invoke(
        tenantId: TenantId,
        shipmentId: ShipmentId,
        receivedBy: String,
        deliveryProof: String? = null
    ): Result<ConfirmDeliveredResult> = runCatching {
        val existing = shipmentRepository.findById(tenantId, shipmentId)
            ?: error("Shipment tidak ditemukan: ${shipmentId.value}")

        require(receivedBy.isNotBlank()) { "Nama penerima wajib diisi" }

        val now = Clock.System.now()
        val delivered = existing.confirmDelivered(now, receivedBy)
        val saved = shipmentRepository.save(delivered)

        // Record domain event
        shipmentRepository.recordEvent(
            tenantId = tenantId,
            shipmentId = saved.id,
            eventType = "DELIVERED",
            actor = receivedBy,
            detail = "carrierMode=${saved.carrier.mode},expedition=${saved.carrier.expedition},destinationCity=${saved.destination.city},deliveryProof=${deliveryProof ?: "none"}",
            occurredAt = now
        )

        ConfirmDeliveredResult(
            shipment = saved,
            event = ShipmentDelivered(
                shipmentId = saved.id,
                shipmentNumber = saved.shipmentNumber,
                carrierMode = saved.carrier.mode,
                expedition = saved.carrier.expedition,
                destinationCity = saved.destination.city,
                receivedBy = receivedBy,
                deliveryProof = deliveryProof,
                occurredAt = now
            )
        )
    }
}

data class ConfirmDeliveredResult(
    val shipment: com.eventverse.app.domain.fulfillment.shipment.DispatchShipment,
    val event: ShipmentDelivered
)

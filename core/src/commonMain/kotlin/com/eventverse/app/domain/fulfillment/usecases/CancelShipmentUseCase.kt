package com.eventverse.app.domain.fulfillment.usecases

import com.eventverse.app.domain.fulfillment.ShipmentCancelled
import com.eventverse.app.domain.fulfillment.shipment.ShipmentId
import com.eventverse.app.domain.fulfillment.shipment.ShipmentRepository
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Clock

/**
 * U8: Membatalkan shipment.
 *
 * Shipment hanya bisa dibatalkan jika berstatus ISSUED (belum berangkat).
 * Jika dibatalkan, karton yang terhubung akan di-unlink dan bisa digunakan
 * untuk shipment lain melalui repository method.
 */
class CancelShipmentUseCase(
    private val shipmentRepository: ShipmentRepository
) {
    suspend operator fun invoke(
        tenantId: TenantId,
        shipmentId: ShipmentId,
        reason: String,
        cancelledBy: String
    ): Result<CancelShipmentResult> = runCatching {
        val existing = shipmentRepository.findById(tenantId, shipmentId)
            ?: error("Shipment tidak ditemukan: ${shipmentId.value}")

        require(reason.isNotBlank()) { "Alasan pembatalan wajib diisi untuk audit" }
        require(cancelledBy.isNotBlank()) { "Nama pembatal wajib diisi" }

        val now = Clock.System.now()
        val cancelled = existing.cancel(cancelledBy, reason, now)
        val saved = shipmentRepository.save(cancelled)

        // Record domain event
        shipmentRepository.recordEvent(
            tenantId = tenantId,
            shipmentId = saved.id,
            eventType = "CANCELLED",
            actor = cancelledBy,
            detail = "reason=$reason",
            occurredAt = now
        )

        CancelShipmentResult(
            shipment = saved,
            event = ShipmentCancelled(
                shipmentId = saved.id,
                shipmentNumber = saved.shipmentNumber,
                carrierMode = saved.carrier.mode.takeIf { it.isNotBlank() },
                expedition = saved.carrier.expedition.takeIf { it.isNotBlank() },
                cancelledBy = cancelledBy,
                reason = reason,
                occurredAt = now
            )
        )
    }
}

data class CancelShipmentResult(
    val shipment: com.eventverse.app.domain.fulfillment.shipment.DispatchShipment,
    val event: ShipmentCancelled
)

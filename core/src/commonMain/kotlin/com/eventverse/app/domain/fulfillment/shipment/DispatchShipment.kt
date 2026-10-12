package com.eventverse.app.domain.fulfillment.shipment

import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.domain.fulfillment.carton.CartonId
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

data class DispatchShipment(
    val id: ShipmentId,
    val tenantId: TenantId,
    val shipmentNumber: ShipmentNumber,
    val stockOwnership: StockOwnership,
    val carrier: CarrierInfo = CarrierInfo(),
    val destination: DestinationInfo = DestinationInfo(),
    val status: ShipmentStatus = ShipmentStatus.ISSUED,
    val shippedAt: Instant? = null,
    val deliveredAt: Instant? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
    val notes: String = ""
) {
    init {
        require(shipmentNumber.value.isNotBlank()) { "Nomor shipment wajib diisi" }

        if (status == ShipmentStatus.IN_TRANSIT) {
            requireNotNull(shippedAt) { "Status IN_TRANSIT wajib punya shippedAt" }
        }

        if (status == ShipmentStatus.DELIVERED) {
            requireNotNull(deliveredAt) { "Status DELIVERED wajib punya deliveredAt" }
        }
    }

    val isActive: Boolean get() = status.isActive

    fun issue(actor: String): DispatchShipment {
        require(status == ShipmentStatus.ISSUED) {
            "Hanya shipment berstatus ISSUED yang bisa diubah"
        }
        return copy(updatedAt = Clock.System.now())
    }

    fun markInTransit(now: Instant): DispatchShipment {
        require(status == ShipmentStatus.ISSUED) {
            "Hanya shipment berstatus ISSUED yang bisa ditandai IN_TRANSIT"
        }
        return copy(
            status = ShipmentStatus.IN_TRANSIT,
            shippedAt = now,
            updatedAt = now
        )
    }

    fun confirmDelivered(now: Instant, actor: String): DispatchShipment {
        require(status == ShipmentStatus.IN_TRANSIT) {
            "Hanya shipment berstatus IN_TRANSIT yang bisa dikonfirmasi DELIVERED"
        }
        return copy(
            status = ShipmentStatus.DELIVERED,
            deliveredAt = now,
            updatedAt = now
        )
    }

    fun cancel(actor: String, reason: String, now: Instant): DispatchShipment {
        require(status == ShipmentStatus.ISSUED) {
            "Hanya shipment berstatus ISSUED yang bisa dibatalkan"
        }
        require(reason.isNotBlank()) {
            "Alasan pembatalan wajib diisi untuk audit"
        }
        return copy(
            status = ShipmentStatus.CANCELLED,
            updatedAt = now
        )
    }
}

data class CarrierInfo(
    val mode: String = "",
    val expedition: String = "",
    val container: String = "",
    val driver: String = "",
    val trackingNumber: String = ""
)

data class DestinationInfo(
    val buyer: String = "",
    val address: String = "",
    val contact: String = "",
    val city: String = ""
)

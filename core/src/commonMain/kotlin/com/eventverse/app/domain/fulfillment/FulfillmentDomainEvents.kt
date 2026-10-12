package com.eventverse.app.domain.fulfillment

import com.eventverse.app.domain.fulfillment.carton.CartonId
import com.eventverse.app.domain.fulfillment.carton.CartonNumber
import com.eventverse.app.domain.fulfillment.shipment.ShipmentId
import com.eventverse.app.domain.fulfillment.shipment.ShipmentNumber
import kotlinx.datetime.Instant

/**
 * Domain events untuk packing dan shipping — semuanya past tense.
 *
 * Kejadian domain menjawab "siapa melakukan apa dan kapan" untuk keperluan
 * audit trail dan analisis物流.
 */

// ============== CARTON EVENTS ==============

sealed interface CartonEvent {
    val cartonId: CartonId
    val cartonNumber: CartonNumber
    val occurredAt: Instant
}

/**
 * Karton baru dibuat — biasanya saat operator memulai packing untuk satu production order.
 */
data class CartonCreated(
    override val cartonId: CartonId,
    override val cartonNumber: CartonNumber,
    val tenantId: com.eventverse.app.domain.tenant.TenantId,
    val productionRef: String,
    val createdBy: String,
    override val occurredAt: Instant
) : CartonEvent

/**
 * Karton disegel — saat semua item sudah masuk dan siap untuk shipping manifest.
 */
data class CartonSealed(
    override val cartonId: CartonId,
    override val cartonNumber: CartonNumber,
    val sealNumber: String,
    val grossWeightKg: Double,
    val itemCount: Int,
    val sealedBy: String,
    override val occurredAt: Instant
) : CartonEvent

/**
 * Karton dibuka kembali — untuk koreksi jika ada salah input atau item kurang.
 */
data class CartonReopened(
    override val cartonId: CartonId,
    override val cartonNumber: CartonNumber,
    val reason: String,
    val reopenedBy: String,
    override val occurredAt: Instant
) : CartonEvent

// ============== SHIPMENT EVENTS ==============

sealed interface ShipmentEvent {
    val shipmentId: ShipmentId
    val shipmentNumber: ShipmentNumber
    val occurredAt: Instant
}

/**
 * Surat jalan diterbitkan — shipment dibuat dan siap untuk loading ke kendaraan.
 */
data class ShipmentIssued(
    override val shipmentId: ShipmentId,
    override val shipmentNumber: ShipmentNumber,
    val tenantId: com.eventverse.app.domain.tenant.TenantId,
    val carrierMode: String,
    val expedition: String,
    val destinationCity: String,
    val cartonCount: Int,
    val totalPieces: Int,
    val issuedBy: String,
    override val occurredAt: Instant
) : ShipmentEvent

/**
 * Kendaraan berangkat dari warehouse — mulai perjalanan ke tujuan.
 */
data class ShipmentInTransit(
    override val shipmentId: ShipmentId,
    override val shipmentNumber: ShipmentNumber,
    val carrierMode: String,
    val expedition: String,
    val trackingNumber: String?,
    val departedAt: Instant,
    override val occurredAt: Instant
) : ShipmentEvent

/**
 * Penerima mengkonfirmasi penerimaan barang — shipment selesai.
 */
data class ShipmentDelivered(
    override val shipmentId: ShipmentId,
    override val shipmentNumber: ShipmentNumber,
    val carrierMode: String,
    val expedition: String,
    val destinationCity: String,
    val receivedBy: String,
    val deliveryProof: String?,
    override val occurredAt: Instant
) : ShipmentEvent

/**
 * Shipment dibatalkan — sebelum atau setelah issued.
 */
data class ShipmentCancelled(
    override val shipmentId: ShipmentId,
    override val shipmentNumber: ShipmentNumber,
    val carrierMode: String?,
    val expedition: String?,
    val cancelledBy: String,
    val reason: String,
    override val occurredAt: Instant
) : ShipmentEvent

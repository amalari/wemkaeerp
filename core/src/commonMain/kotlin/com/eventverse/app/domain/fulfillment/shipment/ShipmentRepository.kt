package com.eventverse.app.domain.fulfillment.shipment

import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.domain.fulfillment.carton.CartonId
import kotlinx.datetime.Instant
import kotlin.jvm.JvmInline

/**
 * Operasi domain pengiriman eksternal — bukan DAO generik.
 *
 * Tidak ada `delete`: pengiriman yang dibatalkan dicatat status CANCELLED,
 * bukan dihapus untuk menjaga jejak audit.
 */
interface ShipmentRepository {

    suspend fun findById(tenantId: TenantId, id: ShipmentId): DispatchShipment?

    suspend fun findAll(tenantId: TenantId): List<DispatchShipment>

    suspend fun findByStatus(tenantId: TenantId, status: ShipmentStatus): List<DispatchShipment>

    suspend fun findByNumber(tenantId: TenantId, number: ShipmentNumber): DispatchShipment?

    suspend fun findActiveShipments(tenantId: TenantId): List<DispatchShipment>

    suspend fun findByCartonId(tenantId: TenantId, cartonId: CartonId): List<DispatchShipment>

    suspend fun save(shipment: DispatchShipment): DispatchShipment

    suspend fun recordEvent(
        tenantId: TenantId,
        shipmentId: ShipmentId,
        eventType: String,
        actor: String,
        detail: String,
        occurredAt: Instant
    )

    suspend fun getNextShipmentSequence(tenantId: TenantId, year: Int): Int

    suspend fun linkCartons(tenantId: TenantId, shipmentId: ShipmentId, cartonIds: List<CartonId>)

    suspend fun unlinkCartons(tenantId: TenantId, shipmentId: ShipmentId, cartonIds: List<CartonId>)
}

@JvmInline
value class ShipmentId(val value: String) {
    init {
        require(value.isNotBlank()) { "ShipmentId tidak boleh kosong" }
        require(value.length <= 64) { "ShipmentId maksimal 64 karakter" }
    }
}

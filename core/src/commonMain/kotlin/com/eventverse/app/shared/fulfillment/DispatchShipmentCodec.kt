package com.eventverse.app.shared.fulfillment

import com.eventverse.app.domain.fulfillment.carton.CartonId
import com.eventverse.app.domain.fulfillment.shipment.CarrierInfo
import com.eventverse.app.domain.fulfillment.shipment.DestinationInfo
import com.eventverse.app.domain.fulfillment.shipment.DispatchShipment
import com.eventverse.app.domain.fulfillment.shipment.ShipmentId
import com.eventverse.app.domain.fulfillment.shipment.ShipmentNumber
import com.eventverse.app.domain.fulfillment.shipment.ShipmentStatus
import com.eventverse.app.domain.fulfillment.shipment.StockOwnership
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.shared.common.DateTimeCodec
import com.eventverse.app.shared.json.JsonValue
import com.eventverse.app.shared.json.jsonArrayOf
import com.eventverse.app.shared.json.jsonObjectOf
import com.eventverse.app.shared.json.jsonOf
import kotlinx.datetime.Instant

/**
 * Codec pengiriman eksternal (SJ keluar pabrik). Cermin domain, bukan sebaliknya.
 *
 * `cartonIds` mencerminkan pivot `shipment_cartons` — daftar karton yang terikat shipment.
 * Status tak dikenal **ditolak**, bukan jatuh ke default (Kontrak 4 variability).
 */
object DispatchShipmentCodec {

    private val EPOCH = Instant.fromEpochMilliseconds(0)

    fun encode(shipment: DispatchShipment, cartonIds: List<CartonId> = emptyList()): JsonValue.Obj = jsonObjectOf(
        "id" to jsonOf(shipment.id.value),
        "tenantId" to jsonOf(shipment.tenantId.value),
        "shipmentNumber" to jsonOf(shipment.shipmentNumber.value),
        "stockOwnership" to jsonOf(shipment.stockOwnership.name),
        "carrier" to encodeCarrier(shipment.carrier),
        "destination" to encodeDestination(shipment.destination),
        "cartonIds" to jsonArrayOf(cartonIds.map { jsonOf(it.value) }),
        "status" to jsonOf(shipment.status.name),
        "statusLabel" to jsonOf(shipment.status.displayName),
        "shippedAt" to (shipment.shippedAt?.let { jsonOf(it.toString()) } ?: JsonValue.Null),
        "deliveredAt" to (shipment.deliveredAt?.let { jsonOf(it.toString()) } ?: JsonValue.Null),
        "createdAt" to jsonOf(shipment.createdAt.toString()),
        "updatedAt" to jsonOf(shipment.updatedAt.toString()),
        "notes" to jsonOf(shipment.notes)
    )

    fun decode(obj: JsonValue.Obj): DecodedShipment = DecodedShipment(
        shipment = DispatchShipment(
            id = ShipmentId(obj.string("id") ?: ""),
            tenantId = TenantId(obj.string("tenantId") ?: ""),
            // Format nomor tidak sah ditolak oleh value object — bukan dipaksa kosong.
            shipmentNumber = ShipmentNumber(obj.string("shipmentNumber") ?: ""),
            stockOwnership = enumOrNull<StockOwnership>(obj.string("stockOwnership"))
                ?: throw IllegalArgumentException("StockOwnership tak dikenal: ${obj.string("stockOwnership")}"),
            carrier = obj.obj("carrier")?.let(::decodeCarrier) ?: CarrierInfo(),
            destination = obj.obj("destination")?.let(::decodeDestination) ?: DestinationInfo(),
            status = enumOrNull<ShipmentStatus>(obj.string("status"))
                ?: throw IllegalArgumentException("Status shipment tak dikenal: ${obj.string("status")}"),
            shippedAt = obj.string("shippedAt")?.takeIf { it.isNotBlank() }?.let(Instant::parse),
            deliveredAt = obj.string("deliveredAt")?.takeIf { it.isNotBlank() }?.let(Instant::parse),
            createdAt = DateTimeCodec.parseInstantOrFallback(
                obj.string("createdAt"),
                DateTimeCodec.parseInstantOrFallback(obj.string("updatedAt"), EPOCH)
            ),
            updatedAt = DateTimeCodec.parseInstantOrFallback(obj.string("updatedAt"), EPOCH),
            notes = obj.string("notes") ?: ""
        ),
        cartonIds = obj.stringArray("cartonIds").map(::CartonId)
    )

    private fun encodeCarrier(carrier: CarrierInfo): JsonValue.Obj = jsonObjectOf(
        "mode" to jsonOf(carrier.mode),
        "expedition" to jsonOf(carrier.expedition),
        "container" to jsonOf(carrier.container),
        "driver" to jsonOf(carrier.driver),
        "trackingNumber" to jsonOf(carrier.trackingNumber)
    )

    private fun decodeCarrier(obj: JsonValue.Obj) = CarrierInfo(
        mode = obj.string("mode") ?: "",
        expedition = obj.string("expedition") ?: "",
        container = obj.string("container") ?: "",
        driver = obj.string("driver") ?: "",
        trackingNumber = obj.string("trackingNumber") ?: ""
    )

    private fun encodeDestination(destination: DestinationInfo): JsonValue.Obj = jsonObjectOf(
        "buyer" to jsonOf(destination.buyer),
        "address" to jsonOf(destination.address),
        "contact" to jsonOf(destination.contact),
        "city" to jsonOf(destination.city)
    )

    private fun decodeDestination(obj: JsonValue.Obj) = DestinationInfo(
        buyer = obj.string("buyer") ?: "",
        address = obj.string("address") ?: "",
        contact = obj.string("contact") ?: "",
        city = obj.string("city") ?: ""
    )

    private inline fun <reified T : Enum<T>> enumOrNull(raw: String?): T? =
        raw?.let { value -> enumValues<T>().find { it.name == value } }

    /** Hasil baca: shipment + karton yang terikat (pivot `shipment_cartons`). */
    data class DecodedShipment(val shipment: DispatchShipment, val cartonIds: List<CartonId>)
}

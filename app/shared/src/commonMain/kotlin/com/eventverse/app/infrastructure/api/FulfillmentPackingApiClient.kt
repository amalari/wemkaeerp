package com.eventverse.app.infrastructure.api

import com.eventverse.app.domain.fulfillment.carton.CartonId
import com.eventverse.app.domain.fulfillment.carton.PackingCarton
import com.eventverse.app.domain.fulfillment.shipment.CarrierInfo
import com.eventverse.app.domain.fulfillment.shipment.DestinationInfo
import com.eventverse.app.domain.fulfillment.shipment.DispatchShipment
import com.eventverse.app.domain.fulfillment.shipment.ShipmentId
import com.eventverse.app.domain.fulfillment.shipment.StockOwnership
import com.eventverse.app.shared.fulfillment.PackingCartonCodec
import com.eventverse.app.shared.fulfillment.DispatchShipmentCodec
import com.eventverse.app.shared.json.JsonParser
import com.eventverse.app.shared.json.JsonValue
import com.eventverse.app.shared.json.jsonArrayOf
import com.eventverse.app.shared.json.jsonObjectOf
import com.eventverse.app.shared.json.jsonOf
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.CancellationException

/**
 * API client untuk modul Packing & Pengiriman Eksternal (TRD-FUL-001 Track C).
 *
 * Endpoint:
 * - GET /packing-queue — antrian unit + karton OPEN + shipment aktif
 * - POST /cartons — buat karton baru
 * - PATCH /cartons/{id}/seal — segel karton
 * - PATCH /cartons/{id}/reopen — buka kembali karton (MANAGE)
 * - POST /shipments — terbitkan SJ eksternal
 * - PATCH /shipments/{id}/in-transit — tandai sedang diantar
 * - PATCH /shipments/{id}/delivered — konfirmasi terkirim
 * - DELETE /shipments/{id} — batalkan SJ (MANAGE)
 */
interface FulfillmentPackingRemoteDataSource {

    /** Ambil antrian packing: karton OPEN + shipment aktif. */
    suspend fun packingQueue(tenantSlug: String): Result<PackingQueue>

    /** Buat karton packing baru. */
    suspend fun createCarton(
        tenantSlug: String,
        productionRef: String,
        notes: String = ""
    ): Result<PackingCarton>

    /** Segel karton (karton wajib tidak kosong). */
    suspend fun sealCarton(
        tenantSlug: String,
        cartonId: String,
        sealNumber: String,
        grossWeightKg: Double,
        actor: String
    ): Result<PackingCarton>

    /** Buka kembali karton yang sudah disegel (butuh alasan, MANAGE). */
    suspend fun reopenCarton(
        tenantSlug: String,
        cartonId: String,
        reason: String,
        actor: String
    ): Result<PackingCarton>

    /** Terbitkan Surat Jalan (pengiriman eksternal). */
    suspend fun issueShipment(
        tenantSlug: String,
        stockOwnership: StockOwnership,
        carrier: CarrierInfo,
        destination: DestinationInfo,
        cartonIds: List<String>,
        notes: String = ""
    ): Result<DispatchShipment>

    /** Tandai SJ sedang dalam perjalanan. */
    suspend fun markInTransit(
        tenantSlug: String,
        shipmentId: String,
        actor: String
    ): Result<DispatchShipment>

    /** Konfirmasi SJ sudah terkirim. */
    suspend fun confirmDelivered(
        tenantSlug: String,
        shipmentId: String,
        actor: String
    ): Result<DispatchShipment>

    /** Batalkan SJ (hanya ISSUED, butuh alasan). */
    suspend fun cancelShipment(
        tenantSlug: String,
        shipmentId: String,
        reason: String,
        actor: String
    ): Result<DispatchShipment>
}

/** Respons endpoint /packing-queue. */
data class PackingQueue(
    val cartons: List<PackingCarton>,
    val shipments: List<DispatchShipment>
)

class FulfillmentPackingApiClient(
    private val httpClient: HttpClient = HttpClient(),
    private val baseUrl: String = "",
    private val tokenProvider: SessionTokenProvider = StoredSessionTokenProvider
) : FulfillmentPackingRemoteDataSource {

    private fun resolveUrl(path: String): String =
        if (baseUrl.isNotBlank()) "${baseUrl.trimEnd('/')}$path" else path

    override suspend fun packingQueue(tenantSlug: String): Result<PackingQueue> = apiResult {
        val response = httpClient.get(resolveUrl("$BASE_PATH/packing-queue")) {
            tenantRequest(tenantSlug, tokenProvider)
            accept(ContentType.Application.Json)
        }
        val parsed = JsonParser.parse(response.requireBody("memuat antrian packing")) as? JsonValue.Obj
            ?: throw IllegalArgumentException("\$: respons antrian packing harus objek")

        val cartonsArr = parsed["cartons"] as? JsonValue.Arr
        val cartons = cartonsArr?.items?.filterIsInstance<JsonValue.Obj>()?.map {
            PackingCartonCodec.decode(it)
        }.orEmpty()

        val shipmentsArr = parsed["shipments"] as? JsonValue.Arr
        val shipments = shipmentsArr?.items?.filterIsInstance<JsonValue.Obj>()?.map {
            DispatchShipmentCodec.decode(it).shipment
        }.orEmpty()

        PackingQueue(cartons = cartons, shipments = shipments)
    }

    override suspend fun createCarton(
        tenantSlug: String,
        productionRef: String,
        notes: String
    ): Result<PackingCarton> = apiResult {
        val body = jsonObjectOf(
            "productionRef" to jsonOf(productionRef),
            "notes" to jsonOf(notes)
        )
        val response = httpClient.post(resolveUrl("$BASE_PATH/cartons")) {
            tenantRequest(tenantSlug, tokenProvider)
            contentType(ContentType.Application.Json)
            setBody(body.encode())
        }
        val parsed = JsonParser.parse(response.requireBody("membuat karton")) as? JsonValue.Obj
            ?: throw IllegalArgumentException("\$: respons pembuatan karton harus objek")
        PackingCartonCodec.decode(parsed)
    }

    override suspend fun sealCarton(
        tenantSlug: String,
        cartonId: String,
        sealNumber: String,
        grossWeightKg: Double,
        actor: String
    ): Result<PackingCarton> = apiResult {
        val body = jsonObjectOf(
            "sealNumber" to jsonOf(sealNumber),
            "grossWeightKg" to jsonOf(grossWeightKg),
            "actor" to jsonOf(actor)
        )
        val response = httpClient.patch(resolveUrl("$BASE_PATH/cartons/$cartonId/seal")) {
            tenantRequest(tenantSlug, tokenProvider)
            contentType(ContentType.Application.Json)
            setBody(body.encode())
        }
        val parsed = JsonParser.parse(response.requireBody("menyegel karton")) as? JsonValue.Obj
            ?: throw IllegalArgumentException("\$: respons penyegelan karton harus objek")
        PackingCartonCodec.decode(parsed)
    }

    override suspend fun reopenCarton(
        tenantSlug: String,
        cartonId: String,
        reason: String,
        actor: String
    ): Result<PackingCarton> = apiResult {
        val body = jsonObjectOf(
            "reason" to jsonOf(reason),
            "actor" to jsonOf(actor)
        )
        val response = httpClient.patch(resolveUrl("$BASE_PATH/cartons/$cartonId/reopen")) {
            tenantRequest(tenantSlug, tokenProvider)
            contentType(ContentType.Application.Json)
            setBody(body.encode())
        }
        val parsed = JsonParser.parse(response.requireBody("membuka karton")) as? JsonValue.Obj
            ?: throw IllegalArgumentException("\$: respons pembukan karton harus objek")
        PackingCartonCodec.decode(parsed)
    }

    override suspend fun issueShipment(
        tenantSlug: String,
        stockOwnership: StockOwnership,
        carrier: CarrierInfo,
        destination: DestinationInfo,
        cartonIds: List<String>,
        notes: String
    ): Result<DispatchShipment> = apiResult {
        val body = jsonObjectOf(
            "stockOwnership" to jsonOf(stockOwnership.name),
            "carrier" to jsonObjectOf(
                "mode" to jsonOf(carrier.mode),
                "expedition" to jsonOf(carrier.expedition),
                "container" to jsonOf(carrier.container),
                "driver" to jsonOf(carrier.driver),
                "trackingNumber" to jsonOf(carrier.trackingNumber)
            ),
            "destination" to jsonObjectOf(
                "buyer" to jsonOf(destination.buyer),
                "address" to jsonOf(destination.address),
                "contact" to jsonOf(destination.contact),
                "city" to jsonOf(destination.city)
            ),
            "cartonIds" to jsonArrayOf(cartonIds.map(::jsonOf)),
            "notes" to jsonOf(notes)
        )
        val response = httpClient.post(resolveUrl("$BASE_PATH/shipments")) {
            tenantRequest(tenantSlug, tokenProvider)
            contentType(ContentType.Application.Json)
            setBody(body.encode())
        }
        val parsed = JsonParser.parse(response.requireBody("menerbitkan SJ")) as? JsonValue.Obj
            ?: throw IllegalArgumentException("\$: respons penerbitan SJ harus objek")
        DispatchShipmentCodec.decode(parsed).shipment
    }

    override suspend fun markInTransit(
        tenantSlug: String,
        shipmentId: String,
        actor: String
    ): Result<DispatchShipment> = apiResult {
        val body = jsonObjectOf("actor" to jsonOf(actor))
        val response = httpClient.patch(resolveUrl("$BASE_PATH/shipments/$shipmentId/in-transit")) {
            tenantRequest(tenantSlug, tokenProvider)
            contentType(ContentType.Application.Json)
            setBody(body.encode())
        }
        val parsed = JsonParser.parse(response.requireBody("menandai SJ")) as? JsonValue.Obj
            ?: throw IllegalArgumentException("\$: respons penandaan SJ harus objek")
        DispatchShipmentCodec.decode(parsed).shipment
    }

    override suspend fun confirmDelivered(
        tenantSlug: String,
        shipmentId: String,
        actor: String
    ): Result<DispatchShipment> = apiResult {
        val body = jsonObjectOf("actor" to jsonOf(actor))
        val response = httpClient.patch(resolveUrl("$BASE_PATH/shipments/$shipmentId/delivered")) {
            tenantRequest(tenantSlug, tokenProvider)
            contentType(ContentType.Application.Json)
            setBody(body.encode())
        }
        val parsed = JsonParser.parse(response.requireBody("mengonfirmasi SJ")) as? JsonValue.Obj
            ?: throw IllegalArgumentException("\$: respons konfirmasi SJ harus objek")
        DispatchShipmentCodec.decode(parsed).shipment
    }

    override suspend fun cancelShipment(
        tenantSlug: String,
        shipmentId: String,
        reason: String,
        actor: String
    ): Result<DispatchShipment> = apiResult {
        val body = jsonObjectOf(
            "reason" to jsonOf(reason),
            "actor" to jsonOf(actor)
        )
        val response = httpClient.delete(resolveUrl("$BASE_PATH/shipments/$shipmentId")) {
            tenantRequest(tenantSlug, tokenProvider)
            contentType(ContentType.Application.Json)
            setBody(body.encode())
        }
        val parsed = JsonParser.parse(response.requireBody("membatalkan SJ")) as? JsonValue.Obj
            ?: throw IllegalArgumentException("\$: respons pembatalan SJ harus objek")
        DispatchShipmentCodec.decode(parsed).shipment
    }

    private suspend fun HttpResponse.requireBody(action: String): String {
        val body = bodyAsText()
        if (!status.isSuccess()) {
            error(body.ifBlank { "Gagal $action (HTTP ${status.value})" })
        }
        return body
    }

    companion object {
        private const val BASE_PATH = "/api/tenant/fulfillment"
    }
}

/** Menjaga pembatalan coroutine tetap merambat; kegagalan HTTP/codec tetap menjadi kontrak [Result]. */
private suspend inline fun <T> apiResult(crossinline block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (failure: Throwable) {
    Result.failure(failure)
}

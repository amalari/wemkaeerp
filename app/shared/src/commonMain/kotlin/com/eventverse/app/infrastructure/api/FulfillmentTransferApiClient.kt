package com.eventverse.app.infrastructure.api

import com.eventverse.app.domain.fulfillment.FulfillmentRouteConfig
import com.eventverse.app.domain.fulfillment.HandoverMode
import com.eventverse.app.domain.fulfillment.HandoverProof
import com.eventverse.app.domain.fulfillment.HandoverRoute
import com.eventverse.app.domain.fulfillment.HandoverRouteCode
import com.eventverse.app.domain.fulfillment.HandoverRouteSetting
import com.eventverse.app.domain.fulfillment.HandoverRouteSettingsView
import com.eventverse.app.domain.fulfillment.InternalTransfer
import com.eventverse.app.domain.fulfillment.SackRoute
import com.eventverse.app.domain.fulfillment.toRouteCode
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.domain.transfer.FlowNodeRef
import com.eventverse.app.shared.fulfillment.FulfillmentRouteConfigCodec
import com.eventverse.app.shared.fulfillment.HandoverRouteSettingsCodec
import com.eventverse.app.shared.fulfillment.InternalTransferCodec
import com.eventverse.app.shared.json.JsonParser
import com.eventverse.app.shared.json.JsonValue
import com.eventverse.app.shared.json.jsonArrayOf
import com.eventverse.app.shared.json.jsonObjectOf
import com.eventverse.app.shared.json.jsonOf
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*

interface FulfillmentTransferRemoteDataSource {

    suspend fun transfers(tenantSlug: String): Result<List<InternalTransfer>>

    /** Pola serah terima tiap rute (backward compatible). */
    suspend fun routeSettings(tenantSlug: String): Result<FulfillmentRouteConfig>

    /** Pola serah terima tiap rute berbasis data (TRD-FLOW-003). */
    suspend fun routeSettingsView(tenantSlug: String): Result<HandoverRouteSettingsView>

    /** Mengubah mode serah terima per rute (PUT /route-settings). */
    suspend fun updateRouteModes(
        tenantSlug: String,
        modes: Map<HandoverRouteCode, HandoverMode>
    ): Result<Unit>

    /** Memuat seluruh rute serah terima milik tenant (GET /routes). */
    suspend fun routes(tenantSlug: String): Result<List<HandoverRoute>>

    /** Memperbarui daftar rute tenant (PUT /routes). */
    suspend fun updateRoutes(
        tenantSlug: String,
        routes: List<HandoverRoute>
    ): Result<Unit>

    suspend fun submit(
        tenantSlug: String,
        sackPayload: String,
        routeCode: HandoverRouteCode,
        dispatchWeightKg: String?,
        dispatchScalePhotoKey: String?,
        requestedBy: String,
        notes: String,
        declaredPcs: Int?
    ): Result<InternalTransfer>

    suspend fun submit(
        tenantSlug: String,
        sackPayload: String,
        leg: SackRoute,
        dispatchWeightKg: String?,
        dispatchScalePhotoKey: String?,
        requestedBy: String,
        notes: String,
        declaredPcs: Int?
    ): Result<InternalTransfer> = submit(
        tenantSlug = tenantSlug,
        sackPayload = sackPayload,
        routeCode = leg.toRouteCode(),
        dispatchWeightKg = dispatchWeightKg,
        dispatchScalePhotoKey = dispatchScalePhotoKey,
        requestedBy = requestedBy,
        notes = notes,
        declaredPcs = declaredPcs
    )

    suspend fun approve(
        tenantSlug: String,
        transferId: String,
        approverName: String,
        signatureKey: String
    ): Result<InternalTransfer>

    suspend fun reject(
        tenantSlug: String,
        transferId: String,
        reason: String,
        approverName: String
    ): Result<InternalTransfer>

    suspend fun resubmit(
        tenantSlug: String,
        transferId: String,
        dispatchWeightKg: String,
        dispatchScalePhotoKey: String,
        requestedBy: String
    ): Result<InternalTransfer>

    suspend fun receive(
        tenantSlug: String,
        transferId: String,
        proof: HandoverProof,
        receivedWeightKg: String?,
        receivedPcs: Int?,
        recordedBy: String
    ): Result<InternalTransfer>

    /** Mengunggah bukti (foto timbangan / TTD / resi); mengembalikan key penyimpanannya. */
    suspend fun uploadEvidence(
        tenantSlug: String,
        fileName: String,
        contentType: String,
        bytes: ByteArray
    ): Result<String>
}

class FulfillmentTransferApiClient(
    private val httpClient: HttpClient = HttpClient(),
    private val baseUrl: String = "",
    private val tokenProvider: SessionTokenProvider = StoredSessionTokenProvider
) : FulfillmentTransferRemoteDataSource {

    private fun resolveUrl(path: String): String =
        if (baseUrl.isNotBlank()) "${baseUrl.trimEnd('/')}$path" else path

    override suspend fun transfers(tenantSlug: String): Result<List<InternalTransfer>> = runCatching {
        val response = httpClient.get(resolveUrl("$BASE_PATH/transfers")) {
            tenantRequest(tenantSlug, tokenProvider)
            accept(ContentType.Application.Json)
        }
        val parsed = JsonParser.parse(response.requireBody("memuat daftar transfer")) as? JsonValue.Arr
            ?: return@runCatching emptyList()
        parsed.items.filterIsInstance<JsonValue.Obj>().map(InternalTransferCodec::decode)
    }

    override suspend fun routeSettingsView(tenantSlug: String): Result<HandoverRouteSettingsView> = runCatching {
        val response = httpClient.get(resolveUrl("$BASE_PATH/route-settings")) {
            tenantRequest(tenantSlug, tokenProvider)
            accept(ContentType.Application.Json)
        }
        val parsed = JsonParser.parse(response.requireBody("memuat pola serah terima")) as? JsonValue.Obj
            ?: return@runCatching HandoverRouteSettingsView(TenantId(tenantSlug), emptyList())
        decodeRouteSettingsView(parsed, TenantId(tenantSlug))
    }

    override suspend fun routeSettings(tenantSlug: String): Result<FulfillmentRouteConfig> = runCatching {
        val response = httpClient.get(resolveUrl("$BASE_PATH/route-settings")) {
            tenantRequest(tenantSlug, tokenProvider)
            accept(ContentType.Application.Json)
        }
        val parsed = JsonParser.parse(response.requireBody("memuat pola serah terima")) as? JsonValue.Obj
            ?: return@runCatching FulfillmentRouteConfig(TenantId(tenantSlug))
        FulfillmentRouteConfigCodec.decode(parsed, TenantId(tenantSlug))
    }

    override suspend fun updateRouteModes(
        tenantSlug: String,
        modes: Map<HandoverRouteCode, HandoverMode>
    ): Result<Unit> = runCatching {
        val body = jsonObjectOf(
            "routes" to jsonArrayOf(
                modes.map { (code, mode) ->
                    jsonObjectOf(
                        "route" to jsonOf(code.value),
                        "mode" to jsonOf(mode.name)
                    )
                }
            )
        )
        val response = httpClient.put(resolveUrl("$BASE_PATH/route-settings")) {
            tenantRequest(tenantSlug, tokenProvider)
            contentType(ContentType.Application.Json)
            setBody(body.encode())
        }
        response.requireBody("memperbarui pola serah terima")
    }

    override suspend fun routes(tenantSlug: String): Result<List<HandoverRoute>> = runCatching {
        val response = httpClient.get(resolveUrl("$BASE_PATH/routes")) {
            tenantRequest(tenantSlug, tokenProvider)
            accept(ContentType.Application.Json)
        }
        val parsed = JsonParser.parse(response.requireBody("memuat daftar rute")) as? JsonValue.Obj
            ?: return@runCatching emptyList()
        HandoverRouteSettingsCodec.decodeRoutes(parsed)
    }

    override suspend fun updateRoutes(
        tenantSlug: String,
        routes: List<HandoverRoute>
    ): Result<Unit> = runCatching {
        val body = HandoverRouteSettingsCodec.encodeRoutes(routes)
        val response = httpClient.put(resolveUrl("$BASE_PATH/routes")) {
            tenantRequest(tenantSlug, tokenProvider)
            contentType(ContentType.Application.Json)
            setBody(body.encode())
        }
        response.requireBody("memperbarui daftar rute")
    }

    override suspend fun submit(
        tenantSlug: String,
        sackPayload: String,
        routeCode: HandoverRouteCode,
        dispatchWeightKg: String?,
        dispatchScalePhotoKey: String?,
        requestedBy: String,
        notes: String,
        declaredPcs: Int?
    ): Result<InternalTransfer> = postTransfer(
        tenantSlug, "$BASE_PATH/transfers", jsonObjectOf(
            "sackCode" to jsonOf(sackPayload),
            "leg" to jsonOf(routeCode.value),
            "dispatchWeightKg" to (dispatchWeightKg?.let(::jsonOf) ?: JsonValue.Null),
            "dispatchScalePhotoKey" to (dispatchScalePhotoKey?.let(::jsonOf) ?: JsonValue.Null),
            "declaredPcs" to (declaredPcs?.let(::jsonOf) ?: JsonValue.Null),
            "requestedBy" to jsonOf(requestedBy),
            "notes" to jsonOf(notes)
        ), "mengajukan transfer"
    )

    override suspend fun approve(
        tenantSlug: String,
        transferId: String,
        approverName: String,
        signatureKey: String
    ): Result<InternalTransfer> = postTransfer(
        tenantSlug, "$BASE_PATH/transfers/$transferId/approve", jsonObjectOf(
            "approverName" to jsonOf(approverName),
            "signatureKey" to jsonOf(signatureKey)
        ), "menyetujui transfer"
    )

    override suspend fun reject(
        tenantSlug: String,
        transferId: String,
        reason: String,
        approverName: String
    ): Result<InternalTransfer> = postTransfer(
        tenantSlug, "$BASE_PATH/transfers/$transferId/reject", jsonObjectOf(
            "reason" to jsonOf(reason),
            "approverName" to jsonOf(approverName)
        ), "menolak transfer"
    )

    override suspend fun resubmit(
        tenantSlug: String,
        transferId: String,
        dispatchWeightKg: String,
        dispatchScalePhotoKey: String,
        requestedBy: String
    ): Result<InternalTransfer> = postTransfer(
        tenantSlug, "$BASE_PATH/transfers/$transferId/resubmit", jsonObjectOf(
            "dispatchWeightKg" to jsonOf(dispatchWeightKg),
            "dispatchScalePhotoKey" to jsonOf(dispatchScalePhotoKey),
            "requestedBy" to jsonOf(requestedBy)
        ), "mengajukan ulang transfer"
    )

    override suspend fun receive(
        tenantSlug: String,
        transferId: String,
        proof: HandoverProof,
        receivedWeightKg: String?,
        receivedPcs: Int?,
        recordedBy: String
    ): Result<InternalTransfer> {
        val fields = mutableMapOf<String, JsonValue>(
            "receivedWeightKg" to (receivedWeightKg?.let(::jsonOf) ?: JsonValue.Null),
            "receivedPcs" to (receivedPcs?.let(::jsonOf) ?: JsonValue.Null),
            "recordedBy" to jsonOf(recordedBy)
        )
        when (proof) {
            is HandoverProof.ReceiverHandover -> {
                fields["handoverType"] = jsonOf("RECEIVER")
                fields["receiverName"] = jsonOf(proof.receiverName)
                fields["receiverSignatureKey"] = proof.signatureKey?.let(::jsonOf) ?: JsonValue.Null
                fields["handoverPhotoKey"] = jsonOf(proof.evidencePhotoKey)
            }
            is HandoverProof.CourierShipment -> {
                fields["handoverType"] = jsonOf("COURIER")
                fields["carrier"] = jsonOf(proof.carrier)
                fields["trackingNumber"] = jsonOf(proof.trackingNumber)
                fields["chargeableWeightKg"] = jsonOf(proof.chargeableWeightKg.value.toString())
                fields["handoverPhotoKey"] = jsonOf(proof.evidencePhotoKey)
            }
        }
        return postTransfer(
            tenantSlug, "$BASE_PATH/transfers/$transferId/receive",
            jsonObjectOf(*fields.map { it.toPair() }.toTypedArray()), "mencatat penerimaan"
        )
    }

    override suspend fun uploadEvidence(
        tenantSlug: String,
        fileName: String,
        contentType: String,
        bytes: ByteArray
    ): Result<String> = runCatching {
        val response = httpClient.post(
            resolveUrl("$BASE_PATH/evidence/upload?fileName=$fileName&contentType=$contentType")
        ) {
            tenantRequest(tenantSlug, tokenProvider)
            setBody(bytes)
        }
        JsonParser.parseObject(response.requireBody("mengunggah bukti"))
            .string("key") ?: error("Server tidak mengembalikan key berkas")
    }

    private suspend fun postTransfer(
        tenantSlug: String,
        url: String,
        body: com.eventverse.app.shared.json.JsonValue.Obj,
        action: String
    ): Result<InternalTransfer> = runCatching {
        val response = httpClient.post(resolveUrl(url)) {
            tenantRequest(tenantSlug, tokenProvider)
            contentType(ContentType.Application.Json)
            setBody(body.encode())
        }
        InternalTransferCodec.decode(JsonParser.parseObject(response.requireBody(action)))
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

        /** Dekode payload route-settings menjadi [HandoverRouteSettingsView]. */
        internal fun decodeRouteSettingsView(parsed: JsonValue.Obj, tenantId: TenantId): HandoverRouteSettingsView {
            val rows = parsed["routes"] as? JsonValue.Arr
                ?: return HandoverRouteSettingsView(tenantId, emptyList())
            val settings = rows.items.mapNotNull { item ->
                val row = item as? JsonValue.Obj ?: return@mapNotNull null
                val rawCode = row.string("route") ?: return@mapNotNull null
                val code = HandoverRouteCode.parse(rawCode).getOrNull() ?: return@mapNotNull null
                val label = row.string("routeLabel") ?: code.value
                val modeName = row.string("mode") ?: HandoverMode.ADMIN_HUB.name
                val mode = HandoverMode.entries.firstOrNull { it.name == modeName } ?: HandoverMode.ADMIN_HUB
                val isExplicit = row.boolean("isExplicit") ?: false
                val active = row.boolean("active") ?: true
                val sortOrder = row.int("sortOrder") ?: 0
                val fromRef = row.string("from")?.let(FlowNodeRef::parse)
                val toRef = row.string("to")?.let(FlowNodeRef::parse)
                HandoverRouteSetting(
                    route = HandoverRoute(
                        code = code,
                        label = label,
                        from = fromRef,
                        to = toRef,
                        sortOrder = sortOrder,
                        active = active
                    ),
                    mode = mode,
                    isExplicit = isExplicit
                )
            }
            return HandoverRouteSettingsView(tenantId, settings)
        }
    }
}

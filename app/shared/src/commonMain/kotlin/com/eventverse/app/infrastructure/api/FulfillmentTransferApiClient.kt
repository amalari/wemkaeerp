package com.eventverse.app.infrastructure.api

import com.eventverse.app.domain.fulfillment.HandoverProof
import com.eventverse.app.domain.fulfillment.InternalTransfer
import com.eventverse.app.domain.fulfillment.SackRoute
import com.eventverse.app.shared.fulfillment.InternalTransferCodec
import com.eventverse.app.shared.json.JsonParser
import com.eventverse.app.shared.json.JsonValue
import com.eventverse.app.shared.json.jsonObjectOf
import com.eventverse.app.shared.json.jsonOf
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*

interface FulfillmentTransferRemoteDataSource {

    suspend fun transfers(tenantSlug: String): Result<List<InternalTransfer>>

    suspend fun submit(
        tenantSlug: String,
        sackPayload: String,
        leg: SackRoute,
        dispatchWeightKg: String,
        dispatchScalePhotoKey: String,
        requestedBy: String,
        notes: String
    ): Result<InternalTransfer>

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

    override suspend fun submit(
        tenantSlug: String,
        sackPayload: String,
        leg: SackRoute,
        dispatchWeightKg: String,
        dispatchScalePhotoKey: String,
        requestedBy: String,
        notes: String
    ): Result<InternalTransfer> = postTransfer(
        tenantSlug, "$BASE_PATH/transfers", jsonObjectOf(
            "sackCode" to jsonOf(sackPayload),
            "leg" to jsonOf(leg.name),
            "dispatchWeightKg" to jsonOf(dispatchWeightKg),
            "dispatchScalePhotoKey" to jsonOf(dispatchScalePhotoKey),
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
                fields["receiverSignatureKey"] = jsonOf(proof.signatureKey)
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

    private companion object {
        const val BASE_PATH = "/api/tenant/fulfillment"
    }
}

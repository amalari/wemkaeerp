package com.eventverse.app.infrastructure.api

import com.eventverse.app.domain.sampling.SamplingOrder
import com.eventverse.app.domain.sampling.storage.SampleStorageStatus
import com.eventverse.app.shared.json.JsonParser
import com.eventverse.app.shared.json.JsonValue
import com.eventverse.app.shared.json.jsonObjectOf
import com.eventverse.app.shared.json.jsonOf
import com.eventverse.app.shared.sampling.SampleStorageCodec
import com.eventverse.app.shared.sampling.SamplingOrderCodec
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess

/** Hasil masuk/keluar penyimpanan: SPK yang sudah pindah tahap + gambaran kustodinya. */
data class SampleStorageResult(val order: SamplingOrder, val storage: SampleStorageStatus)

/** Kustodi penyimpanan sampel (Pengemasan → Penyimpanan → Terkirim). */
interface SamplingStorageRemoteDataSource {
    suspend fun getStorage(tenantSlug: String, orderId: String): Result<SampleStorageStatus>
    suspend fun store(tenantSlug: String, orderId: String, locationLabel: String, qtyPcs: Int): Result<SampleStorageResult>
    suspend fun release(tenantSlug: String, orderId: String, partialReason: String?): Result<SampleStorageResult>
}

class SamplingStorageApiClient(
    private val httpClient: HttpClient = HttpClient(),
    private val baseUrl: String = "",
    private val tokenProvider: SessionTokenProvider = StoredSessionTokenProvider
) : SamplingStorageRemoteDataSource {

    private fun resolveUrl(path: String): String =
        if (baseUrl.isNotBlank()) "${baseUrl.trimEnd('/')}$path" else path

    override suspend fun getStorage(tenantSlug: String, orderId: String): Result<SampleStorageStatus> = runCatching {
        val response = httpClient.get(resolveUrl("$ORDERS_PATH/$orderId/storage")) {
            tenantRequest(tenantSlug, tokenProvider)
        }
        val parsed = JsonParser.parse(response.requireBody("memuat data penyimpanan")) as? JsonValue.Obj
            ?: error("Respons penyimpanan tidak valid")
        SampleStorageCodec.decodeStatus(parsed)
    }

    override suspend fun store(
        tenantSlug: String,
        orderId: String,
        locationLabel: String,
        qtyPcs: Int
    ): Result<SampleStorageResult> = post(
        tenantSlug = tenantSlug,
        path = "$ORDERS_PATH/$orderId/store",
        payload = jsonObjectOf("locationLabel" to jsonOf(locationLabel), "qtyPcs" to jsonOf(qtyPcs)),
        action = "menyimpan barang"
    )

    override suspend fun release(tenantSlug: String, orderId: String, partialReason: String?): Result<SampleStorageResult> =
        post(
            tenantSlug = tenantSlug,
            path = "$ORDERS_PATH/$orderId/release",
            payload = jsonObjectOf("partialReason" to jsonOf(partialReason)),
            action = "melepas barang ke pengiriman"
        )

    private suspend fun post(
        tenantSlug: String,
        path: String,
        payload: JsonValue.Obj,
        action: String
    ): Result<SampleStorageResult> = runCatching {
        val response = httpClient.post(resolveUrl(path)) {
            tenantRequest(tenantSlug, tokenProvider)
            contentType(ContentType.Application.Json)
            setBody(payload.encode())
        }
        val parsed = JsonParser.parse(response.requireBody(action)) as? JsonValue.Obj
            ?: error("Respons penyimpanan tidak valid")
        SampleStorageResult(
            order = SamplingOrderCodec.decode(parsed.obj("order") ?: error("Respons tanpa SPK")),
            storage = SampleStorageCodec.decodeStatus(parsed.obj("storage") ?: jsonObjectOf())
        )
    }

    /** Pesan gerbang domain (422) diteruskan apa adanya — itulah yang perlu dibaca admin. */
    private suspend fun HttpResponse.requireBody(action: String): String {
        val body = bodyAsText()
        if (status == HttpStatusCode.UnprocessableEntity) error(body)
        if (!status.isSuccess()) error("Gagal $action (HTTP ${status.value}): $body")
        return body
    }

    private companion object {
        const val ORDERS_PATH = "/api/tenant/sampling/orders"
    }
}

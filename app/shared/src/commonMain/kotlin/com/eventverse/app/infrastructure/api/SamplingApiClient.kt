package com.eventverse.app.infrastructure.api

import com.eventverse.app.domain.sampling.*
import com.eventverse.app.shared.json.*
import com.eventverse.app.shared.sampling.SamplingOrderCodec
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*

interface SamplingRemoteDataSource {
    suspend fun getOrders(tenantSlug: String, status: SamplingStatus? = null): Result<List<SamplingOrder>>
    suspend fun getOrderDetail(tenantSlug: String, orderId: String): Result<SamplingOrder>
    suspend fun createOrder(
        tenantSlug: String,
        clientName: String,
        styleName: String,
        sizeMode: SizeMode = SizeMode.ALL_SIZE,
        useFactoryPreset: Boolean = true
    ): Result<SamplingOrder>
    suspend fun updateTechnicalSpec(tenantSlug: String, order: SamplingOrder): Result<SamplingOrder>
    suspend fun toggleMilestone(
        tenantSlug: String,
        orderId: String,
        step: MilestoneStep,
        isCompleted: Boolean
    ): Result<SamplingOrder>
    suspend fun approveOrder(
        tenantSlug: String,
        orderId: String,
        isApproved: Boolean,
        accNotes: String
    ): Result<SamplingOrder>
}

class SamplingApiClient(
    private val httpClient: HttpClient = HttpClient(),
    private val baseUrl: String = "",
    private val tokenProvider: SessionTokenProvider = StoredSessionTokenProvider
) : SamplingRemoteDataSource {

    private fun resolveUrl(path: String): String =
        if (baseUrl.isNotBlank()) "${baseUrl.trimEnd('/')}$path" else path

    override suspend fun getOrders(tenantSlug: String, status: SamplingStatus?): Result<List<SamplingOrder>> = runCatching {
        val path = if (status != null) "$ORDERS_PATH?status=${status.name}" else ORDERS_PATH
        val response = httpClient.get(resolveUrl(path)) {
            tenantRequest(tenantSlug, tokenProvider)
            accept(ContentType.Application.Json)
        }
        val body = response.requireBody("memuat daftar SPK sample")
        val parsed = JsonParser.parse(body) as? JsonValue.Arr ?: return@runCatching emptyList()
        parsed.items.filterIsInstance<JsonValue.Obj>().map { SamplingOrderCodec.decode(it) }
    }

    override suspend fun getOrderDetail(tenantSlug: String, orderId: String): Result<SamplingOrder> = runCatching {
        val response = httpClient.get(resolveUrl("$ORDERS_PATH/$orderId")) {
            tenantRequest(tenantSlug, tokenProvider)
            accept(ContentType.Application.Json)
        }
        val body = response.requireBody("memuat detail SPK sample")
        val parsed = JsonParser.parse(body) as? JsonValue.Obj ?: error("Respons SPK tidak valid")
        SamplingOrderCodec.decode(parsed)
    }

    override suspend fun createOrder(
        tenantSlug: String,
        clientName: String,
        styleName: String,
        sizeMode: SizeMode,
        useFactoryPreset: Boolean
    ): Result<SamplingOrder> = runCatching {
        val payload = jsonObjectOf(
            "clientName" to jsonOf(clientName),
            "styleName" to jsonOf(styleName),
            "sizeMode" to jsonOf(sizeMode.name),
            "useFactoryAllSizePreset" to jsonOf(useFactoryPreset)
        ).encode()

        val response = httpClient.post(resolveUrl(ORDERS_PATH)) {
            tenantRequest(tenantSlug, tokenProvider)
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
        val body = response.requireBody("membuat SPK sample")
        val parsed = JsonParser.parse(body) as? JsonValue.Obj ?: error("Respons SPK tidak valid")
        SamplingOrderCodec.decode(parsed)
    }

    override suspend fun updateTechnicalSpec(tenantSlug: String, order: SamplingOrder): Result<SamplingOrder> = runCatching {
        val payload = SamplingOrderCodec.encode(order).encode()
        val response = httpClient.put(resolveUrl("$ORDERS_PATH/${order.id.value}/technical-spec")) {
            tenantRequest(tenantSlug, tokenProvider)
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
        val body = response.requireBody("menyimpan spesifikasi teknis SPK")
        val parsed = JsonParser.parse(body) as? JsonValue.Obj ?: error("Respons SPK tidak valid")
        SamplingOrderCodec.decode(parsed)
    }

    override suspend fun toggleMilestone(
        tenantSlug: String,
        orderId: String,
        step: MilestoneStep,
        isCompleted: Boolean
    ): Result<SamplingOrder> = runCatching {
        val payload = jsonObjectOf(
            "isCompleted" to jsonOf(isCompleted)
        ).encode()

        val response = httpClient.patch(resolveUrl("$ORDERS_PATH/$orderId/milestones/${step.name}")) {
            tenantRequest(tenantSlug, tokenProvider)
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
        val body = response.requireBody("mengubah progres milestone")
        val parsed = JsonParser.parse(body) as? JsonValue.Obj ?: error("Respons milestone tidak valid")
        SamplingOrderCodec.decode(parsed)
    }

    override suspend fun approveOrder(
        tenantSlug: String,
        orderId: String,
        isApproved: Boolean,
        accNotes: String
    ): Result<SamplingOrder> = runCatching {
        val payload = jsonObjectOf(
            "isApproved" to jsonOf(isApproved),
            "accNotes" to jsonOf(accNotes)
        ).encode()

        val response = httpClient.post(resolveUrl("$ORDERS_PATH/$orderId/approve")) {
            tenantRequest(tenantSlug, tokenProvider)
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
        val body = response.requireBody("memproses persetujuan ACC produksi")
        val parsed = JsonParser.parse(body) as? JsonValue.Obj ?: error("Respons ACC tidak valid")
        SamplingOrderCodec.decode(parsed)
    }

    private suspend fun HttpResponse.requireBody(action: String): String {
        val body = bodyAsText()
        if (!status.isSuccess()) {
            error("Gagal $action (HTTP ${status.value}): $body")
        }
        return body
    }

    private companion object {
        const val ORDERS_PATH = "/api/tenant/sampling/orders"
    }
}

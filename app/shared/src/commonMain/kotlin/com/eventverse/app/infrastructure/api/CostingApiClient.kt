package com.eventverse.app.infrastructure.api

import com.eventverse.app.domain.costing.*
import com.eventverse.app.domain.pipeline.CostingBehavior
import com.eventverse.app.shared.contracts.CostingCalculationResultCodec
import com.eventverse.app.shared.costing.CostingRateCardCodec
import com.eventverse.app.shared.costing.CostingSheetCodec
import com.eventverse.app.shared.json.*
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.datetime.Instant

interface CostingRemoteDataSource {
    suspend fun getSheets(tenantSlug: String, techPackId: String? = null, status: CostingSheetStatus? = null): Result<List<CostingSheet>>
    suspend fun getSheet(tenantSlug: String, id: String): Result<CostingSheet>
    suspend fun createDraft(tenantSlug: String, techPackId: String, orderQuantity: Long, behavior: CostingBehavior, overrides: Map<String, String> = emptyMap()): Result<CostingSheet>
    suspend fun calculate(tenantSlug: String, id: String, nodeParams: Map<String, String> = emptyMap()): Result<CostingSheet>
    suspend fun reprice(tenantSlug: String, id: String, pricingAsOf: Instant? = null): Result<CostingSheet>
    suspend fun overrideParameters(tenantSlug: String, id: String, overrides: Map<String, String>): Result<CostingSheet>
    suspend fun submitForApproval(tenantSlug: String, id: String): Result<CostingSheet>
    suspend fun approve(tenantSlug: String, id: String): Result<CostingSheet>
    suspend fun reject(tenantSlug: String, id: String, reason: String): Result<CostingSheet>
    suspend fun revise(tenantSlug: String, id: String): Result<CostingSheet>
    suspend fun getActiveRateCard(tenantSlug: String, behavior: CostingBehavior): Result<CostingRateCard>
    suspend fun updateRateCard(tenantSlug: String, card: CostingRateCard): Result<CostingRateCard>
    suspend fun getTelemetry(tenantSlug: String): Result<CostingNodeTelemetry>
}

class CostingApiClient(
    private val httpClient: HttpClient = HttpClient(),
    private val baseUrl: String = "",
    private val tokenProvider: SessionTokenProvider = StoredSessionTokenProvider
) : CostingRemoteDataSource {

    private fun resolveUrl(path: String): String =
        if (baseUrl.isNotBlank()) "${baseUrl.trimEnd('/')}$path" else path

    companion object {
        private const val BASE_PATH = "/api/tenant/costing"
    }

    override suspend fun getSheets(
        tenantSlug: String,
        techPackId: String?,
        status: CostingSheetStatus?
    ): Result<List<CostingSheet>> = runCatching {
        val params = buildList {
            if (!techPackId.isNullOrBlank()) add("techPackId=${techPackId.encodeURLParameter()}")
            if (status != null) add("status=${status.name}")
        }.joinToString("&")

        val path = if (params.isNotEmpty()) "$BASE_PATH/sheets?$params" else "$BASE_PATH/sheets"
        val response = httpClient.get(resolveUrl(path)) {
            tenantRequest(tenantSlug, tokenProvider)
            accept(ContentType.Application.Json)
        }
        val body = response.requireBody("memuat daftar lembar HPP")
        val parsed = JsonParser.parse(body) as? JsonValue.Arr ?: error("Respons daftar HPP tidak valid")
        parsed.items.filterIsInstance<JsonValue.Obj>().map(CostingSheetCodec::decode)
    }

    override suspend fun getSheet(tenantSlug: String, id: String): Result<CostingSheet> = runCatching {
        val response = httpClient.get(resolveUrl("$BASE_PATH/sheets/$id")) {
            tenantRequest(tenantSlug, tokenProvider)
            accept(ContentType.Application.Json)
        }
        val body = response.requireBody("memuat detail lembar HPP")
        val parsed = JsonParser.parse(body) as? JsonValue.Obj ?: error("Respons detail HPP tidak valid")
        CostingSheetCodec.decode(parsed)
    }

    override suspend fun createDraft(
        tenantSlug: String,
        techPackId: String,
        orderQuantity: Long,
        behavior: CostingBehavior,
        overrides: Map<String, String>
    ): Result<CostingSheet> = runCatching {
        val payload = jsonObjectOf(
            "techPackId" to jsonOf(techPackId),
            "orderQuantity" to jsonOf(orderQuantity),
            "behavior" to jsonOf(behavior.code),
            "parameterOverrides" to jsonStringMapOf(overrides)
        )
        val response = httpClient.post(resolveUrl("$BASE_PATH/sheets")) {
            tenantRequest(tenantSlug, tokenProvider)
            contentType(ContentType.Application.Json)
            setBody(payload.encode())
        }
        val body = response.requireBody("membuat draft HPP")
        val parsed = JsonParser.parse(body) as? JsonValue.Obj ?: error("Respons buat draft HPP tidak valid")
        CostingSheetCodec.decode(parsed)
    }

    override suspend fun calculate(
        tenantSlug: String,
        id: String,
        nodeParams: Map<String, String>
    ): Result<CostingSheet> = runCatching {
        val payload = jsonObjectOf("nodeParams" to jsonStringMapOf(nodeParams))
        val response = httpClient.post(resolveUrl("$BASE_PATH/sheets/$id/calculate")) {
            tenantRequest(tenantSlug, tokenProvider)
            contentType(ContentType.Application.Json)
            setBody(payload.encode())
        }
        val body = response.requireBody("menghitung lembar HPP")
        val parsed = JsonParser.parse(body) as? JsonValue.Obj ?: error("Respons hitung HPP tidak valid")
        CostingSheetCodec.decode(parsed)
    }

    override suspend fun reprice(
        tenantSlug: String,
        id: String,
        pricingAsOf: Instant?
    ): Result<CostingSheet> = runCatching {
        val payload = jsonObjectOf(
            "pricingAsOf" to (pricingAsOf?.let { jsonOf(it.toString()) } ?: JsonValue.Null)
        )
        val response = httpClient.post(resolveUrl("$BASE_PATH/sheets/$id/reprice")) {
            tenantRequest(tenantSlug, tokenProvider)
            contentType(ContentType.Application.Json)
            setBody(payload.encode())
        }
        val body = response.requireBody("reprice lembar HPP")
        val parsed = JsonParser.parse(body) as? JsonValue.Obj ?: error("Respons reprice HPP tidak valid")
        CostingSheetCodec.decode(parsed)
    }

    override suspend fun overrideParameters(
        tenantSlug: String,
        id: String,
        overrides: Map<String, String>
    ): Result<CostingSheet> = runCatching {
        val payload = jsonObjectOf("parameterOverrides" to jsonStringMapOf(overrides))
        val response = httpClient.post(resolveUrl("$BASE_PATH/sheets/$id/override-parameters")) {
            tenantRequest(tenantSlug, tokenProvider)
            contentType(ContentType.Application.Json)
            setBody(payload.encode())
        }
        val body = response.requireBody("mengubah parameter HPP")
        val parsed = JsonParser.parse(body) as? JsonValue.Obj ?: error("Respons ubah parameter tidak valid")
        CostingSheetCodec.decode(parsed)
    }

    override suspend fun submitForApproval(tenantSlug: String, id: String): Result<CostingSheet> = runCatching {
        val response = httpClient.post(resolveUrl("$BASE_PATH/sheets/$id/submit")) {
            tenantRequest(tenantSlug, tokenProvider)
        }
        val body = response.requireBody("mengajukan persetujuan HPP")
        val parsed = JsonParser.parse(body) as? JsonValue.Obj ?: error("Respons pengajuan persetujuan tidak valid")
        CostingSheetCodec.decode(parsed)
    }

    override suspend fun approve(tenantSlug: String, id: String): Result<CostingSheet> = runCatching {
        val response = httpClient.post(resolveUrl("$BASE_PATH/sheets/$id/approve")) {
            tenantRequest(tenantSlug, tokenProvider)
        }
        val body = response.requireBody("menyetujui lembar HPP")
        val parsed = JsonParser.parse(body) as? JsonValue.Obj ?: error("Respons persetujuan HPP tidak valid")
        CostingSheetCodec.decode(parsed)
    }

    override suspend fun reject(tenantSlug: String, id: String, reason: String): Result<CostingSheet> = runCatching {
        val payload = jsonObjectOf("reason" to jsonOf(reason))
        val response = httpClient.post(resolveUrl("$BASE_PATH/sheets/$id/reject")) {
            tenantRequest(tenantSlug, tokenProvider)
            contentType(ContentType.Application.Json)
            setBody(payload.encode())
        }
        val body = response.requireBody("menolak lembar HPP")
        val parsed = JsonParser.parse(body) as? JsonValue.Obj ?: error("Respons penolakan HPP tidak valid")
        CostingSheetCodec.decode(parsed)
    }

    override suspend fun revise(tenantSlug: String, id: String): Result<CostingSheet> = runCatching {
        val response = httpClient.post(resolveUrl("$BASE_PATH/sheets/$id/revise")) {
            tenantRequest(tenantSlug, tokenProvider)
        }
        val body = response.requireBody("membuat revisi lembar HPP")
        val parsed = JsonParser.parse(body) as? JsonValue.Obj ?: error("Respons revisi HPP tidak valid")
        CostingSheetCodec.decode(parsed)
    }

    override suspend fun getActiveRateCard(tenantSlug: String, behavior: CostingBehavior): Result<CostingRateCard> = runCatching {
        val response = httpClient.get(resolveUrl("$BASE_PATH/rate-card?behavior=${behavior.code}")) {
            tenantRequest(tenantSlug, tokenProvider)
            accept(ContentType.Application.Json)
        }
        val body = response.requireBody("memuat rate card aktif")
        val parsed = JsonParser.parse(body) as? JsonValue.Obj ?: error("Respons rate card tidak valid")
        CostingRateCardCodec.decode(parsed)
    }

    override suspend fun updateRateCard(tenantSlug: String, card: CostingRateCard): Result<CostingRateCard> = runCatching {
        val payload = CostingRateCardCodec.encode(card)
        val response = httpClient.put(resolveUrl("$BASE_PATH/rate-card")) {
            tenantRequest(tenantSlug, tokenProvider)
            contentType(ContentType.Application.Json)
            setBody(payload.encode())
        }
        val body = response.requireBody("menyimpan rate card")
        val parsed = JsonParser.parse(body) as? JsonValue.Obj ?: error("Respons simpan rate card tidak valid")
        CostingRateCardCodec.decode(parsed)
    }

    override suspend fun getTelemetry(tenantSlug: String): Result<CostingNodeTelemetry> = runCatching {
        val response = httpClient.get(resolveUrl("$BASE_PATH/telemetry")) {
            tenantRequest(tenantSlug, tokenProvider)
            accept(ContentType.Application.Json)
        }
        val body = response.requireBody("memuat telemetri HPP")
        val json = JsonParser.parse(body) as? JsonValue.Obj ?: error("Respons telemetri tidak valid")

        val healthStatus = json.string("healthStatus")?.let {
            com.eventverse.app.domain.pipeline.FlowHealthStatus.valueOf(it)
        } ?: com.eventverse.app.domain.pipeline.FlowHealthStatus.HEALTHY

        CostingNodeTelemetry(
            pendingSheetCount = json.int("pendingSheetCount") ?: 0,
            overdueApprovalCount = json.int("overdueApprovalCount") ?: 0,
            avgApprovalCycleHours = json.double("avgApprovalCycleHours") ?: 0.0,
            healthStatus = healthStatus
        )
    }

    private suspend fun HttpResponse.requireBody(action: String): String {
        val body = bodyAsText()
        if (!status.isSuccess()) {
            error("Gagal $action (HTTP ${status.value}): $body")
        }
        return body
    }
}

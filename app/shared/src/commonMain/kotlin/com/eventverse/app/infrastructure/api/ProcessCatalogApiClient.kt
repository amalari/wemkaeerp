package com.eventverse.app.infrastructure.api

import com.eventverse.app.domain.process.TenantOptionalProcess
import com.eventverse.app.domain.sampling.SamplingPipelineStage
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.domain.workqueue.WorkExecutionMode
import com.eventverse.app.domain.workqueue.WorkStationCode
import com.eventverse.app.shared.json.JsonParser
import com.eventverse.app.shared.json.JsonValue
import com.eventverse.app.shared.json.jsonObjectOf
import com.eventverse.app.shared.json.jsonOf
import com.eventverse.app.domain.transfer.FlowLegBoard
import com.eventverse.app.shared.process.ProcessCatalogCodec
import com.eventverse.app.shared.transfer.FlowLegCodec
import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess

/** DTO hasil alur proses efektif untuk SPK/desain. */
data class OrderFlowDto(
    val orderId: String,
    val isCustomFlow: Boolean,
    val processes: List<TenantOptionalProcess>
)

/** Sumber data remote katalog proses opsional tenant (endpoint /api/tenant/process-catalog). */
interface ProcessCatalogRemoteDataSource {
    suspend fun fetchCatalog(): Result<List<TenantOptionalProcess>>
    suspend fun addProcess(
        code: String,
        displayName: String,
        anchorAfter: SamplingPipelineStage,
        executionMode: WorkExecutionMode = WorkExecutionMode.IN_HOUSE,
        vendorRef: String? = null,
        piecerateTariffIdr: Long = 0L,
        standardMinutesPerPiece: Double = 0.0
    ): Result<TenantOptionalProcess>
    suspend fun repositionProcess(processId: String, newAnchorAfter: SamplingPipelineStage): Result<Unit>
    suspend fun removeProcess(processId: String): Result<Unit>

    /** Alur proses spesifik per desain/SPK */
    suspend fun fetchOrderFlow(orderId: String): Result<OrderFlowDto>
    suspend fun saveOrderFlow(orderId: String, processes: List<TenantOptionalProcess>): Result<OrderFlowDto>
    suspend fun resetOrderFlow(orderId: String): Result<OrderFlowDto>

    /**
     * Perpindahan barang yang tersirat di alur SPK ini, berikut status dokumennya.
     *
     * Diturunkan di server dari konfigurasi lokasi tenant — klien tidak menghitungnya sendiri,
     * supaya konektor yang tampil dan gerbang yang menolak selalu berasal dari sumber yang sama.
     */
    suspend fun fetchFlowLegs(orderId: String): Result<FlowLegBoard>
}

class ProcessCatalogApiClient(
    private val httpClient: HttpClient = HttpClient(),
    private val baseUrl: String = "",
    private val tokenProvider: SessionTokenProvider = StoredSessionTokenProvider
) : ProcessCatalogRemoteDataSource {

    private val tenantSlug: String
        get() = StoredTenantSlugProvider.currentTenantSlug() ?: "demo-tenant"

    override suspend fun fetchCatalog(): Result<List<TenantOptionalProcess>> = runCatching {
        val response = httpClient.get("$baseUrl/api/tenant/process-catalog") {
            tenantRequest(tenantSlug, tokenProvider)
        }
        val obj = decodeBody(response.bodyAsText(), response.status.isSuccess())
        obj.objectArray("processes").map { ProcessCatalogCodec.decodeProcess(it, FALLBACK_TENANT)!! }
    }

    override suspend fun addProcess(
        code: String,
        displayName: String,
        anchorAfter: SamplingPipelineStage,
        executionMode: WorkExecutionMode,
        vendorRef: String?,
        piecerateTariffIdr: Long,
        standardMinutesPerPiece: Double
    ): Result<TenantOptionalProcess> = runCatching {
        val body = jsonObjectOf(
            "code" to jsonOf(code),
            "displayName" to jsonOf(displayName),
            "samplingAnchorAfter" to jsonOf(anchorAfter.name),
            "executionMode" to jsonOf(executionMode.name),
            "vendorRef" to jsonOf(vendorRef),
            "piecerateTariffIdr" to jsonOf(piecerateTariffIdr),
            "standardMinutesPerPiece" to jsonOf(standardMinutesPerPiece)
        )
        val response = httpClient.post("$baseUrl/api/tenant/process-catalog") {
            tenantRequest(tenantSlug, tokenProvider)
            contentType(ContentType.Application.Json)
            setBody(body.encode())
        }
        val obj = decodeBody(response.bodyAsText(), response.status.isSuccess())
        ProcessCatalogCodec.decodeProcess(obj, FALLBACK_TENANT) ?: error("Invalid process payload")
    }

    override suspend fun repositionProcess(
        processId: String,
        newAnchorAfter: SamplingPipelineStage
    ): Result<Unit> = runCatching {
        val body = jsonObjectOf("samplingAnchorAfter" to jsonOf(newAnchorAfter.name))
        val response = httpClient.patch("$baseUrl/api/tenant/process-catalog/$processId") {
            tenantRequest(tenantSlug, tokenProvider)
            contentType(ContentType.Application.Json)
            setBody(body.encode())
        }
        decodeBody(response.bodyAsText(), response.status.isSuccess())
        Unit
    }

    override suspend fun removeProcess(processId: String): Result<Unit> = runCatching {
        val response = httpClient.delete("$baseUrl/api/tenant/process-catalog/$processId") {
            tenantRequest(tenantSlug, tokenProvider)
        }
        decodeBody(response.bodyAsText(), response.status.isSuccess())
        Unit
    }

    override suspend fun fetchOrderFlow(orderId: String): Result<OrderFlowDto> = runCatching {
        val response = httpClient.get("$baseUrl/api/tenant/sampling/orders/$orderId/flow") {
            tenantRequest(tenantSlug, tokenProvider)
        }
        val obj = decodeBody(response.bodyAsText(), response.status.isSuccess())
        val isCustom = obj.boolean("isCustomFlow") ?: false
        val processes = obj.objectArray("processes").mapNotNull { ProcessCatalogCodec.decodeProcess(it, FALLBACK_TENANT) }
        OrderFlowDto(orderId, isCustom, processes)
    }

    override suspend fun fetchFlowLegs(orderId: String): Result<FlowLegBoard> = runCatching {
        val response = httpClient.get("$baseUrl/api/tenant/sampling/orders/$orderId/flow-legs") {
            tenantRequest(tenantSlug, tokenProvider)
        }
        val obj = decodeBody(response.bodyAsText(), response.status.isSuccess())
        FlowLegCodec.decodeBoard(obj)
    }

    override suspend fun saveOrderFlow(
        orderId: String,
        processes: List<TenantOptionalProcess>
    ): Result<OrderFlowDto> = runCatching {
        val body = jsonObjectOf(
            "processes" to ProcessCatalogCodec.encodeProcesses(processes)
        )
        val response = httpClient.put("$baseUrl/api/tenant/sampling/orders/$orderId/flow") {
            tenantRequest(tenantSlug, tokenProvider)
            contentType(ContentType.Application.Json)
            setBody(body.encode())
        }
        val obj = decodeBody(response.bodyAsText(), response.status.isSuccess())
        val isCustom = obj.boolean("isCustomFlow") ?: true
        val returned = obj.objectArray("processes").mapNotNull { ProcessCatalogCodec.decodeProcess(it, FALLBACK_TENANT) }
        OrderFlowDto(orderId, isCustom, returned)
    }

    override suspend fun resetOrderFlow(orderId: String): Result<OrderFlowDto> = runCatching {
        val response = httpClient.delete("$baseUrl/api/tenant/sampling/orders/$orderId/flow") {
            tenantRequest(tenantSlug, tokenProvider)
        }
        val obj = decodeBody(response.bodyAsText(), response.status.isSuccess())
        val isCustom = obj.boolean("isCustomFlow") ?: false
        val processes = obj.objectArray("processes").mapNotNull { ProcessCatalogCodec.decodeProcess(it, FALLBACK_TENANT) }
        OrderFlowDto(orderId, isCustom, processes)
    }

    private fun decodeBody(text: String, isSuccess: Boolean): JsonValue.Obj {
        val obj = JsonParser.parse(text) as? JsonValue.Obj ?: error("Invalid JSON response: $text")
        if (!isSuccess) error("HTTP error: ${obj.string("error") ?: text}")
        return obj
    }

    private companion object {
        val FALLBACK_TENANT = TenantId("server")
    }
}
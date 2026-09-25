package com.eventverse.app.infrastructure.api

import com.eventverse.app.domain.pipeline.StockOwnershipSemantics
import com.eventverse.app.domain.production.BulkProductionStatus
import com.eventverse.app.domain.production.BulkWorkOrder
import com.eventverse.app.domain.production.MachineLineAllocation
import com.eventverse.app.domain.production.ProductionLineName
import com.eventverse.app.domain.production.ProductionStage
import com.eventverse.app.shared.json.*
import com.eventverse.app.shared.production.BulkWorkOrderCodec
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*

interface ProductionRemoteDataSource {
    suspend fun getWorkOrders(
        tenantSlug: String,
        status: BulkProductionStatus? = null
    ): Result<List<BulkWorkOrder>>

    suspend fun getWorkOrderDetail(tenantSlug: String, workOrderId: String): Result<BulkWorkOrder>

    suspend fun launchFromDeal(
        tenantSlug: String,
        dealId: String,
        stockOwnership: StockOwnershipSemantics = StockOwnershipSemantics.OWNED_RAW_MATERIAL,
        notes: String = ""
    ): Result<List<BulkWorkOrder>>

    suspend fun allocateLine(
        tenantSlug: String,
        workOrderId: String,
        allocation: MachineLineAllocation
    ): Result<BulkWorkOrder>

    suspend fun removeLine(
        tenantSlug: String,
        workOrderId: String,
        lineName: ProductionLineName
    ): Result<BulkWorkOrder>

    suspend fun recordProgress(
        tenantSlug: String,
        workOrderId: String,
        stage: ProductionStage,
        completedPcs: Int,
        reworkPcs: Int = 0,
        rejectPcs: Int = 0
    ): Result<BulkWorkOrder>
}

class ProductionApiClient(
    private val httpClient: HttpClient = HttpClient(),
    private val baseUrl: String = "",
    private val tokenProvider: SessionTokenProvider = StoredSessionTokenProvider
) : ProductionRemoteDataSource {

    private fun resolveUrl(path: String): String =
        if (baseUrl.isNotBlank()) "${baseUrl.trimEnd('/')}$path" else path

    override suspend fun getWorkOrders(
        tenantSlug: String,
        status: BulkProductionStatus?
    ): Result<List<BulkWorkOrder>> = runCatching {
        val path = if (status != null) "$WORK_ORDERS_PATH?status=${status.name}" else WORK_ORDERS_PATH
        val response = httpClient.get(resolveUrl(path)) {
            tenantRequest(tenantSlug, tokenProvider)
            accept(ContentType.Application.Json)
        }
        val body = response.requireBody("memuat daftar SPK massal")
        val parsed = JsonParser.parse(body) as? JsonValue.Arr ?: return@runCatching emptyList()
        parsed.items.filterIsInstance<JsonValue.Obj>().map { BulkWorkOrderCodec.decode(it) }
    }

    override suspend fun getWorkOrderDetail(tenantSlug: String, workOrderId: String): Result<BulkWorkOrder> =
        runCatching {
            val response = httpClient.get(resolveUrl("$WORK_ORDERS_PATH/$workOrderId")) {
                tenantRequest(tenantSlug, tokenProvider)
                accept(ContentType.Application.Json)
            }
            decodeOrder(response.requireBody("memuat detail SPK massal"))
        }

    override suspend fun launchFromDeal(
        tenantSlug: String,
        dealId: String,
        stockOwnership: StockOwnershipSemantics,
        notes: String
    ): Result<List<BulkWorkOrder>> = runCatching {
        val payload = jsonObjectOf(
            "dealId" to jsonOf(dealId),
            "stockOwnership" to jsonOf(stockOwnership.name),
            "notes" to jsonOf(notes)
        ).encode()

        val response = httpClient.post(resolveUrl("$WORK_ORDERS_PATH/launch-from-deal")) {
            tenantRequest(tenantSlug, tokenProvider)
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
        decodeOrders(response.requireBody("menerbitkan SPK massal"))
    }

    override suspend fun allocateLine(
        tenantSlug: String,
        workOrderId: String,
        allocation: MachineLineAllocation
    ): Result<BulkWorkOrder> = runCatching {
        val payload = jsonObjectOf(
            "lineName" to jsonOf(allocation.lineName.value),
            "machineCount" to jsonOf(allocation.machineCount),
            "assignedPcs" to jsonOf(allocation.assignedPcs),
            "startDate" to jsonOf(allocation.startDate?.toString()),
            "targetFinishDate" to jsonOf(allocation.targetFinishDate?.toString()),
            "operatorCount" to jsonOf(allocation.operatorCount),
            "notes" to jsonOf(allocation.notes)
        ).encode()

        val response = httpClient.post(resolveUrl("$WORK_ORDERS_PATH/$workOrderId/lines")) {
            tenantRequest(tenantSlug, tokenProvider)
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
        decodeOrder(response.requireBody("mengalokasikan lini mesin"))
    }

    override suspend fun removeLine(
        tenantSlug: String,
        workOrderId: String,
        lineName: ProductionLineName
    ): Result<BulkWorkOrder> = runCatching {
        val response = httpClient.delete(
            resolveUrl("$WORK_ORDERS_PATH/$workOrderId/lines/${lineName.value.encodeURLPathPart()}")
        ) {
            tenantRequest(tenantSlug, tokenProvider)
            accept(ContentType.Application.Json)
        }
        decodeOrder(response.requireBody("menghapus alokasi lini"))
    }

    override suspend fun recordProgress(
        tenantSlug: String,
        workOrderId: String,
        stage: ProductionStage,
        completedPcs: Int,
        reworkPcs: Int,
        rejectPcs: Int
    ): Result<BulkWorkOrder> = runCatching {
        val payload = jsonObjectOf(
            "stage" to jsonOf(stage.name),
            "completedPcs" to jsonOf(completedPcs),
            "reworkPcs" to jsonOf(reworkPcs),
            "rejectPcs" to jsonOf(rejectPcs)
        ).encode()

        val response = httpClient.post(resolveUrl("$WORK_ORDERS_PATH/$workOrderId/progress")) {
            tenantRequest(tenantSlug, tokenProvider)
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
        decodeOrder(response.requireBody("mencatat hasil produksi"))
    }

    private fun decodeOrder(body: String): BulkWorkOrder {
        val parsed = JsonParser.parse(body) as? JsonValue.Obj ?: error("Respons SPK massal tidak valid")
        return BulkWorkOrderCodec.decode(parsed)
    }

    /**
     * Respons launch berupa array SPK (1 per ukuran); objek tunggal tetap diterima agar
     * aman saat server lama/baru bercampur di periode deployment.
     */
    private fun decodeOrders(body: String): List<BulkWorkOrder> =
        when (val parsed = JsonParser.parse(body)) {
            is JsonValue.Arr -> parsed.items.filterIsInstance<JsonValue.Obj>().map { BulkWorkOrderCodec.decode(it) }
            is JsonValue.Obj -> listOf(BulkWorkOrderCodec.decode(parsed))
            else -> error("Respons SPK massal tidak valid")
        }

    private suspend fun HttpResponse.requireBody(action: String): String {
        val body = bodyAsText()
        if (!status.isSuccess()) {
            error("Gagal $action (HTTP ${status.value}): $body")
        }
        return body
    }

    private companion object {
        const val WORK_ORDERS_PATH = "/api/tenant/production/work-orders"
    }
}

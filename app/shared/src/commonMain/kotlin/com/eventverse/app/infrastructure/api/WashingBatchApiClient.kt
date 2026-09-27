package com.eventverse.app.infrastructure.api

import com.eventverse.app.domain.workqueue.WashingBatch
import com.eventverse.app.domain.workqueue.WashingBatchId
import com.eventverse.app.domain.workqueue.WashingSortOutput
import com.eventverse.app.domain.workqueue.WorkCard
import com.eventverse.app.domain.workqueue.WorkCardId
import com.eventverse.app.shared.json.JsonParser
import com.eventverse.app.shared.json.JsonValue
import com.eventverse.app.shared.json.jsonArrayOf
import com.eventverse.app.shared.json.jsonObjectOf
import com.eventverse.app.shared.json.jsonOf
import com.eventverse.app.shared.workqueue.WashingBatchCodec
import com.eventverse.app.shared.workqueue.WorkQueueCodec
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess

data class BundlePhotoUploadItem(
    val cardId: WorkCardId,
    val bundlePhotoKey: String
)

interface WashingBatchRemoteDataSource {
    suspend fun fetchPendingWashingBundles(): Result<List<WorkCard>>
    suspend fun fetchBatches(): Result<List<WashingBatch>>
    suspend fun createBatch(
        batchCode: String,
        machineDrumNo: String,
        washRecipe: String,
        operatorName: String,
        bundles: List<BundlePhotoUploadItem>,
        notes: String = ""
    ): Result<WashingBatch>
    suspend fun completeSorting(
        batchId: WashingBatchId,
        sortOutputs: List<WashingSortOutput>
    ): Result<List<WorkCard>>
}

class WashingBatchApiClient(
    private val httpClient: HttpClient = HttpClient(),
    private val baseUrl: String = "",
    private val tokenProvider: SessionTokenProvider = StoredSessionTokenProvider
) : WashingBatchRemoteDataSource {

    private val tenantSlug: String
        get() = StoredTenantSlugProvider.currentTenantSlug() ?: "demo-tenant"

    override suspend fun fetchPendingWashingBundles(): Result<List<WorkCard>> = runCatching {
        val response = httpClient.get("$baseUrl/api/tenant/work-queue/washing/pending-bundles") {
            tenantRequest(tenantSlug, tokenProvider)
        }
        if (!response.status.isSuccess()) {
            error("Gagal mengambil bundle antrean cuci: ${response.status}")
        }
        val text = response.bodyAsText()
        val obj = JsonParser.parse(text) as? JsonValue.Obj ?: error("Respons tidak valid")
        obj.objectArray("cards").map(WorkQueueCodec::decodeCard)
    }

    override suspend fun fetchBatches(): Result<List<WashingBatch>> = runCatching {
        val response = httpClient.get("$baseUrl/api/tenant/work-queue/washing/batches") {
            tenantRequest(tenantSlug, tokenProvider)
        }
        if (!response.status.isSuccess()) {
            error("Gagal mengambil daftar batch cuci: ${response.status}")
        }
        val text = response.bodyAsText()
        val obj = JsonParser.parse(text) as? JsonValue.Obj ?: error("Respons tidak valid")
        obj.objectArray("batches").map(WashingBatchCodec::decodeBatch)
    }

    override suspend fun createBatch(
        batchCode: String,
        machineDrumNo: String,
        washRecipe: String,
        operatorName: String,
        bundles: List<BundlePhotoUploadItem>,
        notes: String
    ): Result<WashingBatch> = runCatching {
        val bundleObjs = bundles.map {
            jsonObjectOf(
                "cardId" to jsonOf(it.cardId.value),
                "bundlePhotoKey" to jsonOf(it.bundlePhotoKey)
            )
        }
        val payload = jsonObjectOf(
            "batchCode" to jsonOf(batchCode),
            "machineDrumNo" to jsonOf(machineDrumNo),
            "washRecipe" to jsonOf(washRecipe),
            "operatorName" to jsonOf(operatorName),
            "bundles" to jsonArrayOf(bundleObjs),
            "notes" to jsonOf(notes)
        )
        val response = httpClient.post("$baseUrl/api/tenant/work-queue/washing/batches") {
            contentType(ContentType.Application.Json)
            tenantRequest(tenantSlug, tokenProvider)
            setBody(payload.encode())
        }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) {
            val err = (JsonParser.parse(text) as? JsonValue.Obj)?.string("error")
            error(err ?: "Gagal membuat sesi batch cuci: ${response.status}")
        }
        val obj = JsonParser.parse(text) as? JsonValue.Obj ?: error("Respons tidak valid")
        WashingBatchCodec.decodeBatch(obj)
    }

    override suspend fun completeSorting(
        batchId: WashingBatchId,
        sortOutputs: List<WashingSortOutput>
    ): Result<List<WorkCard>> = runCatching {
        val sortObjs = sortOutputs.map(WashingBatchCodec::encodeSortOutput)
        val payload = jsonObjectOf(
            "sortOutputs" to jsonArrayOf(sortObjs)
        )
        val response = httpClient.post("$baseUrl/api/tenant/work-queue/washing/batches/${batchId.value}/complete-sort") {
            contentType(ContentType.Application.Json)
            tenantRequest(tenantSlug, tokenProvider)
            setBody(payload.encode())
        }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) {
            val err = (JsonParser.parse(text) as? JsonValue.Obj)?.string("error")
            error(err ?: "Gagal menyelesaikan sortir cuci: ${response.status}")
        }
        val obj = JsonParser.parse(text) as? JsonValue.Obj ?: error("Respons tidak valid")
        obj.objectArray("cards").map(WorkQueueCodec::decodeCard)
    }
}

package com.eventverse.app.infrastructure.api

import com.eventverse.app.domain.stageflow.IndustryTemplateCode
import com.eventverse.app.domain.stageflow.StageCode
import com.eventverse.app.domain.stageflow.StageDefinition
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.shared.json.JsonParser
import com.eventverse.app.shared.json.JsonValue
import com.eventverse.app.shared.json.jsonObjectOf
import com.eventverse.app.shared.json.jsonOf
import com.eventverse.app.shared.stageflow.TenantStageFlowCodec
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess

/** Kerangka tahap pabrik yang sedang berlaku, beserta template asalnya. */
data class TenantStageFlowView(val template: IndustryTemplateCode, val stages: List<StageDefinition>)

/** Kerangka tahap pabrik (TRD-FLOW-001) — sumber kolom papan sampling & meja operator, dan editornya. */
interface StageFlowRemoteDataSource {
    suspend fun fetchTenantStages(): Result<List<StageDefinition>>

    // Sunting (3b/3c). Default gagal supaya pemalsu di test yang hanya membaca tidak perlu diubah.
    suspend fun fetchTenantFlow(): Result<TenantStageFlowView> = Result.failure(UnsupportedOperationException())
    suspend fun addStage(draft: NewStageDraft): Result<TenantStageFlowView> = Result.failure(UnsupportedOperationException())
    suspend fun removeStage(code: StageCode): Result<TenantStageFlowView> = Result.failure(UnsupportedOperationException())
    suspend fun moveStage(code: StageCode, after: StageCode): Result<TenantStageFlowView> = Result.failure(UnsupportedOperationException())
    suspend fun renameStage(code: StageCode, displayName: String, shortLabel: String): Result<TenantStageFlowView> =
        Result.failure(UnsupportedOperationException())
    suspend fun resetTo(template: IndustryTemplateCode): Result<TenantStageFlowView> = Result.failure(UnsupportedOperationException())
}

/** Isian form "Tambah Tahap". */
data class NewStageDraft(
    val code: StageCode,
    val displayName: String,
    val shortLabel: String,
    val after: StageCode,
    val isOperatorDesk: Boolean
)

class StageFlowApiClient(
    private val httpClient: HttpClient = HttpClient(),
    private val baseUrl: String = "",
    private val tokenProvider: SessionTokenProvider = StoredSessionTokenProvider
) : StageFlowRemoteDataSource {

    private val tenantSlug: String
        get() = StoredTenantSlugProvider.currentTenantSlug() ?: "demo-tenant"

    override suspend fun fetchTenantStages(): Result<List<StageDefinition>> = fetchTenantFlow().map { it.stages }

    override suspend fun fetchTenantFlow(): Result<TenantStageFlowView> = runCatching {
        decode(httpClient.get("$baseUrl$PATH") { tenantRequest(tenantSlug, tokenProvider) })
    }

    override suspend fun addStage(draft: NewStageDraft) = send {
        httpClient.post("$baseUrl$PATH/stages") {
            json(
                jsonObjectOf(
                    "code" to jsonOf(draft.code.value),
                    "displayName" to jsonOf(draft.displayName),
                    "shortLabel" to jsonOf(draft.shortLabel),
                    "afterCode" to jsonOf(draft.after.value),
                    "isOperatorDesk" to jsonOf(draft.isOperatorDesk)
                )
            )
        }
    }

    override suspend fun removeStage(code: StageCode) = send {
        httpClient.delete("$baseUrl$PATH/stages/${code.value}") { tenantRequest(tenantSlug, tokenProvider) }
    }

    override suspend fun moveStage(code: StageCode, after: StageCode) = send {
        httpClient.post("$baseUrl$PATH/stages/${code.value}/move") { json(jsonObjectOf("afterCode" to jsonOf(after.value))) }
    }

    override suspend fun renameStage(code: StageCode, displayName: String, shortLabel: String) = send {
        httpClient.patch("$baseUrl$PATH/stages/${code.value}") {
            json(jsonObjectOf("displayName" to jsonOf(displayName), "shortLabel" to jsonOf(shortLabel)))
        }
    }

    override suspend fun resetTo(template: IndustryTemplateCode) = send {
        httpClient.post("$baseUrl$PATH/reset") { json(jsonObjectOf("template" to jsonOf(template.name))) }
    }

    private suspend fun send(call: suspend () -> HttpResponse): Result<TenantStageFlowView> = runCatching { decode(call()) }

    private fun HttpRequestBuilder.json(body: JsonValue.Obj) {
        tenantRequest(tenantSlug, tokenProvider)
        contentType(ContentType.Application.Json)
        setBody(body.encode())
    }

    /** 409 membawa JSON `{error}`; 400/403 teks polos — keduanya dijadikan pesan yang bisa dibaca admin. */
    private suspend fun decode(response: HttpResponse): TenantStageFlowView {
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) {
            error(JsonParser.parseObjectOrNull(text)?.string("error") ?: text.ifBlank { "Gagal (${response.status.value})" })
        }
        // Tenant id di sini hanya pengisi: klien memakai daftar tahapnya, bukan agregatnya.
        val flow = TenantStageFlowCodec.decode(TenantId("client"), JsonParser.parseObject(text))
        return TenantStageFlowView(flow.template, flow.stages)
    }

    private companion object {
        const val PATH = "/api/tenant/stage-flow"
    }
}

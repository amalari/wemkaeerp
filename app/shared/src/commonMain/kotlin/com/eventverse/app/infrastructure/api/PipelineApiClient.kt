package com.eventverse.app.infrastructure.api

import com.eventverse.app.domain.pipeline.CustomTenantPipeline
import com.eventverse.app.domain.pipeline.GarmentBusinessPreset
import com.eventverse.app.domain.pipeline.TenantModuleCatalogSnapshot
import com.eventverse.app.shared.json.JsonWriter
import com.eventverse.app.shared.pipeline.PipelineGraphCodec
import com.eventverse.app.shared.pipeline.TenantModuleCatalogCodec
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*

/**
 * Client for the tenant pipeline API.
 *
 * The factory flow screen previously rendered `PipelinePresetFactory` output directly, so a
 * tenant's persisted topology — its module set, its names, its bypass flags — never reached
 * the UI. This is the missing link: the same [PipelineGraphCodec] the server writes with is
 * used to read the response, so there is one wire format rather than two.
 */
class PipelineApiClient(
    private val httpClient: HttpClient = HttpClient(),
    private val baseUrl: String = "",
    private val tokenProvider: SessionTokenProvider = StoredSessionTokenProvider
) : PipelineRemoteDataSource {
    private fun resolveUrl(path: String): String =
        if (baseUrl.isNotBlank()) "${baseUrl.trimEnd('/')}$path" else path

    private val stageFlow by lazy { StageFlowApiClient(httpClient, baseUrl, tokenProvider) }

    override suspend fun getStageFlow() = stageFlow.fetchTenantStages()

    /** GET /api/tenant/pipeline/telemetry */
    override suspend fun getTelemetry(tenantSlug: String) = runCatching {
        val response = httpClient.get(resolveUrl("$PIPELINE_PATH/telemetry")) {
            tenantRequest(tenantSlug, tokenProvider)
            accept(ContentType.Application.Json)
        }
        com.eventverse.app.shared.pipeline.ModuleTelemetryCodec.decode(
            com.eventverse.app.shared.json.JsonParser.parseObject(response.requireBody("memuat telemetri alur"))
        )
    }

    /** GET /api/tenant/pipeline */
    override suspend fun getPipeline(tenantSlug: String): Result<CustomTenantPipeline> = runCatching {
        val response = httpClient.get(resolveUrl(PIPELINE_PATH)) {
            tenantRequest(tenantSlug, tokenProvider)
            accept(ContentType.Application.Json)
        }
        PipelineGraphCodec.decodePipelineFromPayload(response.requireBody("memuat alur pabrik"))
    }

    /** PUT /api/tenant/pipeline — persists the whole topology. */
    override suspend fun savePipeline(
        tenantSlug: String,
        pipeline: CustomTenantPipeline
    ): Result<CustomTenantPipeline> = runCatching {
        val response = httpClient.put(resolveUrl(PIPELINE_PATH)) {
            tenantRequest(tenantSlug, tokenProvider)
            contentType(ContentType.Application.Json)
            setBody(PipelineGraphCodec.encodePipeline(pipeline))
        }
        PipelineGraphCodec.decodePipelineFromPayload(response.requireBody("menyimpan alur pabrik"))
    }

    /** POST /api/tenant/pipeline/reset */
    override suspend fun resetPipeline(
        tenantSlug: String,
        preset: GarmentBusinessPreset
    ): Result<CustomTenantPipeline> = runCatching {
        val response = httpClient.post(resolveUrl("$PIPELINE_PATH/reset")) {
            tenantRequest(tenantSlug, tokenProvider)
            contentType(ContentType.Application.Json)
            setBody("{\"preset\":\"${preset.code}\"}")
        }
        PipelineGraphCodec.decodePipelineFromPayload(response.requireBody("mereset alur pabrik"))
    }

    /** GET /api/tenant/pipeline/modules — catalogue plus subscription limits. */
    override suspend fun getModuleCatalog(tenantSlug: String): Result<TenantModuleCatalogSnapshot> = runCatching {
        val response = httpClient.get(resolveUrl("$PIPELINE_PATH/modules")) {
            tenantRequest(tenantSlug, tokenProvider)
            accept(ContentType.Application.Json)
        }
        TenantModuleCatalogCodec.decode(response.requireBody("memuat katalog modul"))
    }

    /** POST /api/tenant/pipeline/modules/activation — switch one module on or off. */
    override suspend fun setModuleActivation(
        tenantSlug: String,
        moduleId: String,
        isActive: Boolean
    ): Result<CustomTenantPipeline> = runCatching {
        val response = httpClient.post(resolveUrl("$PIPELINE_PATH/modules/activation")) {
            tenantRequest(tenantSlug, tokenProvider)
            contentType(ContentType.Application.Json)
            setBody("{\"moduleId\":\"${JsonWriter.escape(moduleId)}\",\"isActive\":$isActive}")
        }
        PipelineGraphCodec.decodePipelineFromPayload(response.requireBody("mengubah status modul"))
    }

    /** PUT /api/tenant/pipeline/modules/{nodeId} — rename a module for this tenant only. */
    override suspend fun renameModule(
        tenantSlug: String,
        nodeId: String,
        displayName: String,
        formulaParameters: Map<String, String>?
    ): Result<CustomTenantPipeline> = runCatching {
        val parametersJson = formulaParameters?.let { parameters ->
            val entries = parameters.entries.joinToString(",") { (key, value) ->
                "\"${JsonWriter.escape(key)}\":\"${JsonWriter.escape(value)}\""
            }
            ",\"formulaParameters\":{$entries}"
        } ?: ""

        val response = httpClient.put(resolveUrl("$PIPELINE_PATH/modules/$nodeId")) {
            tenantRequest(tenantSlug, tokenProvider)
            contentType(ContentType.Application.Json)
            setBody("{\"displayName\":\"${JsonWriter.escape(displayName)}\"$parametersJson}")
        }
        PipelineGraphCodec.decodePipelineFromPayload(response.requireBody("mengubah nama modul"))
    }

    /**
     * Reads the body, converting a non-2xx response into a failure that carries the server's
     * own message — plan-limit rejections are explanatory and worth surfacing verbatim.
     */
    private suspend fun HttpResponse.requireBody(action: String): String {
        val body = bodyAsText()
        if (!status.isSuccess()) {
            error("Gagal $action (HTTP ${status.value}): $body")
        }
        return body
    }

    private companion object {
        const val PIPELINE_PATH = "/api/tenant/pipeline"
    }
}

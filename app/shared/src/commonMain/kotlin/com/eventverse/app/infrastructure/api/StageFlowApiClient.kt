package com.eventverse.app.infrastructure.api

import com.eventverse.app.domain.stageflow.StageDefinition
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.shared.json.JsonParser
import com.eventverse.app.shared.stageflow.TenantStageFlowCodec
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess

/** Kerangka tahap pabrik (TRD-FLOW-001) — sumber kolom papan sampling & meja operator. */
interface StageFlowRemoteDataSource {
    suspend fun fetchTenantStages(): Result<List<StageDefinition>>
}

class StageFlowApiClient(
    private val httpClient: HttpClient = HttpClient(),
    private val baseUrl: String = "",
    private val tokenProvider: SessionTokenProvider = StoredSessionTokenProvider
) : StageFlowRemoteDataSource {

    private val tenantSlug: String
        get() = StoredTenantSlugProvider.currentTenantSlug() ?: "demo-tenant"

    override suspend fun fetchTenantStages(): Result<List<StageDefinition>> = runCatching {
        val response = httpClient.get("$baseUrl/api/tenant/stage-flow") {
            tenantRequest(tenantSlug, tokenProvider)
        }
        check(response.status.isSuccess()) { "Gagal memuat kerangka tahap: ${response.status}" }
        // Tenant id di sini hanya pengisi: klien memakai daftar tahapnya, bukan agregatnya.
        TenantStageFlowCodec.decode(TenantId("client"), JsonParser.parseObject(response.bodyAsText())).stages
    }
}

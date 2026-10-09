package com.eventverse.app.presentation.relation

import com.eventverse.app.infrastructure.api.SessionTokenProvider
import com.eventverse.app.infrastructure.api.StoredSessionTokenProvider
import com.eventverse.app.infrastructure.api.StoredTenantSlugProvider
import com.eventverse.app.infrastructure.api.tenantRequest
import com.eventverse.app.presentation.designsystem.RelationOption
import com.eventverse.app.shared.json.JsonParser
import com.eventverse.app.shared.json.JsonValue
import io.ktor.client.HttpClient
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.isSuccess

/**
 * Pencarian opsi rujukan (C7, TRD-FIELD-001 FR-4) — diimplementasi klien terhadap kontrak
 * route `GET /api/tenant/relation-options?module={target}&entity={entity}&q={kueri}` yang
 * dibangun paralel di Track B.
 *
 * Kontrak respons: `[{"id","label"}]`. Label v1 = field `TEXT` pertama baris target; server
 * sudah mem-`fallback` ke id, jadi klien tidak perlu menebak.
 */
interface RelationOptionsRemoteDataSource {
    suspend fun search(
        tenantSlug: String,
        module: String,
        entity: String,
        query: String
    ): Result<List<RelationOption>>
}

/**
 * Klien HTTP nyata. Satu `HttpClient` bersama dipakai seluruh call site (lihat
 * [RelationOptionsClients]) — membuat klien baru setiap recompose membocorkan connection pool.
 */
class RelationOptionsApiClient(
    private val httpClient: HttpClient = HttpClient(),
    private val baseUrl: String = "",
    private val tokenProvider: SessionTokenProvider = StoredSessionTokenProvider,
    private val tenantSlug: () -> String? = StoredTenantSlugProvider::currentTenantSlug
) : RelationOptionsRemoteDataSource {

    private fun resolveUrl(path: String): String =
        if (baseUrl.isNotBlank()) "${baseUrl.trimEnd('/')}$path" else path

    override suspend fun search(
        tenantSlug: String,
        module: String,
        entity: String,
        query: String
    ): Result<List<RelationOption>> = runCatching {
        val slug = tenantSlug.ifBlank { this.tenantSlug().orEmpty() }
        val response = httpClient.get(resolveUrl(RELATION_OPTIONS_PATH)) {
            tenantRequest(slug, tokenProvider)
            parameter("module", module)
            parameter("entity", entity)
            if (query.isNotBlank()) parameter("q", query)
            accept(ContentType.Application.Json)
        }
        val body = response.bodyAsText()
        if (!response.status.isSuccess()) {
            error("Gagal mencari rujukan (HTTP ${response.status.value}): $body")
        }
        JsonParser.parseArray(body).mapNotNull { element ->
            val obj = element as? JsonValue.Obj ?: return@mapNotNull null
            val id = obj.string("id") ?: return@mapNotNull null
            RelationOption(id = id, label = obj.string("label") ?: id)
        }
    }

    private companion object {
        const val RELATION_OPTIONS_PATH = "/api/tenant/relation-options"
    }
}

/** Klien bersama untuk seluruh pemilih rujukan — satu per proses, seperti `sharedFieldFileClient`. */
object RelationOptionsClients {
    val shared: RelationOptionsRemoteDataSource by lazy { RelationOptionsApiClient() }
}

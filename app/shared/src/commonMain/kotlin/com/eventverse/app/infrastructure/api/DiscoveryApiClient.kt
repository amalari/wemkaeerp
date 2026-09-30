package com.eventverse.app.infrastructure.api

import com.eventverse.app.shared.json.JsonParser
import com.eventverse.app.shared.json.JsonValue
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*

/**
 * Klien funnel discovery (plan §1, Fase D). Endpoint-nya per-pengguna (bukan `/api/admin`), jadi
 * token cukup; `X-Tenant-Slug` tetap dikirim bila ada karena superadmin tanpa header itu akan
 * ditolak `TenantResolutionPlugin` (404) pada path non-admin.
 *
 * Respons di-parse di sini menjadi [JsonValue] dan diteruskan apa adanya — bentuk ringkasan draf
 * adalah data yang ditampilkan, bukan kontrak yang dibungkus tipe domain lagi.
 */
class DiscoveryApiClient(
    private val httpClient: HttpClient = HttpClient(),
    private val baseUrl: String = "",
    private val tokenProvider: SessionTokenProvider = StoredSessionTokenProvider
) {
    private fun resolveUrl(path: String): String =
        if (baseUrl.isNotBlank()) "${baseUrl.trimEnd('/')}$path" else path

    private fun HttpRequestBuilder.authed() {
        StoredTenantSlugProvider.currentTenantSlug()?.let { header("X-Tenant-Slug", it) }
        tokenProvider.currentToken()?.let { token -> header(HttpHeaders.Authorization, "Bearer $token") }
    }

    /** POST /api/discovery/drafts — narasi → draf. */
    suspend fun createDraft(narrative: String, industryHint: String? = null, id: String? = null): Result<JsonValue> =
        call(HttpMethod.Post, "/api/discovery/drafts") {
            contentType(ContentType.Application.Json)
            setBody(
                buildString {
                    append("{\"narrative\":").append(JsonValue.Str(narrative).encode())
                    if (industryHint != null) append(",\"industryHint\":").append(JsonValue.Str(industryHint).encode())
                    if (id != null) append(",\"id\":").append(JsonValue.Str(id).encode())
                    append("}")
                }
            )
        }

    /** GET /api/discovery/drafts/{id} — ringkasan draf. */
    suspend fun getDraft(id: String): Result<JsonValue> = call(HttpMethod.Get, "/api/discovery/drafts/$id")

    /** GET /api/discovery/drafts/{id}/price — estimasi dari draf (B1). */
    suspend fun price(id: String, marginPercent: Double = 35.0): Result<JsonValue> =
        call(HttpMethod.Get, "/api/discovery/drafts/$id/price?marginPercent=$marginPercent")

    /** POST /api/discovery/drafts/{id}/lock — bekukan draf. */
    suspend fun lock(id: String): Result<JsonValue> = call(HttpMethod.Post, "/api/discovery/drafts/$id/lock")

    /** POST /api/discovery/drafts/{id}/submit — CTA "Bangun Sistem Ini" (B2). */
    suspend fun submit(id: String, companyName: String): Result<JsonValue> =
        call(HttpMethod.Post, "/api/discovery/drafts/$id/submit") {
            contentType(ContentType.Application.Json)
            setBody("{\"companyName\":${JsonValue.Str(companyName).encode()}}")
        }

    private suspend fun call(method: HttpMethod, path: String, block: HttpRequestBuilder.() -> Unit = {}): Result<JsonValue> =
        runCatching {
            val response = httpClient.request(resolveUrl(path)) {
                this.method = method
                authed()
                accept(ContentType.Application.Json)
                block()
            }
            val text = response.bodyAsText()
            if (!response.status.isSuccess()) {
                error(text.ifBlank { "HTTP ${response.status.value}" })
            }
            JsonParser.parseObject(text)
        }
}

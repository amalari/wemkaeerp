package com.eventverse.app.infrastructure.api

import com.eventverse.app.shared.json.JsonParser
import com.eventverse.app.shared.json.JsonValue
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*

/**
 * Klien WeMake Builder (PLAN-builder-console M0). Endpoint-nya milik tenant (prefiks /api/builder),
 * jadi token + `X-Tenant-Slug` cukup — pola yang sama dengan [DiscoveryApiClient].
 *
 * Respons diteruskan sebagai [JsonValue] apa adanya: bentuk overview (status, deployment aktif)
 * adalah data tampilan, bukan kontrak domain.
 */
class BuilderApiClient(
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

    /** GET /api/builder/overview — status tenant + deployment aktif + riwayat. */
    suspend fun overview(): Result<JsonValue> = call(HttpMethod.Get, "/api/builder/overview")

    private suspend fun call(method: HttpMethod, path: String): Result<JsonValue> =
        runCatching {
            val response = httpClient.request(resolveUrl(path)) {
                this.method = method
                authed()
                accept(ContentType.Application.Json)
            }
            val text = response.bodyAsText()
            if (!response.status.isSuccess()) {
                error(text.ifBlank { "HTTP ${response.status.value}" })
            }
            JsonParser.parse(text)
        }
}

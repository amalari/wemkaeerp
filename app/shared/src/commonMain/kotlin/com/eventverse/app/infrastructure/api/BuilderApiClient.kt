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

    /** GET /api/builder/draft — draf kerja tenant (envelope renderer Fase D); `null` bila belum ada. */
    suspend fun draft(): Result<JsonValue?> =
        call(HttpMethod.Get, "/api/builder/draft").map { it as? JsonValue.Null ?: it }

    /** GET /api/builder/chat — percakapan tenant + seluruh pesan. */
    suspend fun chat(): Result<JsonValue> = call(HttpMethod.Get, "/api/builder/chat")

    /** POST /api/builder/chat — kirim pesan; agent menjawab (patch usulan, belum diterapkan). */
    suspend fun sendMessage(text: String): Result<JsonValue> =
        call(HttpMethod.Post, "/api/builder/chat", """{"text":${JsonValue.Str(text).encode()}}""")

    /** POST /api/builder/chat/apply — aksi manusia: terapkan patch usulan dari pesan. */
    suspend fun applyPatch(messageId: String): Result<JsonValue> =
        call(HttpMethod.Post, "/api/builder/chat/apply", """{"messageId":${JsonValue.Str(messageId).encode()}}""")

    /** GET /api/builder/deployments — riwayat deployment + antrian build tenant. */
    suspend fun deployments(): Result<JsonValue> = call(HttpMethod.Get, "/api/builder/deployments")

    /** POST /api/builder/deployments — deploy: kunci draf + pin versi + aktifkan. */
    suspend fun deploy(): Result<JsonValue> = call(HttpMethod.Post, "/api/builder/deployments")

    /** POST /api/builder/deployments/rollback — pin versi sebelumnya; `force` = arsip modul eksplisit. */
    suspend fun rollback(force: Boolean = false): Result<JsonValue> =
        call(HttpMethod.Post, "/api/builder/deployments/rollback${if (force) "?force=true" else ""}")

    /**
     * GET /api/builder/billing/invoices — tagihan langganan **milik tenant pemanggil** (FR-M2-5).
     * Penyaringan ada di server (`tenantContext`), bukan di sini: klien tidak pernah mengirim
     * identitas tenant untuk endpoint ini.
     */
    suspend fun invoices(): Result<JsonValue> = call(HttpMethod.Get, "/api/builder/billing/invoices")

    private suspend fun call(method: HttpMethod, path: String, body: String? = null): Result<JsonValue> =
        runCatching {
            val response = httpClient.request(resolveUrl(path)) {
                this.method = method
                authed()
                body?.let {
                    contentType(ContentType.Application.Json)
                    setBody(it)
                }
                accept(ContentType.Application.Json)
            }
            val text = response.bodyAsText()
            if (!response.status.isSuccess()) {
                error(text.ifBlank { "HTTP ${response.status.value}" })
            }
            JsonParser.parse(text)
        }
}

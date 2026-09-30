package com.eventverse.app.infrastructure.api

import com.eventverse.app.domain.crm.prefill.LeadDraft
import com.eventverse.app.shared.crm.LeadDraftCodec
import com.eventverse.app.shared.json.JsonParser
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess

/** Tenant belum mengaktifkan draf lead AI (409). Dibedakan agar UI menampilkan ajakan aktivasi, bukan galat. */
class LeadDraftDisabledException : IllegalStateException("Draf lead AI belum diaktifkan untuk pabrik ini")

/** Kontrak klien draf lead AI (TRD-HELP-002) — antarmuka supaya ViewModel bisa diuji dengan palsu. */
interface LeadDraftGateway {
    suspend fun settings(): Result<LeadDraftCodec.AiSettings>
    suspend fun setEnabled(enabled: Boolean): Result<LeadDraftCodec.AiSettings>
    suspend fun draft(text: String): Result<LeadDraft>
}

class LeadDraftApiClient(
    private val httpClient: HttpClient = HttpClient(),
    private val baseUrl: String = "",
    private val tokenProvider: SessionTokenProvider = StoredSessionTokenProvider,
) : LeadDraftGateway {

    private fun url(path: String) = if (baseUrl.isNotBlank()) "${baseUrl.trimEnd('/')}$path" else path

    private fun HttpRequestBuilder.authed() {
        StoredTenantSlugProvider.currentTenantSlug()?.let { header("X-Tenant-Slug", it) }
        tokenProvider.currentToken()?.let { header(HttpHeaders.Authorization, "Bearer $it") }
    }

    private suspend fun HttpResponse.textOrThrow(): String {
        val text = bodyAsText()
        if (status == HttpStatusCode.Conflict) throw LeadDraftDisabledException()
        if (!status.isSuccess()) error(text.ifBlank { "HTTP ${status.value}" })
        return text
    }

    override suspend fun settings() = runCatching {
        LeadDraftCodec.decodeSettings(JsonParser.parseObject(httpClient.get(url(SETTINGS)) { authed() }.textOrThrow()))
    }

    override suspend fun setEnabled(enabled: Boolean) = runCatching {
        val response = httpClient.put(url(SETTINGS)) {
            authed(); contentType(ContentType.Application.Json); setBody("""{"leadDraftEnabled":$enabled}""")
        }
        LeadDraftCodec.decodeSettings(JsonParser.parseObject(response.textOrThrow()))
    }

    override suspend fun draft(text: String) = runCatching {
        val response = httpClient.post(url("/api/tenant/crm/leads/draft")) {
            authed(); contentType(ContentType.Application.Json); setBody(LeadDraftCodec.encodeText(text))
        }
        LeadDraftCodec.decodeDraft(JsonParser.parseObject(response.textOrThrow()))
    }

    private companion object {
        const val SETTINGS = "/api/tenant/crm/ai-settings"
    }
}

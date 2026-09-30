package com.eventverse.app.infrastructure.api

import com.eventverse.app.domain.help.usecases.HelpResult
import com.eventverse.app.domain.pack.ModuleId
import com.eventverse.app.shared.help.HelpCodec
import com.eventverse.app.shared.json.JsonParser
import io.ktor.client.HttpClient
import io.ktor.client.request.accept
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess

/** Kontrak klien AI helper — antarmuka supaya ViewModel chat bisa diuji dengan palsu (Fase 4). */
interface HelpGateway {
    suspend fun ask(question: String, currentModule: ModuleId?): Result<HelpResult>
}

/** Klien `POST /api/tenant/help/ask` (TRD-HELP-001 §4.4). */
class HelpApiClient(
    private val httpClient: HttpClient = HttpClient(),
    private val baseUrl: String = "",
    private val tokenProvider: SessionTokenProvider = StoredSessionTokenProvider,
) : HelpGateway {

    override suspend fun ask(question: String, currentModule: ModuleId?): Result<HelpResult> = runCatching {
        val response = httpClient.post(if (baseUrl.isNotBlank()) "${baseUrl.trimEnd('/')}/api/tenant/help/ask" else "/api/tenant/help/ask") {
            StoredTenantSlugProvider.currentTenantSlug()?.let { header("X-Tenant-Slug", it) }
            tokenProvider.currentToken()?.let { header(HttpHeaders.Authorization, "Bearer $it") }
            accept(ContentType.Application.Json)
            contentType(ContentType.Application.Json)
            setBody(HelpCodec.encodeRequest(question, currentModule).encode())
        }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) error(text.ifBlank { "HTTP ${response.status.value}" })
        HelpCodec.decodeResult(JsonParser.parseObject(text))
    }
}

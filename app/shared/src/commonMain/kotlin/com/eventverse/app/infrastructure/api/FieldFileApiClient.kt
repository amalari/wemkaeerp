package com.eventverse.app.infrastructure.api

import com.eventverse.app.shared.json.JsonParser
import io.ktor.client.HttpClient
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess

/** Galat HTTP ber-status dari endpoint berkas field — bahan pemetaan pesan manusiawi (FR-3). */
class FieldFileHttpException(val status: Int, message: String) : Exception(message)

/**
 * Operasi unggah/unduh berkas field (TRD-FIELD-002 §4.4). Dua varian endpoint berkontrak identik:
 * module-records (prototype) dan CRM-leads — dipisah per klien karena gate modul induknya beda,
 * bentuk responsnya sama (`{"ref"}` / `{"url"}` presigned).
 */
interface FieldFileRemoteDataSource {

    /** Unggah byte (body raw, metadata di query — pola PO/mockup); sukses = `FileRef` string. */
    suspend fun uploadFieldFile(
        moduleCode: String,
        recordId: String,
        fieldKey: String,
        fileName: String,
        contentType: String,
        bytes: ByteArray
    ): Result<String>

    /** URL presigned (15 menit) untuk mengunduh berkas referensi. */
    suspend fun fieldFileDownloadUrl(moduleCode: String, recordId: String, fieldKey: String): Result<String>
}

/**
 * Klien endpoint module-records untuk tipe field `FILE` (kontrak §4.4, Track B mengimplementasi
 * servernya). Meta lewat query parameter, byte sebagai body mentah `application/octet-stream` —
 * satu request, tanpa plugin multipart (komentar DealRoutes L82).
 *
 * Kegagalan non-2xx dilempar sebagai [FieldFileHttpException] ber-status (bukan pesan generik)
 * supaya UI bisa memetakan 413/415/503 ke sebab yang bisa dibaca pengguna.
 */
class FieldFileApiClient(
    private val httpClient: HttpClient = HttpClient(),
    private val baseUrl: String = "",
    private val tokenProvider: SessionTokenProvider = StoredSessionTokenProvider,
    private val tenantSlug: () -> String? = StoredTenantSlugProvider::currentTenantSlug
) : FieldFileRemoteDataSource {

    override suspend fun uploadFieldFile(
        moduleCode: String,
        recordId: String,
        fieldKey: String,
        fileName: String,
        contentType: String,
        bytes: ByteArray
    ): Result<String> = runCatching {
        val response = httpClient.post(
            resolveUrl("$MODULE_RECORDS_BASE/$moduleCode/records/$recordId/fields/$fieldKey/upload")
        ) {
            authorize()
            parameter("fileName", fileName)
            parameter("contentType", contentType)
            contentType(ContentType.Application.OctetStream)
            setBody(bytes)
        }
        val body = response.fieldFilePathBody("mengunggah berkas")
        JsonParser.parseObject(body).string("ref")
            ?: error("Server tidak mengembalikan referensi berkas")
    }

    override suspend fun fieldFileDownloadUrl(
        moduleCode: String,
        recordId: String,
        fieldKey: String
    ): Result<String> = runCatching {
        val response = httpClient.get(
            resolveUrl("$MODULE_RECORDS_BASE/$moduleCode/records/$recordId/fields/$fieldKey/download")
        ) {
            authorize()
            accept(ContentType.Application.Json)
        }
        val body = response.fieldFilePathBody("membuat tautan unduhan")
        JsonParser.parseObject(body).string("url")
            ?: error("Respons tautan unduhan tidak valid")
    }

    private fun io.ktor.client.request.HttpRequestBuilder.authorize() {
        tenantSlug()?.let { header("X-Tenant-Slug", it) }
        tokenProvider.currentToken()?.let { header(HttpHeaders.Authorization, "Bearer $it") }
    }

    private fun resolveUrl(path: String): String =
        if (baseUrl.isNotBlank()) "${baseUrl.trimEnd('/')}$path" else path

    private companion object {
        /** Basis route module-records hasil generate (`{moduleCode}` = schema modul). */
        const val MODULE_RECORDS_BASE = "/api/tenant/modules"
    }
}

/**
 * Ambil body respons endpoint berkas field, atau lempar [FieldFileHttpException] ber-status untuk
 * non-2xx. Satu implementasi dipakai bersama klien module-records & CRM-leads — supaya kontrak
 * status yang mendasari pemetaan 413/415/503 (FR-3) tidak menyimpang antar endpoint.
 */
internal suspend fun HttpResponse.fieldFilePathBody(action: String): String {
    val body = bodyAsText()
    if (!status.isSuccess()) {
        throw FieldFileHttpException(status.value, body.ifBlank { "Gagal $action (HTTP ${status.value})" })
    }
    return body
}

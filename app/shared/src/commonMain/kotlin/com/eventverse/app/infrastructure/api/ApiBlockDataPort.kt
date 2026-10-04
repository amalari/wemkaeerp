package com.eventverse.app.infrastructure.api

import com.eventverse.app.domain.prototype.BlockDataPort
import com.eventverse.app.domain.prototype.DataBinding
import com.eventverse.app.domain.prototype.PortError
import com.eventverse.app.domain.prototype.PortException
import com.eventverse.app.domain.prototype.PrototypeRow
import com.eventverse.app.shared.json.JsonParser
import com.eventverse.app.shared.json.JsonValue
import com.eventverse.app.shared.json.jsonObjectOf
import com.eventverse.app.shared.json.jsonStringMapOf
import io.ktor.client.HttpClient
import io.ktor.client.request.accept
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException

/**
 * [BlockDataPort] yang berbicara dengan route CRUD modul hasil generate (plan data-port §3.6, butir C1).
 * Satu port = satu tabel: [basePath] adalah `DataBinding.Api.basePath`, mis.
 * `/api/tenant/modules/layanan_change_request/change_requests`. Kontrak HTTP **sudah ada** di route
 * yang digenerate `HandoffScaffoldGenerator.generateFromSpec`:
 *
 * | Operasi | HTTP | Respons sukses |
 * |---|---|---|
 * | `load` | `GET basePath` | `[ {id, values} ]` |
 * | `create` | `POST basePath` `{values}` | `201 {id, values}` (**id dibuat server**) |
 * | `update` | `PUT basePath/{id}` `{values}` | `200 {id, values}` (atomik di server) |
 * | `delete` | `DELETE basePath/{id}` | `200` |
 *
 * **Kegagalan tidak pernah lolos sebagai pengecualian mentah**: semuanya menjadi
 * `Result.failure(PortException(PortError))` sehingga UI cukup memetakan [PortError] ke pesan
 * (`userMessage`) tanpa mengenal HTTP. Pemetaan status (tabel [portErrorFor]) sengaja satu tempat.
 * [CancellationException] **tidak** ditelan — pembatalan coroutine harus tetap merambat.
 *
 * Validasi isi tetap milik server (reducer yang sama dengan prototype); klien hanya meneruskan
 * dan menerjemahkan jawabannya.
 */
class ApiBlockDataPort(
    private val basePath: String,
    private val httpClient: HttpClient = HttpClient(),
    private val baseUrl: String = "",
    private val tokenProvider: SessionTokenProvider = StoredSessionTokenProvider,
    private val tenantSlug: () -> String? = StoredTenantSlugProvider::currentTenantSlug
) : BlockDataPort {

    init { require(basePath.isNotBlank()) { "basePath ApiBlockDataPort kosong" } }

    override suspend fun load(): Result<List<PrototypeRow>> = call(HttpMethod.Get, basePath) { text ->
        val items = (JsonParser.parse(text) as? JsonValue.Arr)?.items
            ?: throw IllegalStateException("Daftar bukan array JSON")
        items.map { parseRow(it) }
    }

    override suspend fun create(values: Map<String, String>): Result<PrototypeRow> =
        call(HttpMethod.Post, basePath, valuesBody(values)) { parseRow(JsonParser.parse(it)) }

    override suspend fun update(rowId: String, changes: Map<String, String>): Result<PrototypeRow> =
        call(HttpMethod.Put, rowPath(rowId), valuesBody(changes)) { parseRow(JsonParser.parse(it)) }

    override suspend fun delete(rowId: String): Result<Unit> =
        call(HttpMethod.Delete, rowPath(rowId)) { }

    private fun rowPath(rowId: String) = basePath.trimEnd('/') + "/" + rowId

    private fun valuesBody(values: Map<String, String>): String =
        jsonObjectOf("values" to jsonStringMapOf(values)).encode()

    private fun parseRow(node: JsonValue): PrototypeRow {
        val obj = node as? JsonValue.Obj ?: throw IllegalStateException("Baris bukan objek JSON")
        val id = obj.string("id")?.takeIf { it.isNotBlank() } ?: throw IllegalStateException("Baris tanpa id")
        return PrototypeRow(id, obj.stringMap("values"))
    }

    private suspend fun <T> call(method: HttpMethod, path: String, body: String? = null, parse: (String) -> T): Result<T> =
        try {
            val response: HttpResponse = httpClient.request(if (baseUrl.isNotBlank()) baseUrl.trimEnd('/') + path else path) {
                this.method = method
                tenantSlug()?.let { header("X-Tenant-Slug", it) }
                tokenProvider.currentToken()?.let { header(HttpHeaders.Authorization, "Bearer $it") }
                body?.let { contentType(ContentType.Application.Json); setBody(it) }
                accept(ContentType.Application.Json)
            }
            val text = response.bodyAsText()
            if (response.status.isSuccess()) {
                // Respons sukses yang tak terbaca = server berperilaku di luar kontrak: bukan galat pengguna.
                runCatching { parse(text) }.fold(
                    onSuccess = { Result.success(it) },
                    onFailure = { Result.failure(PortException(PortError.Unavailable("respons server tidak terbaca (${it.message})"))) }
                )
            } else {
                Result.failure(PortException(portErrorFor(response.status.value, text)))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Result.failure(PortException(PortError.Unavailable(e.message.orEmpty())))
        }

    internal companion object {
        /**
         * Satu-satunya pemetaan status HTTP → [PortError]. 400/409/422 = isi atau aturan ditolak (pesan server
         * ditampilkan apa adanya); 401/402/403 = tidak berwenang atau akses tenant tertutup; 404 = tak ada;
         * lainnya (5xx, 408, 429, …) = tidak tersedia — **bukan** salah pengguna.
         */
        fun portErrorFor(status: Int, body: String): PortError {
            val message = body.trim().takeIf { it.isNotEmpty() && it.length <= 400 }.orEmpty()
            return when (status) {
                400, 409, 422 -> PortError.Validation(message.ifBlank { "Isian ditolak server." })
                401, 402, 403 -> PortError.Forbidden(message)
                404 -> PortError.NotFound(message)
                else -> PortError.Unavailable("HTTP $status")
            }
        }
    }
}

/** Fabrik port untuk satu layar ber-binding API (dipakai `PrototypeSession` saat membangun blok). */
fun apiBlockDataPortFor(binding: DataBinding.Api): BlockDataPort = ApiBlockDataPort(binding.basePath)

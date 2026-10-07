package com.eventverse.app.infrastructure.api

import com.eventverse.app.shared.json.JsonParser
import com.eventverse.app.shared.json.JsonValue
import com.eventverse.app.shared.json.jsonObjectOf
import com.eventverse.app.shared.json.jsonOf
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

    /**
     * GET /api/discovery/drafts — draf milik pemanggil (E1, sesi interview persisten).
     * Ringkasan penuh (bukan stub), jadi wizard bisa melanjutkan sesi tanpa fetch kedua.
     */
    suspend fun listDrafts(): Result<JsonValue> = call(HttpMethod.Get, "/api/discovery/drafts")

    /** GET /api/discovery/drafts/{id} — ringkasan draf. */
    suspend fun getDraft(id: String): Result<JsonValue> = call(HttpMethod.Get, "/api/discovery/drafts/$id")

    /** GET /api/discovery/drafts/{id}/price — estimasi dari draf (B1); [modules] = harga hanya modul terpilih. */
    suspend fun price(id: String, marginPercent: Double = 35.0, modules: Collection<String>? = null): Result<JsonValue> =
        call(
            HttpMethod.Get,
            "/api/discovery/drafts/$id/price?marginPercent=$marginPercent" +
                (modules?.let { "&modules=" + it.joinToString(",") } ?: "")
        )

    /** POST /api/discovery/drafts/{id}/lock — bekukan draf. */
    suspend fun lock(id: String): Result<JsonValue> = call(HttpMethod.Post, "/api/discovery/drafts/$id/lock")

    /** POST /api/discovery/drafts/{id}/submit — CTA "Bangun Sistem Ini" (B2). */
    suspend fun submit(id: String, companyName: String): Result<JsonValue> =
        call(HttpMethod.Post, "/api/discovery/drafts/$id/submit") {
            contentType(ContentType.Application.Json)
            setBody("{\"companyName\":${JsonValue.Str(companyName).encode()}}")
        }

    /** POST /api/discovery/drafts/{id}/interview — kirim jawaban wawancara (plan §6). */
    suspend fun answerInterview(id: String, body: JsonValue.Obj): Result<JsonValue> =
        call(HttpMethod.Post, "/api/discovery/drafts/$id/interview") {
            contentType(ContentType.Application.Json)
            setBody(body.encode())
        }

    /**
     * URL PDF blueprint (Fase D) untuk dibuka di tab browser.
     *
     * Dua langkah karena tab browser tidak bisa mengirim header `Authorization`: sesi ditukar dengan
     * tiket pendek (±60 detik) yang cakupannya hanya draf ini, lalu PDF-nya dibuka lewat `?ticket=`.
     * Sama seperti cetakan jejak produksi — dan alasan yang sama.
     */
    suspend fun blueprintPdfUrl(id: String): Result<String> =
        call(HttpMethod.Post, "/api/discovery/drafts/$id/print-ticket").mapCatching { body ->
            val obj = body as? JsonValue.Obj ?: error("Respons tiket cetak tidak dikenali")
            val ticket = obj.string("ticket")?.takeIf { it.isNotBlank() }
                ?: error("Server tidak menerbitkan tiket cetak")
            resolveUrl("/api/discovery/drafts/$id/blueprint.pdf") + "?ticket=" + ticket
        }

    /** GET /api/discovery/patterns — daftar pola Studio (Fase C); login cukup. */
    suspend fun listPrototypePatterns(): Result<JsonValue> =
        call(HttpMethod.Get, "/api/discovery/patterns")

    /**
     * GET /api/discovery/demands — buku demand (E2/E3). Server menolak non-superadmin (403);
     * layar yang memakainya menjelaskan gerbang itu, bukan menjadi satu-satunya penjaga.
     */
    suspend fun listDemands(): Result<JsonValue> = call(HttpMethod.Get, "/api/discovery/demands")

    /**
     * POST /api/discovery/patterns — simpan pola Studio (Fase C).
     *
     * Server hanya menerima **superadmin platform** (403 untuk peran lain) dan memvalidasi ulang
     * widget kosakata tertutup + pack yang dikenal, jadi layar tidak perlu — dan tidak boleh —
     * menjadi satu-satunya gerbang.
     *
     * [pattern] ditulis apa adanya sebagai objek JSON: bentuknya milik Studio
     * (`PrototypePatternUi.patternJson`), bukan kontrak yang disusun ulang di lapisan HTTP ini.
     * `id` null berarti pola baru — server yang membuat id-nya.
     */
    suspend fun savePrototypePattern(
        name: String,
        widgetCode: String,
        packCode: String?,
        pattern: JsonValue.Obj,
        id: String? = null
    ): Result<JsonValue> = call(HttpMethod.Post, "/api/discovery/patterns") {
        contentType(ContentType.Application.Json)
        setBody(
            jsonObjectOf(
                "id" to jsonOf(id),
                "name" to jsonOf(name),
                "widget" to jsonOf(widgetCode),
                "packCode" to jsonOf(packCode),
                "pattern" to pattern
            ).encode()
        )
    }

    /**
     * Satu jalur HTTP untuk semua endpoint discovery.
     *
     * Respons di-parse sebagai **nilai JSON apa pun**, bukan wajib objek: `GET
     * /api/discovery/patterns` mengembalikan array, dan `parseObject` di sini pernah membuat layar
     * Studio menampilkan `Expected a JSON object at root` pada daftar pola yang sehat. Bentuknya
     * diperiksa di pemanggil, tempat maknanya diketahui.
     */
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
            JsonParser.parse(text)
        }
}

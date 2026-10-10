package com.eventverse.app.infrastructure.api

import com.eventverse.app.domain.auth.*
import com.eventverse.app.domain.rbac.TestingPersona
import com.eventverse.app.domain.tenant.TenantId
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.CancellationException

class AuthApiClient(
    private val clientProvider: () -> HttpClient = { HttpClient() },
    private val baseUrl: String = ""
) {
    private val httpClient: HttpClient by lazy { clientProvider() }

    constructor(httpClient: HttpClient, baseUrl: String = "") : this({ httpClient }, baseUrl)

    private fun resolveUrl(path: String): String =
        if (baseUrl.isNotBlank()) "${baseUrl.trimEnd('/')}$path" else path

    /**
     * POST /api/public/auth/demo
     * Mengautentikasi pengguna demo langsung ke database PostgreSQL melalui backend Ktor,
     * mengembalikan real signed JWT token dan User data.
     *
     * Gagal selalu berupa [AuthApiError]: [AuthApiError.Rejected] (server menolak) atau
     * [AuthApiError.Unreachable] (tidak terjangkau). Tidak ada sesi pengganti.
     */
    suspend fun loginDemo(tenantSlug: String, role: String = "TENANT_ADMIN"): Result<UserSession> =
        demoSession("Autentikasi Demo gagal") {
            post(resolveUrl("/api/public/auth/demo?tenantSlug=${tenantSlug.encodeURLParameter()}&role=$role")) {
                accept(ContentType.Application.Json)
            }
        }

    /**
     * Jalur bersama login demo/persona (endpoint dan gerbang yang sama). Membedakan galat HTTP dari
     * galat jaringan. 404 tanpa isi = login demo dimatikan di server (`WEMADE_DEMO_LOGIN`); 404 berisi
     * = tenant tidak ada, jadi pesan server dipakai apa adanya.
     */
    private suspend fun demoSession(
        failLabel: String,
        request: suspend HttpClient.() -> HttpResponse
    ): Result<UserSession> {
        val response = try {
            httpClient.request()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return Result.failure(AuthApiError.Unreachable(e))
        }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) {
            val status = response.status.value
            val message = when {
                status == 404 && text.isBlank() -> "Login demo dimatikan di server ini"
                text.isNotBlank() -> text.trim()
                else -> "$failLabel (HTTP $status)"
            }
            return Result.failure(AuthApiError.Rejected(status, text, message))
        }
        val session = parseUserSession(text)
            ?: return Result.failure(AuthApiError.Malformed("Gagal mem-parsing sesi pengguna dari server"))
        return Result.success(session)
    }

    /**
     * POST /api/public/auth/demo — masuk sebagai persona pengujian.
     *
     * Dikirim sebagai form body, bukan query string seperti [loginDemo]: nama persona diketik
     * bebas oleh penguji dan lazim mengandung spasi serta huruf beraksen.
     *
     * Server yang memutuskan identitasnya — mencari atau membuat akun, lalu menerbitkan JWT
     * bertanda tangan. Client tidak pernah merakit sesi sendiri, supaya wewenang yang tampil di
     * layar selalu berasal dari sumber yang sama dengan wewenang yang ditegakkan server.
     */
    suspend fun loginPersona(persona: TestingPersona): Result<UserSession> =
        demoSession("Login persona gagal") {
            post(resolveUrl("/api/public/auth/demo")) {
                accept(ContentType.Application.Json)
                contentType(ContentType.Application.FormUrlEncoded)
                setBody(
                    buildList {
                        add("tenantSlug" to persona.tenantSlug)
                        add("username" to persona.name)
                        persona.roleId?.let { add("role" to it.value) }
                        persona.departmentId?.let { add("departmentId" to it) }
                    }.formUrlEncode()
                )
            }
        }

    /**
     * POST /api/public/auth/google
     *
     * Menukar Google ID token dengan sesi WeMade. Server memverifikasi token itu langsung
     * ke Google (termasuk pencocokan audience/client id) lalu menerbitkan JWT bertanda
     * tangan, jadi identitas pengguna tidak pernah ditentukan di sisi client.
     */
    suspend fun loginWithGoogle(
        idToken: String,
        tenantSlug: String?
    ): Result<UserSession> = runCatching {
        require(idToken.isNotBlank()) { "Google ID token tidak boleh kosong" }

        // tenantSlug null = login di permukaan platform: server menurunkan tenant dari akun.
        val response = httpClient.post(resolveUrl("/api/public/auth/google")) {
            accept(ContentType.Application.Json)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(
                buildList {
                    add("idToken" to idToken)
                    tenantSlug?.takeIf { it.isNotBlank() }?.let { add("tenantSlug" to it) }
                }.formUrlEncode()
            )
        }
        if (!response.status.isSuccess()) {
            error("Login Google gagal (HTTP ${response.status.value}): ${response.bodyAsText()}")
        }
        val text = response.bodyAsText()
        parseUserSession(text) ?: error("Gagal mem-parsing sesi pengguna dari server: $text")
    }

    /**
     * GET /api/public/auth/me
     * Memverifikasi JWT token ke backend dan mengambil profil user aktif dari DB.
     */
    suspend fun verifySession(token: String): Result<UserSession> = runCatching {
        val response = httpClient.get(resolveUrl("/api/public/auth/me")) {
            header("Authorization", "Bearer $token")
            accept(ContentType.Application.Json)
        }
        if (!response.status.isSuccess()) {
            error("Sesi tidak valid (HTTP ${response.status.value}): ${response.bodyAsText()}")
        }
        val text = response.bodyAsText()
        parseUserSession(text) ?: error("Gagal mem-parsing profil user dari server: $text")
    }

    /** GET /api/public/onboarding/config → `platformBaseDomain` (`null` = mode lokal). */
    suspend fun fetchPlatformBaseDomain(): Result<String?> = runCatching {
        val response = httpClient.get(resolveUrl("/api/public/onboarding/config")) {
            accept(ContentType.Application.Json)
        }
        if (!response.status.isSuccess()) error("Konfigurasi platform tidak tersedia (HTTP ${response.status.value})")
        extractString(response.bodyAsText(), "platformBaseDomain")?.ifBlank { null }
    }

    /**
     * POST /api/public/auth/handoff/issue — di `app.`, menerbitkan tiket sekali pakai untuk membawa
     * sesi ke subdomain tenant milik akun (discovery-M3-login-split).
     */
    suspend fun issueHandoff(token: String, actAs: String? = null): Result<HandoffTicket> = runCatching {
        // actAs = superadmin masuk tenant lain (discovery-M3b); server mencatat audit di tenant itu.
        val response = httpClient.post(resolveUrl("/api/public/auth/handoff/issue")) {
            header("Authorization", "Bearer $token")
            accept(ContentType.Application.Json)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(listOfNotNull(actAs?.let { "actAs" to it }).formUrlEncode())
        }
        if (!response.status.isSuccess()) {
            error("Gagal menyiapkan perpindahan ke workspace (HTTP ${response.status.value}): ${response.bodyAsText()}")
        }
        val text = response.bodyAsText()
        HandoffTicket(
            ticket = extractString(text, "ticket") ?: error("Tiket tidak ada di respons: $text"),
            tenantSlug = extractString(text, "tenantSlug") ?: error("Tenant tidak ada di respons: $text"),
            origin = extractString(text, "origin") ?: error("Server belum mengonfigurasi PLATFORM_BASE_DOMAIN")
        )
    }

    /** POST /api/public/auth/handoff — di `<slug>.`, menukar tiket menjadi sesi biasa. */
    suspend fun redeemHandoff(ticket: String): Result<UserSession> = runCatching {
        val response = httpClient.post(resolveUrl("/api/public/auth/handoff")) {
            accept(ContentType.Application.Json)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(listOf("ticket" to ticket).formUrlEncode())
        }
        if (!response.status.isSuccess()) {
            error("Tautan masuk tidak berlaku lagi. Silakan masuk kembali. (HTTP ${response.status.value})")
        }
        val text = response.bodyAsText()
        parseUserSession(text) ?: error("Gagal mem-parsing sesi pengguna dari server: $text")
    }

    /**
     * POST /api/admin/tenants/{slug}/act-as — superadmin masuk Builder tenant **di origin `app.`**:
     * server mencatat audit lalu menerbitkan sesi yang ditambatkan ke tenant itu (tanpa tiket, tanpa pindah origin).
     */
    suspend fun actAsSession(token: String, slug: String): Result<UserSession> = runCatching {
        val response = httpClient.post(resolveUrl("/api/admin/tenants/$slug/act-as")) {
            header("Authorization", "Bearer $token")
            accept(ContentType.Application.Json)
        }
        if (!response.status.isSuccess()) {
            error("Gagal masuk ke workspace $slug (HTTP ${response.status.value}): ${response.bodyAsText()}")
        }
        val text = response.bodyAsText()
        parseUserSession(text) ?: error("Gagal mem-parsing sesi pengguna dari server: $text")
    }

    companion object {
        /** Query param pembawa tiket handoff pada URL `<slug>.<base>/login?handoff=…`. */
        const val HANDOFF_QUERY_PARAM = "handoff"

        /**
         * Key the persisted auth session lives under. Declared here, beside the
         * (de)serialisation that owns the format, so infrastructure need not reach into
         * the presentation layer to read the token.
         */
        const val SESSION_STORAGE_KEY = "wemade_auth_session"

        fun serializeSession(session: UserSession): String {
            val user = session.user
            val escapedToken = escapeJson(session.token.value)
            val escapedSlug = escapeJson(session.tenantSlug ?: "")
            val escapedUserId = escapeJson(user.id.value)
            val escapedTenantId = escapeJson(user.tenantId?.value ?: "")
            val escapedUsername = escapeJson(user.username.value)
            val escapedEmail = escapeJson(user.email.value)
            val roleName = user.role.name

            val departmentJson = user.departmentId?.let { "\"${escapeJson(it)}\"" } ?: "null"
            val customRoleJson = user.customRoleId?.let { "\"${escapeJson(it)}\"" } ?: "null"

            return "{\"token\":\"$escapedToken\",\"tenantSlug\":\"$escapedSlug\",\"user\":{\"id\":\"$escapedUserId\",\"tenantId\":\"$escapedTenantId\",\"username\":\"$escapedUsername\",\"email\":\"$escapedEmail\",\"role\":\"$roleName\",\"departmentId\":$departmentJson,\"customRoleId\":$customRoleJson}}"
        }

        fun deserializeSession(json: String?): UserSession? {
            if (json.isNullOrBlank()) return null
            return parseUserSession(json)
        }

        private fun parseUserSession(json: String): UserSession? {
            try {
                val tokenStr = extractString(json, "token") ?: return null
                val tenantSlug = extractString(json, "tenantSlug")
                val userJson = extractJsonObject(json, "user") ?: return null

                val userIdStr = extractString(userJson, "id") ?: return null
                val tenantIdStr = extractString(userJson, "tenantId")
                val usernameStr = extractString(userJson, "username") ?: "user"
                val emailStr = extractString(userJson, "email") ?: "user@example.com"
                val roleStr = extractString(userJson, "role") ?: Role.TENANT_ADMIN.name
                val role = runCatching { Role.valueOf(roleStr) }.getOrDefault(Role.TENANT_ADMIN)

                val user = User(
                    id = UserId(userIdStr),
                    tenantId = tenantIdStr?.takeIf { it.isNotBlank() }?.let { TenantId(it) },
                    username = Username(usernameStr),
                    email = EmailAddress(emailStr),
                    role = role,
                    isActive = true,
                    departmentId = extractString(userJson, "departmentId")?.takeIf { it.isNotBlank() },
                    customRoleId = extractString(userJson, "customRoleId")?.takeIf { it.isNotBlank() }
                )

                return UserSession(
                    user = user,
                    token = AuthToken(tokenStr),
                    tenantSlug = tenantSlug
                )
            } catch (_: Exception) {
                return null
            }
        }

        private fun escapeJson(s: String): String =
            s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "")

        private fun extractJsonObject(json: String, key: String): String? {
            val pattern = "\"$key\"\\s*:\\s*\\{".toRegex()
            val match = pattern.find(json) ?: return null
            val startIndex = match.range.last
            var depth = 0
            var inQuotes = false
            var escape = false
            for (i in startIndex until json.length) {
                val c = json[i]
                if (escape) { escape = false; continue }
                if (c == '\\') { escape = true; continue }
                if (c == '"') { inQuotes = !inQuotes; continue }
                if (!inQuotes) {
                    if (c == '{') depth++
                    else if (c == '}') {
                        depth--
                        if (depth == 0) return json.substring(startIndex, i + 1)
                    }
                }
            }
            return null
        }

        private fun extractString(json: String, key: String): String? {
            val topLevelOnly = stripNestedObjects(json)
            val regex = "\"$key\"\\s*:\\s*\"([^\"]*)\"".toRegex()
            return regex.find(topLevelOnly)?.groupValues?.get(1)
        }

        private fun stripNestedObjects(json: String): String {
            val sb = StringBuilder()
            var depth = 0
            var inQuotes = false
            var escape = false
            for (i in json.indices) {
                val c = json[i]
                if (escape) {
                    escape = false
                    if (depth <= 1) sb.append(c)
                    continue
                }
                if (c == '\\') {
                    escape = true
                    if (depth <= 1) sb.append(c)
                    continue
                }
                if (c == '"') {
                    inQuotes = !inQuotes
                    if (depth <= 1) sb.append(c)
                    continue
                }
                if (!inQuotes) {
                    if (c == '{' || c == '[') {
                        depth++
                        if (depth <= 1) sb.append(c)
                    } else if (c == '}' || c == ']') {
                        if (depth <= 1) sb.append(c)
                        depth--
                    } else if (depth <= 1) {
                        sb.append(c)
                    }
                } else if (depth <= 1) {
                    sb.append(c)
                }
            }
            return sb.toString()
        }
    }
}

/** Tiket serah-terima sesi + origin subdomain tujuan (`https://<slug>.<base>`). */
data class HandoffTicket(val ticket: String, val tenantSlug: String, val origin: String)

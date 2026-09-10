package com.eventverse.app.infrastructure.api

import com.eventverse.app.domain.auth.*
import com.eventverse.app.domain.tenant.TenantId
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*

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
     */
    suspend fun loginDemo(
        tenantSlug: String = "wemade-demo",
        role: String = "TENANT_ADMIN"
    ): Result<UserSession> = runCatching {
        val response = httpClient.post(resolveUrl("/api/public/auth/demo?tenantSlug=$tenantSlug&role=$role")) {
            accept(ContentType.Application.Json)
        }
        if (!response.status.isSuccess()) {
            error("Autentikasi Demo gagal (HTTP ${response.status.value}): ${response.bodyAsText()}")
        }
        val text = response.bodyAsText()
        parseUserSession(text) ?: error("Gagal mem-parsing sesi pengguna dari server: $text")
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
        tenantSlug: String
    ): Result<UserSession> = runCatching {
        require(idToken.isNotBlank()) { "Google ID token tidak boleh kosong" }
        require(tenantSlug.isNotBlank()) { "Subdomain perusahaan wajib diisi" }

        val response = httpClient.post(resolveUrl("/api/public/auth/google")) {
            accept(ContentType.Application.Json)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(
                listOf(
                    "idToken" to idToken,
                    "tenantSlug" to tenantSlug
                ).formUrlEncode()
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

    companion object {
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

            return "{\"token\":\"$escapedToken\",\"tenantSlug\":\"$escapedSlug\",\"user\":{\"id\":\"$escapedUserId\",\"tenantId\":\"$escapedTenantId\",\"username\":\"$escapedUsername\",\"email\":\"$escapedEmail\",\"role\":\"$roleName\"}}"
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
                    isActive = true
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

package com.eventverse.app.infrastructure.auth

import com.eventverse.app.domain.auth.GoogleUserProfile
import com.eventverse.app.infrastructure.EnvLoader
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration

/**
 * Service for interacting with Google OAuth 2.0 / OpenID Connect APIs.
 */
class GoogleAuthService(
    val clientId: String = EnvLoader.get("GOOGLE_CLIENT_ID"),
    val clientSecret: String = EnvLoader.get("GOOGLE_CLIENT_SECRET"),
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build()
) {

    /**
     * Build the Google OAuth 2.0 consent URL for browser redirection.
     */
    fun buildAuthorizationUrl(redirectUri: String, state: String? = null): String {
        val encodedRedirect = URLEncoder.encode(redirectUri, StandardCharsets.UTF_8)
        val encodedScope = URLEncoder.encode("openid email profile", StandardCharsets.UTF_8)
        val stateParam = if (!state.isNullOrBlank()) {
            "&state=" + URLEncoder.encode(state, StandardCharsets.UTF_8)
        } else ""

        return "https://accounts.google.com/o/oauth2/v2/auth?" +
                "client_id=$clientId" +
                "&response_type=id_token" +
                "&redirect_uri=$encodedRedirect" +
                "&scope=$encodedScope" +
                "&nonce=" + System.currentTimeMillis() +
                stateParam
    }

    /**
     * Verifies the Google ID token and extracts the verified user profile.
     * Uses Google's standard tokeninfo endpoint.
     */
    fun verifyIdToken(idToken: String): Result<GoogleUserProfile> = runCatching {
        require(idToken.isNotBlank()) { "Google ID Token cannot be blank" }

        // Test mock token support for local testing / automated test suites
        if (idToken.startsWith("mock-google-token:")) {
            val email = idToken.removePrefix("mock-google-token:")
            return@runCatching GoogleUserProfile(
                email = email,
                name = "Mock Google User",
                googleSubjectId = "mock-sub-${email.hashCode()}",
                emailVerified = true
            )
        }

        val request = HttpRequest.newBuilder()
            .uri(URI.create("https://oauth2.googleapis.com/tokeninfo?id_token=" + URLEncoder.encode(idToken, StandardCharsets.UTF_8)))
            .timeout(Duration.ofSeconds(10))
            .GET()
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

        if (response.statusCode() != 200) {
            error("Gagal memvalidasi token ke Google (Status ${response.statusCode()}): ${response.body()}")
        }

        val body = response.body()
        val email = extractJsonField(body, "email") ?: error("Email tidak ditemukan dalam token Google")
        val name = extractJsonField(body, "name")
        val sub = extractJsonField(body, "sub") ?: error("Subject ID tidak ditemukan dalam token Google")
        val emailVerifiedStr = extractJsonField(body, "email_verified") ?: "false"
        val aud = extractJsonField(body, "aud")

        if (clientId.isNotBlank() && aud != null && !aud.contains(clientId)) {
            error("Target Client ID token ($aud) tidak sesuai dengan konfigurasi server ($clientId)")
        }

        GoogleUserProfile(
            email = email,
            name = name,
            googleSubjectId = sub,
            emailVerified = emailVerifiedStr.toBoolean()
        )
    }

    private fun extractJsonField(json: String, field: String): String? {
        val pattern = "\"$field\"\\s*:\\s*\"([^\"]*)\"".toRegex()
        val match = pattern.find(json)
        if (match != null) return match.groupValues[1]

        // Try boolean/number without quotes
        val rawPattern = "\"$field\"\\s*:\\s*([^,\\}\\s]+)".toRegex()
        val rawMatch = rawPattern.find(json)
        return rawMatch?.groupValues?.get(1)
    }
}

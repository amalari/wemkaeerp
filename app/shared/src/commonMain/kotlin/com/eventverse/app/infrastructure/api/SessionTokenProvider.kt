package com.eventverse.app.infrastructure.api

import com.eventverse.app.infrastructure.storage.PlatformLocalStorage
import io.ktor.client.request.*
import io.ktor.http.*

/**
 * Supplies the current session token to API clients.
 *
 * The server authenticates every tenant-scoped route from a signed JWT, so each data
 * request has to carry one. Reading it from the stored session keeps the token out of every
 * client constructor and view model signature.
 */
interface SessionTokenProvider {
    fun currentToken(): String?
}

/** Reads the token from the persisted auth session (browser localStorage and equivalents). */
object StoredSessionTokenProvider : SessionTokenProvider {
    override fun currentToken(): String? =
        AuthApiClient
            .deserializeSession(PlatformLocalStorage.getItem(AuthApiClient.SESSION_STORAGE_KEY))
            ?.token
            ?.value
            ?.takeIf { it.isNotBlank() }
}

/** Fixed token, for tests and for callers that already hold a session. */
class FixedSessionTokenProvider(private val token: String?) : SessionTokenProvider {
    override fun currentToken(): String? = token?.takeIf { it.isNotBlank() }
}

/**
 * Attaches the credentials every tenant-scoped request needs.
 *
 * `X-Tenant-Slug` is still sent, but the server now treats it as an act-as request that
 * only a platform superadmin may make; for everyone else the tenant comes from the token.
 */
internal fun HttpRequestBuilder.tenantRequest(
    tenantSlug: String,
    tokenProvider: SessionTokenProvider
) {
    header("X-Tenant-Slug", tenantSlug)
    tokenProvider.currentToken()?.let { token ->
        header(HttpHeaders.Authorization, "Bearer $token")
    }
}

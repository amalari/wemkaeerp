package com.eventverse.app

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.eventverse.app.domain.auth.Role
import io.ktor.client.request.*
import io.ktor.http.*
import java.util.Date

/**
 * Issues real signed session tokens for API tests.
 *
 * Tenant-scoped routes authenticate from a verified JWT, so a test that only sets
 * `X-Tenant-Slug` is now — correctly — unauthorized. These helpers keep tests describing
 * *who* is calling rather than restating header plumbing.
 *
 * Claims are built here rather than through `JwtTokenService.generateToken`, which takes a
 * `User` and therefore inherits that aggregate's invariant that a tenant-bound role must
 * carry a tenant id. Tests address tenants by slug, so the token deliberately carries
 * `tenant_slug` without `tenant_id` — a shape the plugin must handle anyway.
 */
object TestAuth {

    // Must match JwtTokenService's defaults, since the server verifies with those.
    private val secret: String =
        System.getenv("JWT_SECRET") ?: "wemade-erp-default-development-secret-key-32-chars-long!"
    private const val ISSUER = "wemade-erp"

    private val algorithm: Algorithm = Algorithm.HMAC256(secret)

    private fun sign(
        userId: String,
        role: Role,
        tenantSlug: String?,
        tenantId: String?
    ): String {
        val now = Date()
        return JWT.create()
            .withIssuer(ISSUER)
            .withSubject(userId)
            .withClaim("tenant_id", tenantId)
            .withClaim("tenant_slug", tenantSlug)
            .withClaim("username", "tester")
            .withClaim("email", "tester@wemade.test")
            .withClaim("role", role.name)
            .withIssuedAt(now)
            .withExpiresAt(Date(now.time + 60 * 60 * 1000L))
            .sign(algorithm)
    }

    /** Token for a user bound to a single tenant, addressed by slug. */
    fun tenantToken(
        tenantSlug: String,
        role: Role = Role.TENANT_ADMIN,
        tenantId: String? = null
    ): String = sign(
        userId = "usr-test-$tenantSlug",
        role = role,
        tenantSlug = tenantSlug,
        tenantId = tenantId
    )

    /** Token for a platform superadmin, which is bound to no single tenant. */
    fun superadminToken(): String = sign(
        userId = "usr-test-superadmin",
        role = Role.PLATFORM_SUPERADMIN,
        tenantSlug = null,
        tenantId = null
    )
}

/** Authenticates as a tenant-bound user of [tenantSlug] and targets that same tenant. */
fun HttpRequestBuilder.asTenant(tenantSlug: String, role: Role = Role.TENANT_ADMIN) {
    header("X-Tenant-Slug", tenantSlug)
    header(HttpHeaders.Authorization, "Bearer ${TestAuth.tenantToken(tenantSlug, role)}")
}

/**
 * Authenticates as a tenant-bound user of [tokenSlug] but asks for [targetSlug] — the
 * cross-tenant attempt the plugin must refuse.
 */
fun HttpRequestBuilder.asTenantTargeting(tokenSlug: String, targetSlug: String) {
    header("X-Tenant-Slug", targetSlug)
    header(HttpHeaders.Authorization, "Bearer ${TestAuth.tenantToken(tokenSlug)}")
}

/** Authenticates as a tenant-bound user without naming a tenant: the token decides. */
fun HttpRequestBuilder.asTenantByTokenOnly(tenantSlug: String) {
    header(HttpHeaders.Authorization, "Bearer ${TestAuth.tenantToken(tenantSlug)}")
}

/** Authenticates as a platform superadmin acting on [actAsSlug]. */
fun HttpRequestBuilder.asSuperadminActingAs(actAsSlug: String) {
    header("X-Tenant-Slug", actAsSlug)
    header(HttpHeaders.Authorization, "Bearer ${TestAuth.superadminToken()}")
}

/** Authenticates as a platform superadmin without naming a workspace. */
fun HttpRequestBuilder.asSuperadmin() {
    header(HttpHeaders.Authorization, "Bearer ${TestAuth.superadminToken()}")
}

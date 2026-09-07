package com.eventverse.app.infrastructure.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.interfaces.DecodedJWT
import com.eventverse.app.domain.auth.AuthToken
import com.eventverse.app.domain.auth.User
import java.util.*

/**
 * Service for issuing and verifying WeMade ERP internal JWT session tokens.
 */
class JwtTokenService(
    private val secret: String = System.getenv("JWT_SECRET") ?: "wemade-erp-default-development-secret-key-32-chars-long!",
    private val issuer: String = "wemade-erp",
    private val validityDurationMillis: Long = 7 * 24 * 60 * 60 * 1000L // 7 days
) {
    private val algorithm = Algorithm.HMAC256(secret)
    private val verifier = JWT.require(algorithm)
        .withIssuer(issuer)
        .build()

    fun generateToken(user: User, tenantSlug: String? = null): AuthToken {
        val now = Date()
        val expiresAt = Date(now.time + validityDurationMillis)

        val tokenString = JWT.create()
            .withIssuer(issuer)
            .withSubject(user.id.value)
            .withClaim("tenant_id", user.tenantId?.value)
            .withClaim("tenant_slug", tenantSlug)
            .withClaim("username", user.username.value)
            .withClaim("email", user.email.value)
            .withClaim("role", user.role.name)
            .withClaim("permissions", user.effectivePermissions.map { it.name })
            .withIssuedAt(now)
            .withExpiresAt(expiresAt)
            .sign(algorithm)

        return AuthToken(tokenString)
    }

    fun verifyToken(token: String): Result<DecodedJWT> = runCatching {
        verifier.verify(token)
    }
}

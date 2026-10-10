package com.eventverse.app.infrastructure.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.interfaces.DecodedJWT
import com.eventverse.app.domain.auth.AuthToken
import com.eventverse.app.domain.auth.Role
import com.eventverse.app.domain.auth.User
import java.util.*

/**
 * Service for issuing and verifying WeMade ERP internal JWT session tokens.
 */
class JwtTokenService(
    secret: String = System.getenv("JWT_SECRET") ?: "wemade-erp-default-development-secret-key-32-chars-long!",
    private val issuer: String = "wemade-erp",
    private val validityDurationMillis: Long = 7 * 24 * 60 * 60 * 1000L // 7 days
) {
    private val algorithm = Algorithm.HMAC256(secret)
    private val verifier = JWT.require(algorithm)
        .withIssuer(issuer)
        .build()

    fun generateToken(user: User, tenantSlug: String? = null): AuthToken {
        // Token tenant-bound tanpa slug ditolak saat penerbitan: gerbang tenant menolaknya di sisi
        // baca, jadi menerbitkannya hanya menghasilkan sesi rusak yang gagal jauh dari sebabnya.
        require(user.role == Role.PLATFORM_SUPERADMIN || !tenantSlug.isNullOrBlank()) {
            "Slug tenant wajib untuk token akun tenant (user ${user.id.value})"
        }
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
            // Identitas yang dikonfigurasi tenant. Dibawa terpisah dari `role` karena `role` adalah
            // enum tetap: menumpangkan id jabatan rakitan tenant di sana membuat `Role.valueOf`
            // gagal di sisi baca, dan kegagalan itu ditelan menjadi TENANT_ADMIN — yaitu melebarkan
            // wewenang justru ketika identitasnya tidak dikenali.
            .withClaim("department_id", user.departmentId)
            .withClaim("custom_role_id", user.customRoleId)
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

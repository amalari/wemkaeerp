package com.eventverse.app.infrastructure.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.eventverse.app.domain.tenant.TenantSlug
import java.util.Date
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Tiket sekali pakai untuk memindahkan sesi dari `app.<base>` ke `<slug>.<base>`
 * (PLAN-builder-console §2, discovery-M3-login-split).
 *
 * Sesi klien tinggal di localStorage, yang terikat per origin — login di `app.` tidak terlihat di
 * `bordir.`. Tiket ini jembatannya: diterbitkan di `app.` untuk satu user + satu tenant, dibawa lewat
 * redirect, lalu ditukar menjadi token sesi biasa di subdomain tujuan.
 *
 * Pengamannya, meniru [PrintTicketService]:
 * - **issuer berbeda** dari token sesi, jadi tiket yang bocor dari URL tidak pernah diterima sebagai sesi;
 * - **umur pendek** ([validityMillis]);
 * - **terikat tenant**: ditolak bila host penukar menunjuk tenant lain;
 * - **sekali pakai** — berbeda dari tiket cetak, karena tiket ini menghasilkan sesi penuh. `jti` yang
 *   sudah ditukar diingat in-memory sampai kedaluwarsa. Konsekuensinya: dengan lebih dari satu instance
 *   server, anti-replay hanya berlaku per instance — pindahkan ke tabel bila server diskalakan.
 */
class SessionHandoffTicketService(
    secret: String = System.getenv("JWT_SECRET") ?: "wemade-erp-default-development-secret-key-32-chars-long!",
    private val validityMillis: Long = 60_000L,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private val algorithm = Algorithm.HMAC256(secret)
    private val verifier = JWT.require(algorithm).withIssuer(ISSUER).build()

    /** jti → waktu kedaluwarsa (epoch millis). */
    private val redeemed = ConcurrentHashMap<String, Long>()

    fun issue(userId: String, tenantSlug: TenantSlug): String {
        val now = clock()
        return JWT.create()
            .withIssuer(ISSUER)
            .withSubject(userId)
            .withJWTId(UUID.randomUUID().toString())
            .withClaim(CLAIM_TENANT_SLUG, tenantSlug.value)
            .withIssuedAt(Date(now))
            .withExpiresAt(Date(now + validityMillis))
            .sign(algorithm)
    }

    /**
     * Menukar tiket. `null` bila tiket tidak sah, kedaluwarsa, sudah dipakai, atau [hostTenant]
     * (tenant dari host penukar; `null` = host lokal/dev) bukan tenant tiket.
     */
    fun redeem(ticket: String, hostTenant: TenantSlug?): HandoffIdentity? {
        val decoded = runCatching { verifier.verify(ticket) }.getOrNull() ?: return null
        val expiresAt = decoded.expiresAt?.time ?: return null
        if (expiresAt <= clock()) return null

        val userId = decoded.subject?.takeIf { it.isNotBlank() } ?: return null
        val slug = decoded.getClaim(CLAIM_TENANT_SLUG).asString()
            ?.let { runCatching { TenantSlug(it) }.getOrNull() } ?: return null
        if (hostTenant != null && hostTenant != slug) return null

        val jti = decoded.id?.takeIf { it.isNotBlank() } ?: return null
        pruneExpired()
        if (redeemed.putIfAbsent(jti, expiresAt) != null) return null

        return HandoffIdentity(userId, slug)
    }

    private fun pruneExpired() {
        val now = clock()
        redeemed.entries.removeIf { it.value <= now }
    }

    private companion object {
        const val ISSUER = "wemade-erp-handoff"
        const val CLAIM_TENANT_SLUG = "tenant_slug"
    }
}

data class HandoffIdentity(val userId: String, val tenantSlug: TenantSlug)

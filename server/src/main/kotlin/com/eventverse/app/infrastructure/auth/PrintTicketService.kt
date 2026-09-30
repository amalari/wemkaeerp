package com.eventverse.app.infrastructure.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.interfaces.DecodedJWT
import com.eventverse.app.domain.tenant.TenantId
import java.util.*

/**
 * Tiket berumur pendek untuk membuka PDF cetak di tab browser.
 *
 * Tab browser tidak bisa mengirim header `Authorization`, jadi PDF yang dibuka lewat tautan biasa
 * selalu ditolak 401 — termasuk untuk superadmin. Tiket ini menggantikan header itu **hanya** untuk
 * satu work order, hanya untuk berkas `.pdf`, dan hanya ±60 detik.
 *
 * Issuer-nya sengaja berbeda dari token sesi: verifier [JwtTokenService] mewajibkan issuer
 * `wemade-erp`, sehingga tiket yang bocor dari URL/history tidak pernah bisa dipakai sebagai sesi.
 *
 * Tiket tidak dibuat sekali-pakai karena penampil PDF browser boleh meminta ulang URL yang sama
 * (range request, muat ulang); umur pendek dan cakupan sempit yang menjadi pengamannya.
 */
class PrintTicketService(
    secret: String = System.getenv("JWT_SECRET") ?: "wemade-erp-default-development-secret-key-32-chars-long!",
    private val validityMillis: Long = 60_000L
) {
    private val algorithm = Algorithm.HMAC256(secret)
    private val verifier = JWT.require(algorithm).withIssuer(ISSUER).build()

    /** [scopePath] adalah prefiks path yang boleh dibuka, mis. `/api/tenant/traceability/work-orders/SAMPLING/x`. */
    fun issue(subject: String, tenantId: TenantId, scopePath: String, platformSuperadmin: Boolean = false): String {
        val now = Date()
        return JWT.create()
            .withIssuer(ISSUER)
            .withSubject(subject)
            .withClaim(CLAIM_TENANT, tenantId.value)
            .withClaim(CLAIM_SCOPE, scopePath)
            .withClaim(CLAIM_PLATFORM_SUPERADMIN, platformSuperadmin)
            .withIssuedAt(now)
            .withExpiresAt(Date(now.time + validityMillis))
            .sign(algorithm)
    }

    /** Tenant tiket, atau `null` bila tiket tidak sah, kedaluwarsa, atau bukan untuk [requestPath]. */
    fun verify(ticket: String, requestPath: String): TenantId? {
        val decoded = decode(ticket, requestPath) ?: return null
        return decoded.getClaim(CLAIM_TENANT).asString()?.takeIf { it.isNotBlank() }?.let(::TenantId)
    }

    /**
     * Pemilik tiket, atau `null` bila tiket tidak sah/kedaluwarsa/bukan untuk [requestPath].
     *
     * Dipakai rute platform yang gerbangnya **kepemilikan dokumen**, bukan keanggotaan tenant: draf
     * discovery milik satu pengguna (T12), dan tab browser yang membukanya tidak bisa membawa Bearer.
     * `isPlatformSuperadmin` ikut dibawa supaya superadmin yang boleh membaca draf orang lain di API
     * juga boleh mengunduhnya lewat tautan — dulu gerbang yang sama berbunyi sama di dua tempat.
     */
    fun verifyUser(ticket: String, requestPath: String): PrintTicketUser? {
        val decoded = decode(ticket, requestPath) ?: return null
        val userId = decoded.subject?.takeIf { it.isNotBlank() } ?: return null
        return PrintTicketUser(userId, decoded.getClaim(CLAIM_PLATFORM_SUPERADMIN).asBoolean() ?: false)
    }

    private fun decode(ticket: String, requestPath: String): DecodedJWT? {
        val decoded = runCatching { verifier.verify(ticket) }.getOrNull() ?: return null
        val scope = decoded.getClaim(CLAIM_SCOPE).asString()?.takeIf { it.isNotBlank() } ?: return null
        if (!requestPath.endsWith(".pdf") || !requestPath.startsWith("$scope/")) return null
        return decoded
    }

    companion object {
        const val QUERY_PARAM = "ticket"
        private const val ISSUER = "wemade-erp-print"
        private const val CLAIM_TENANT = "tenant_id"
        private const val CLAIM_SCOPE = "scope"
        private const val CLAIM_PLATFORM_SUPERADMIN = "platform_superadmin"
    }
}

/**
 * Identitas yang dibawa tiket cetak pada rute per-pengguna.
 *
 * Sengaja bukan `CallerPrincipal`: tiket tidak membawa peran RBAC maupun divisi, dan membuatnya
 * tampak seolah membawa akan mengundang pemeriksaan wewenang yang salah di rute baru.
 */
data class PrintTicketUser(val userId: String, val isPlatformSuperadmin: Boolean)


package com.eventverse.app.routes

import com.eventverse.app.domain.auth.Role
import com.eventverse.app.domain.auth.UserId
import com.eventverse.app.domain.auth.UserRepository
import com.eventverse.app.domain.tenant.HostSurface
import com.eventverse.app.domain.tenant.TenantRepository
import com.eventverse.app.infrastructure.auth.JwtTokenService
import com.eventverse.app.infrastructure.auth.SessionHandoffTicketService
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.header
import io.ktor.server.request.host
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route

/**
 * Serah-terima sesi `app.<base>` → `<slug>.<base>` (discovery-M3-login-split).
 *
 * - `POST /issue` — dipanggil di `app.` dengan token sesi; menerbitkan tiket sekali pakai untuk tenant
 *   **milik akun itu sendiri**. Superadmin tidak di-handoff: ia tetap di platform dan masuk tenant
 *   lewat act-as (`X-Tenant-Slug`).
 * - `POST /` — dipanggil di subdomain tujuan; menukar tiket menjadi token sesi biasa. Ditolak di
 *   permukaan platform, dan ditolak bila host menunjuk tenant lain dari tiket.
 *
 * Keduanya di bawah `/api/public` (tanpa `TenantResolutionPlugin`) karena penukar belum punya sesi;
 * karena itu setiap pemeriksaan identitas dilakukan eksplisit di sini, fail-closed.
 */
fun Route.sessionHandoffRoutes(
    ticketService: SessionHandoffTicketService,
    jwtTokenService: JwtTokenService,
    tenantRepository: TenantRepository,
    userRepo: UserRepository,
    platformBaseDomain: String?
) {
    route("/api/public/auth/handoff") {
        post("/issue") {
            val token = call.request.header("Authorization")?.removePrefix("Bearer ")?.trim().orEmpty()
            val jwt = token.takeIf { it.isNotBlank() }
                ?.let { jwtTokenService.verifyToken(it).getOrNull() }
            val user = jwt?.subject?.takeIf { it.isNotBlank() }?.let { userRepo.findById(UserId(it)) }
            if (user == null || !user.isActive) {
                call.respond(HttpStatusCode.Unauthorized, "Sesi tidak sah")
                return@post
            }
            if (user.role == Role.PLATFORM_SUPERADMIN) {
                call.respond(HttpStatusCode.BadRequest, "Superadmin tetap di platform; masuk tenant lewat act-as")
                return@post
            }
            val tenant = user.tenantId?.let { tenantRepository.findById(it) }
            if (tenant == null || !tenant.isAccessible) {
                call.respond(HttpStatusCode.Forbidden, "Perusahaan untuk akun ini tidak dapat diakses")
                return@post
            }

            val ticket = ticketService.issue(user.id.value, tenant.slug)
            val origin = platformBaseDomain?.takeIf { it.isNotBlank() }
                ?.let { "\"${HostSurface.tenantOrigin(tenant.slug, it)}\"" } ?: "null"
            call.respondText(
                "{\"ticket\":\"$ticket\",\"tenantSlug\":\"${tenant.slug.value}\",\"origin\":$origin}",
                contentType = ContentType.Application.Json
            )
        }

        post {
            val surface = HostSurface.parse(call.request.host(), platformBaseDomain)
            if (surface is HostSurface.Platform) {
                call.respond(HttpStatusCode.Forbidden, "Tiket hanya dapat ditukar di subdomain tenant")
                return@post
            }
            val ticket = runCatching { call.receiveParameters()["ticket"] }.getOrNull().orEmpty()
            val identity = ticket.takeIf { it.isNotBlank() }
                ?.let { ticketService.redeem(it, (surface as? HostSurface.Tenant)?.slug) }
            if (identity == null) {
                call.respond(HttpStatusCode.Unauthorized, "Tiket tidak sah, kedaluwarsa, atau sudah dipakai")
                return@post
            }

            // Baca ulang dari DB: tiket berumur 60 detik, tetapi akun/tenant bisa dinonaktifkan di antaranya.
            val user = userRepo.findById(UserId(identity.userId))
            val tenant = tenantRepository.findBySlug(identity.tenantSlug)
            if (user == null || !user.isActive || tenant == null || !tenant.isAccessible || user.tenantId != tenant.id) {
                call.respond(HttpStatusCode.Forbidden, "Akun atau perusahaan tidak lagi dapat diakses")
                return@post
            }

            val sessionToken = jwtTokenService.generateToken(user, tenant.slug.value)
            call.respondText(
                authSessionJson(user, sessionToken.value, tenant.slug.value),
                contentType = ContentType.Application.Json
            )
        }
    }
}

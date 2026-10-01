package com.eventverse.app.routes

import com.eventverse.app.domain.audit.AuditAction
import com.eventverse.app.domain.audit.AuditLogEntry
import com.eventverse.app.domain.audit.AuditLogRepository
import com.eventverse.app.domain.auth.Role
import kotlinx.datetime.Clock
import com.eventverse.app.domain.auth.UserId
import com.eventverse.app.domain.auth.UserRepository
import com.eventverse.app.domain.tenant.HostSurface
import com.eventverse.app.domain.tenant.TenantRepository
import com.eventverse.app.domain.tenant.TenantSlug
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
    platformBaseDomain: String?,
    auditLogRepository: AuditLogRepository
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
            // discovery-M3b: `actAs=<slug>` = superadmin masuk tenant orang lain. Hanya superadmin; owner
            // yang mengirimnya ditolak, bukan diabaikan diam-diam.
            val actAsSlug = runCatching { call.receiveParameters()["actAs"] }.getOrNull()?.trim()?.ifBlank { null }
            val isSuperadmin = user.role == Role.PLATFORM_SUPERADMIN
            if (actAsSlug != null && !isSuperadmin) {
                call.respond(HttpStatusCode.Forbidden, "Hanya platform superadmin yang boleh masuk ke tenant lain")
                return@post
            }
            if (isSuperadmin && actAsSlug == null) {
                call.respond(HttpStatusCode.BadRequest, "Superadmin wajib menyebut tenant tujuan (actAs)")
                return@post
            }
            val tenant = if (actAsSlug != null) runCatching { TenantSlug(actAsSlug) }.getOrNull()?.let { tenantRepository.findBySlug(it) }
            else user.tenantId?.let { tenantRepository.findById(it) }
            if (tenant == null || !tenant.isAccessible) {
                call.respond(HttpStatusCode.Forbidden, "Perusahaan untuk akun ini tidak dapat diakses")
                return@post
            }

            if (actAsSlug != null) {
                auditLogRepository.record(
                    AuditLogEntry(
                        id = "audit-${tenant.id.value}-actas-${Clock.System.now().toEpochMilliseconds()}",
                        actorUserId = user.id.value,
                        actorRole = user.role,
                        targetTenantId = tenant.id,
                        action = AuditAction.PLATFORM_ACT_AS_STARTED,
                        summary = "Superadmin ${user.username.value} masuk ke workspace ${tenant.slug.value}",
                        occurredAt = Clock.System.now()
                    )
                ).onFailure {
                    // Fail-closed: act-as tanpa jejak melanggar syarat yang membuatnya diizinkan.
                    call.respond(HttpStatusCode.ServiceUnavailable, "Audit act-as gagal dicatat; masuk dibatalkan")
                    return@post
                }
            }

            val ticket = ticketService.issue(user.id.value, tenant.slug, actAs = actAsSlug != null)
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
            val allowed = user != null && user.isActive && tenant != null && tenant.isAccessible &&
                if (identity.actAs) user.role == Role.PLATFORM_SUPERADMIN else user.tenantId == tenant.id
            if (!allowed || user == null || tenant == null) {
                call.respond(HttpStatusCode.Forbidden, "Akun atau perusahaan tidak lagi dapat diakses")
                return@post
            }

            // Act-as: sesi superadmin ditambatkan ke tenant tujuan (tidak disimpan ke DB).
            val sessionUser = if (identity.actAs) user.copy(tenantId = tenant.id) else user
            val sessionToken = jwtTokenService.generateToken(sessionUser, tenant.slug.value)
            call.respondText(
                authSessionJson(sessionUser, sessionToken.value, tenant.slug.value),
                contentType = ContentType.Application.Json
            )
        }
    }
}

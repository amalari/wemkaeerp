package com.eventverse.app.routes

import com.eventverse.app.domain.audit.AuditAction
import com.eventverse.app.domain.audit.AuditLogEntry
import com.eventverse.app.domain.audit.AuditLogRepository
import com.eventverse.app.domain.auth.UserId
import com.eventverse.app.domain.auth.UserRepository
import com.eventverse.app.domain.tenant.TenantRepository
import com.eventverse.app.domain.tenant.TenantSlug
import com.eventverse.app.infrastructure.auth.JwtTokenService
import com.eventverse.app.plugins.callerPrincipalOrNull
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import kotlinx.datetime.Clock

/**
 * `POST /api/admin/tenants/{slug}/act-as` — superadmin masuk Builder tenant **tanpa pindah origin**.
 *
 * Builder hidup di `app.<base>`; subdomain `<slug>.<base>` hanya untuk aplikasi hasil generate. Jadi act-as
 * Builder tidak lagi memakai tiket handoff lintas-origin (yang memuat ulang seluruh bundel WASM), melainkan
 * menerbitkan sesi yang ditambatkan ke tenant tujuan langsung di origin `app.`.
 *
 * Gerbang superadmin datang dari `TenantResolutionPlugin.platformRoutePrefixes` (`/api/admin`); pemeriksaan
 * ulang di sini membuatnya tetap fail-closed bila route ini suatu hari dipasang di luar prefiks itu.
 * Audit `PLATFORM_ACT_AS_STARTED` wajib tercatat sebelum token terbit — gagal mencatat = masuk dibatalkan,
 * sama seperti jalur handoff. Sesi tidak disimpan ke DB; hanya JWT-nya yang memuat tenant tujuan.
 */
fun Route.platformActAsRoutes(
    tenantRepository: TenantRepository,
    userRepository: UserRepository,
    jwtTokenService: JwtTokenService,
    auditLogRepository: AuditLogRepository
) {
    post("/api/admin/tenants/{slug}/act-as") {
        val principal = call.callerPrincipalOrNull
        if (principal == null || !principal.isPlatformSuperadmin) {
            call.respond(HttpStatusCode.Forbidden, "Hanya platform superadmin yang boleh masuk ke tenant lain")
            return@post
        }
        val user = userRepository.findById(UserId(principal.userId))
        if (user == null || !user.isActive) {
            call.respond(HttpStatusCode.Unauthorized, "Sesi tidak sah")
            return@post
        }
        val tenant = call.parameters["slug"]
            ?.let { raw -> runCatching { TenantSlug(raw.trim().lowercase()) }.getOrNull() }
            ?.let { tenantRepository.findBySlug(it) }
        if (tenant == null) {
            call.respond(HttpStatusCode.NotFound, "Tenant tidak ditemukan")
            return@post
        }

        val now = Clock.System.now()
        auditLogRepository.record(
            AuditLogEntry(
                id = "audit-${tenant.id.value}-actas-${now.toEpochMilliseconds()}",
                actorUserId = user.id.value,
                actorRole = user.role,
                targetTenantId = tenant.id,
                action = AuditAction.PLATFORM_ACT_AS_STARTED,
                summary = "Superadmin ${user.username.value} masuk ke workspace ${tenant.slug.value}",
                occurredAt = now
            )
        ).onFailure {
            call.respond(HttpStatusCode.ServiceUnavailable, "Audit act-as gagal dicatat; masuk dibatalkan")
            return@post
        }

        val sessionUser = user.copy(tenantId = tenant.id)
        val token = jwtTokenService.generateToken(sessionUser, tenant.slug.value)
        call.respondText(
            authSessionJson(sessionUser, token.value, tenant.slug.value),
            contentType = ContentType.Application.Json
        )
    }
}

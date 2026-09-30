package com.eventverse.app.routes

import com.eventverse.app.domain.auth.Permission
import com.eventverse.app.domain.auth.Role
import com.eventverse.app.domain.builder.BuilderDeploymentRepository
import com.eventverse.app.domain.builder.Deployment
import com.eventverse.app.domain.tenant.TenantRepository
import com.eventverse.app.plugins.callerPrincipalOrNull
import com.eventverse.app.plugins.tenantContext
import com.eventverse.app.plugins.tenantContextOrNull
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route

/**
 * Endpoint Builder M0 (PLAN-builder-console §6): baca saja — overview & riwayat deployment.
 * Tulis (deploy/rollback/chat) menyusul di M1/M2 dengan pola gate yang sama.
 *
 * Gerbang (fail-closed, Kontrak 7): keputusan wewenang yang tidak bisa dihitung = 403. M0 hanya peran
 * platform yang membawa [Permission.MANAGE_BUILDER] (TENANT_ADMIN, superadmin) — pemberian izin ke
 * kolaborator via RBAC tenant menyusul saat mesin keputusan non-modul tersedia; sampai saat itu
 * kolaborator ditolak, bukan dilayani.
 */
fun Route.builderRoutes(
    tenants: TenantRepository,
    deployments: BuilderDeploymentRepository
) {
    route("/api/builder") {
        get("/overview") {
            call.gate() ?: return@get
            val tenant = call.tenantContext
            val tenantRow = tenants.findById(tenant.tenantId)
            val history = deployments.findByTenant(tenant.tenantId)
            val active = history.firstOrNull { it.isActive }
            call.respondText(
                buildString {
                    append("{\"tenant\":{\"slug\":\"${tenant.slug.value}\",\"name\":\"")
                    append(tenantRow?.name?.value ?: tenant.slug.value)
                    append("\",\"status\":\"${tenantRow?.status?.name ?: "UNKNOWN"}\",")
                    append("\"tier\":\"${tenantRow?.tier?.name}\",")
                    append("\"domainPack\":\"${tenant.domainPack.value}\",")
                    append("\"domainPackVersion\":${tenantRow?.domainPackVersion ?: "null"}},")
                    append("\"activeDeployment\":${active?.let(::deploymentJson) ?: "null"},")
                    append("\"deployments\":[")
                    append(history.joinToString(",") { deploymentJson(it) })
                    append("]}")
                },
                ContentType.Application.Json
            )
        }
    }
}

private fun deploymentJson(d: com.eventverse.app.domain.builder.Deployment): String = buildString {
    append("{\"number\":${d.number.value}")
    append(",\"status\":\"${d.status.name}\"")
    append(",\"packCode\":\"${d.packCode.value}\"")
    append(",\"packVersion\":${d.packVersion ?: "null"}")
    append(",\"appBuild\":${d.appBuild?.let { "\"$it\"" } ?: "null"}")
    append(",\"blueprintRevision\":${d.blueprintRevision}")
    append(",\"createdAt\":${d.createdAt?.toString()?.let { "\"$it\"" } ?: "null"}")
    append("}")
}

/**
 * Gerbang bersama semua endpoint builder: tenant context wajib (plugin resolusi) + peran pembawa
 * [Permission.MANAGE_BUILDER]. Dipisah murni supaya aturan keamanannya teruji tanpa HTTP.
 * Fail-closed: `null` = tolak 403.
 */
internal suspend fun ApplicationCall.gate(): ApplicationCall? {
    val principal = callerPrincipalOrNull
    if (!mayOpenBuilder(principal?.role)) {
        respond(HttpStatusCode.Forbidden, "Butuh izin Builder (MANAGE_BUILDER) untuk membuka konsol ini.")
        return null
    }
    if (tenantContextOrNull == null) {
        respond(HttpStatusCode.NotFound, "No tenant context found")
        return null
    }
    return this
}

/** Aturan fail-closed, teruji tanpa HTTP (pola `mayEditWithoutDecision`). */
internal fun mayOpenBuilder(role: Role?): Boolean =
    role != null && (role == Role.PLATFORM_SUPERADMIN || role.defaultPermissions.contains(Permission.MANAGE_BUILDER))

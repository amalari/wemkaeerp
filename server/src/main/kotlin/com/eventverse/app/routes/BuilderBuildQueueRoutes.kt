package com.eventverse.app.routes

import com.eventverse.app.domain.auth.Role
import com.eventverse.app.domain.builder.BuilderBuildRequestRepository
import com.eventverse.app.domain.builder.BuildRequestStatus
import com.eventverse.app.plugins.callerPrincipalOrNull
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

/**
 * Antrian Pembuatan tipis (FR-M2-4, plan §6): dikelola superadmin, bukan self-service tenant.
 * - `GET  /api/builder/build-queue`             — seluruh request lintas tenant.
 * - `POST /api/builder/build-queue/{id}/status` — ubah status (`?status=IN_PROGRESS|SHIPPED|...`).
 *
 * Fail-closed: tanpa sesi → 401; selain [Role.PLATFORM_SUPERADMIN] → 403. Route ini sengaja
 * terpisah dari gate tenant (`mayOpenBuilder`) karena justru sebaliknya: hanya platform.
 */
fun Route.builderBuildQueueRoutes(buildRequests: BuilderBuildRequestRepository) {
    route("/api/builder/build-queue") {
        get {
            call.superadminGate() ?: return@get
            val queue = buildRequests.findAll()
            call.respondText(
                "{\"requests\":[" + queue.joinToString(",") { r ->
                    "{\"id\":\"${r.id.value}\",\"tenantId\":\"${r.tenantId.value}\",\"moduleId\":\"${r.moduleId}\"," +
                        "\"status\":\"${r.status.name}\",\"reason\":\"${r.reason.replace("\"", "'")}\"" +
                        (r.quoteId?.let { ",\"quoteId\":\"$it\"" } ?: "") + "}"
                } + "]}",
                ContentType.Application.Json
            )
        }

        post("/{id}/status") {
            call.superadminGate() ?: return@post
            val id = call.parameters["id"] ?: ""
            val statusName = call.request.queryParameters["status"] ?: ""
            val status = runCatching { BuildRequestStatus.valueOf(statusName.uppercase()) }.getOrNull()
            if (status == null) {
                call.respond(HttpStatusCode.BadRequest, "status tidak dikenal: '$statusName'")
                return@post
            }
            val existing = buildRequests.findAll().firstOrNull { it.id.value == id }
            if (existing == null) {
                call.respond(HttpStatusCode.NotFound, "Build request '$id' tidak ditemukan")
                return@post
            }
            buildRequests.save(existing.copy(status = status))
            call.respondText(
                "{\"id\":\"$id\",\"status\":\"${status.name}\"}",
                ContentType.Application.Json
            )
        }
    }
}

/** Gate konsol superadmin: 401 tanpa sesi, 403 selain PLATFORM_SUPERADMIN. Fail-closed. */
internal suspend fun ApplicationCall.superadminGate(): ApplicationCall? {
    val principal = callerPrincipalOrNull
    if (principal == null) {
        respond(HttpStatusCode.Unauthorized, "Autentikasi diperlukan")
        return null
    }
    if (principal.role != Role.PLATFORM_SUPERADMIN) {
        respond(HttpStatusCode.Forbidden, "Hanya superadmin platform")
        return null
    }
    return this
}

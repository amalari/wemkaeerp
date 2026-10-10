package com.eventverse.app.routes

import com.eventverse.app.domain.audit.AuditAction
import com.eventverse.app.domain.audit.AuditLogEntry
import com.eventverse.app.domain.audit.AuditLogRepository
import com.eventverse.app.domain.auth.Role
import com.eventverse.app.domain.builder.BuilderBuildRequestRepository
import com.eventverse.app.domain.builder.DeployTenantUseCase
import com.eventverse.app.domain.builder.DeploymentStatus
import com.eventverse.app.domain.builder.RollbackDeploymentUseCase
import com.eventverse.app.domain.pack.usecases.TenantOperationalDataProbe
import com.eventverse.app.plugins.callerPrincipalOrNull
import com.eventverse.app.plugins.tenantContext
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.datetime.Clock

/**
 * Endpoint deployment Builder (FR-M2-1/2/3, plan §6 — agregat terpisah dari chat):
 * - `POST /api/builder/deployments`          — deploy (kunci draf + pin versi + aktifkan).
 * - `POST /api/builder/deployments/rollback` — pin versi sebelumnya; gerbang data → 409.
 * - `GET  /api/builder/deployments`          — riwayat (append-only).
 *
 * Gerbang fail-closed sama dengan chat ([mayOpenBuilder]); activate/rollback **wajib ter-audit**
 * (TRD NFR: deploy = aksi berisiko). Rollback dengan `force` (arsip modul eksplisit) tetap tercatat.
 */
fun Route.builderDeploymentRoutes(
    drafts: com.eventverse.app.domain.discovery.DiscoveryDraftRepository,
    deployments: com.eventverse.app.domain.builder.BuilderDeploymentRepository,
    buildRequests: BuilderBuildRequestRepository,
    tenants: com.eventverse.app.domain.tenant.TenantRepository,
    probe: TenantOperationalDataProbe,
    auditLog: AuditLogRepository,
    /** Riwayat chat: sumber konteks brief yang dibekukan pada tiap permintaan pembuatan (opsi B). */
    chats: com.eventverse.app.domain.builder.BuilderChatRepository
) {
    val deploy = DeployTenantUseCase(drafts, deployments, buildRequests, tenants, briefs = com.eventverse.app.domain.builder.BuildRequestBriefs(chats))
    val rollback = RollbackDeploymentUseCase(deployments, probe)

    route("/api/builder/deployments") {
        get {
            call.gate() ?: return@get
            val history = deployments.findByTenant(call.tenantContext.tenantId)
            val queued = buildRequests.findByTenant(call.tenantContext.tenantId)
            call.respondText(
                buildString {
                    append("{\"deployments\":[")
                    append(history.joinToString(",") { deploymentJson(it) })
                    append("],\"buildRequests\":[")
                    append(queued.joinToString(",") { r ->
                        "{\"id\":\"${r.id.value}\",\"moduleId\":\"${r.moduleId}\",\"status\":\"${r.status.name}\"}"
                    })
                    append("]}")
                },
                ContentType.Application.Json
            )
        }

        post {
            call.gate() ?: return@post
            val tenant = call.tenantContext
            deploy(tenant.tenantId).fold(
                onSuccess = { d ->
                    call.audit(auditLog, if (d.status == DeploymentStatus.BLOCKED_ON_BUILD) "deploy #${d.number.value} BLOCKED_ON_BUILD" else "deploy #${d.number.value} ACTIVE v${d.packVersion}")
                    call.respondText(deploymentJson(d), ContentType.Application.Json)
                },
                onFailure = { e -> call.respond(HttpStatusCode.Conflict, "Deploy gagal: ${e.message}") }
            )
        }

        post("/rollback") {
            call.gate() ?: return@post
            val tenant = call.tenantContext
            val force = call.request.queryParameters["force"] == "true"
            rollback(tenant.tenantId, force).fold(
                onSuccess = { d ->
                    call.audit(auditLog, "rollback -> pin ${d.packVersion?.let { "v$it" } ?: "rev ${d.blueprintRevision}"}${if (force) " (force)" else ""}")
                    call.respondText(deploymentJson(d), ContentType.Application.Json)
                },
                onFailure = { e ->
                    call.respond(HttpStatusCode.Conflict, "Rollback gagal: ${e.message}")
                }
            )
        }
    }
}

private suspend fun ApplicationCall.audit(auditLog: AuditLogRepository, summary: String) {
    val principal = callerPrincipalOrNull ?: return
    runCatching {
        auditLog.record(
            AuditLogEntry(
                id = "audit-${tenantContext.tenantId.value}-${Clock.System.now().toEpochMilliseconds()}",
                actorUserId = principal.userId,
                actorRole = principal.role,
                targetTenantId = tenantContext.tenantId,
                action = if (summary.startsWith("rollback")) AuditAction.BUILDER_DEPLOYMENT_ROLLED_BACK
                else AuditAction.BUILDER_DEPLOYMENT_ACTIVATED,
                summary = summary,
                occurredAt = Clock.System.now()
            )
        )
    }
}

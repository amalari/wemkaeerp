package com.eventverse.app.routes

import com.eventverse.app.domain.auth.Permission
import com.eventverse.app.domain.auth.Role
import com.eventverse.app.domain.builder.ApplyDraftPatchUseCase
import com.eventverse.app.domain.builder.BuilderBuildRequestRepository
import com.eventverse.app.domain.builder.BuilderAgent
import com.eventverse.app.domain.builder.BuilderChatRepository
import com.eventverse.app.domain.builder.BuilderDeploymentRepository
import com.eventverse.app.domain.builder.ChatMessage
import com.eventverse.app.domain.builder.ChatMessageId
import com.eventverse.app.domain.builder.Deployment
import com.eventverse.app.domain.builder.SendBuilderMessageUseCase
import com.eventverse.app.domain.discovery.DiscoveryDraftRepository
import com.eventverse.app.domain.tenant.TenantRepository
import com.eventverse.app.plugins.callerPrincipalOrNull
import com.eventverse.app.plugins.tenantContext
import com.eventverse.app.plugins.tenantContextOrNull
import com.eventverse.app.shared.discovery.DiscoveryDraftCodec
import com.eventverse.app.shared.json.JsonParser
import com.eventverse.app.shared.json.JsonValue
import com.eventverse.app.shared.json.jsonArrayOf
import com.eventverse.app.shared.json.jsonObjectOf
import com.eventverse.app.shared.json.jsonOf
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

/**
 * Endpoint Builder (PLAN-builder-console §6). M0: overview & riwayat deployment (baca saja).
 * M1: chat tersimpan + usulan patch (`propose_patch`) + draf kerja tenant untuk pane
 * Modules/Data Flow/Prototype.
 *
 * Gerbang (fail-closed, Kontrak 7): keputusan wewenang yang tidak bisa dihitung = 403. "Terapkan"
 * adalah aksi manusia — agent tidak pernah menulis draf; patch usulan divalidasi ulang oleh
 * [ApplyDraftPatchUseCase] saat diterapkan, bukan saat agent mengusulkannya.
 */
fun Route.builderRoutes(
    tenants: TenantRepository,
    deployments: BuilderDeploymentRepository,
    chats: BuilderChatRepository,
    agent: BuilderAgent,
    drafts: DiscoveryDraftRepository,
    buildRequests: BuilderBuildRequestRepository =
        com.eventverse.app.infrastructure.PostgresBuilderBuildRequestRepository(),
    probe: com.eventverse.app.domain.pack.usecases.TenantOperationalDataProbe =
        com.eventverse.app.infrastructure.PostgresTenantOperationalDataProbe(),
    auditLog: com.eventverse.app.domain.audit.AuditLogRepository =
        com.eventverse.app.infrastructure.PostgresAuditLogRepository(),
    billingInvoices: com.eventverse.app.domain.builder.SubscriptionInvoiceRepository =
        com.eventverse.app.infrastructure.PostgresSubscriptionInvoiceRepository(),
    billingPreview: com.eventverse.app.domain.builder.TenantBillingPreviewSource =
        com.eventverse.app.domain.builder.TenantBillingPreviewSource { tenantId ->
            com.eventverse.app.domain.moduledev.usecases.GetTenantBillingPreviewUseCase(
                com.eventverse.app.infrastructure.PostgresTenantPipelineRepository(),
                com.eventverse.app.infrastructure.PostgresModuleCatalogRepository(),
                com.eventverse.app.infrastructure.PostgresModulePricingQuoteRepository()
            )(tenantId)
        }
) {
    val send = SendBuilderMessageUseCase(chats, agent, drafts)
    val apply = ApplyDraftPatchUseCase(chats, drafts)
    // Agregat deployment & billing terpisah (plan §6); dipasang di sini supaya Application.kt tidak bertambah.
    builderDeploymentRoutes(drafts, deployments, buildRequests, tenants, probe, auditLog)
    builderBuildQueueRoutes(buildRequests)
    builderBillingRoutes(billingInvoices, billingPreview, auditLog)
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

        // Draf kerja tenant untuk pane Modules/Data Flow/Prototype (FR-M1-4/5). Bentuk = envelope
        // `summaryObj` yang sama dengan renderer Fase D; `null` = belum ada draf.
        get("/draft") {
            call.gate() ?: return@get
            val stored = drafts.findByTenant(call.tenantContext.tenantId)
            call.respondText(
                stored?.let { summaryObj(it).encode() } ?: "null",
                ContentType.Application.Json
            )
        }

        get("/chat") {
            call.gate() ?: return@get
            val conversation = chats.conversationFor(call.tenantContext.tenantId)
            val messages = chats.messages(conversation.id)
            call.respondText(
                jsonObjectOf(
                    "conversationId" to jsonOf(conversation.id.value),
                    "messages" to jsonArrayOf(messages.map(::messageJson))
                ).encode(),
                ContentType.Application.Json
            )
        }

        post("/chat") {
            call.gate() ?: return@post
            val text = (JsonParser.parse(call.receiveText()) as? JsonValue.Obj)?.string("text")?.trim()
            if (text.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, "Body wajib {\"text\":\"…\"}")
                return@post
            }
            send(call.tenantContext.tenantId, text).fold(
                onSuccess = { messages ->
                    call.respondText(jsonArrayOf(messages.map(::messageJson)).encode(), ContentType.Application.Json)
                },
                onFailure = { e -> call.respond(HttpStatusCode.BadGateway, "Agent gagal menjawab: ${e.message}") }
            )
        }

        // "Terapkan" — aksi manusia (plan §4). Validator menilai ulang patch di use case.
        post("/chat/apply") {
            call.gate() ?: return@post
            val messageId = (JsonParser.parse(call.receiveText()) as? JsonValue.Obj)?.string("messageId")
            if (messageId.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, "Body wajib {\"messageId\":\"…\"}")
                return@post
            }
            apply(call.tenantContext.tenantId, ChatMessageId(messageId)).fold(
                onSuccess = { stored ->
                    call.respondText(
                        jsonObjectOf(
                            "draftId" to jsonOf(stored.id.value),
                            "status" to jsonOf(stored.status.name)
                        ).encode(),
                        ContentType.Application.Json
                    )
                },
                onFailure = { e -> call.respond(HttpStatusCode.Conflict, "Patch tidak diterapkan: ${e.message}") }
            )
        }
    }
}

/** Pesan chat sebagai JSON; patch usulan disisipkan apa adanya (sudah JSON sah). */
private fun messageJson(m: ChatMessage): com.eventverse.app.shared.json.JsonValue {
    val patch = m.proposedDraftJson?.let { runCatching { JsonParser.parse(it) }.getOrNull() }
    return jsonObjectOf(
        "id" to jsonOf(m.id.value),
        "role" to jsonOf(m.role.name),
        "text" to jsonOf(m.text),
        "summary" to jsonArrayOf(m.proposedSummary.map(::jsonOf)),
        "proposedDraft" to (patch ?: com.eventverse.app.shared.json.JsonValue.Null),
        "hasPendingPatch" to jsonOf(m.hasPendingPatch),
        "appliedDraftId" to jsonOf(m.appliedDraftId),
        "createdAt" to jsonOf(m.createdAt?.toString())
    )
}

internal fun deploymentJson(d: com.eventverse.app.domain.builder.Deployment): String = buildString {
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

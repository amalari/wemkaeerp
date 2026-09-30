package com.eventverse.app.routes

import com.eventverse.app.domain.auth.UserId
import com.eventverse.app.domain.discovery.DiscoveryAgent
import com.eventverse.app.domain.discovery.DiscoveryDraftId
import com.eventverse.app.domain.discovery.DiscoveryDraftRepository
import com.eventverse.app.domain.discovery.DiscoveryPreviewRegistry
import com.eventverse.app.domain.discovery.DiscoveryRequest
import com.eventverse.app.domain.discovery.StoredDiscoveryDraft
import com.eventverse.app.domain.discovery.usecases.CreateDiscoveryDraftUseCase
import com.eventverse.app.domain.discovery.usecases.EndDiscoveryPreviewUseCase
import com.eventverse.app.domain.discovery.usecases.LockDiscoveryDraftUseCase
import com.eventverse.app.domain.discovery.usecases.StartDiscoveryPreviewUseCase
import com.eventverse.app.domain.discovery.usecases.UpdateDiscoveryDraftUseCase
import com.eventverse.app.domain.tenant.TenantRepository
import com.eventverse.app.plugins.callerPrincipalOrNull
import com.eventverse.app.shared.discovery.DiscoveryDraftCodec
import com.eventverse.app.shared.discovery.DiscoveryDraftDecodeException
import com.eventverse.app.shared.json.JsonParser
import com.eventverse.app.shared.json.JsonValue
import com.eventverse.app.shared.json.jsonArrayOf
import com.eventverse.app.shared.json.jsonObjectOf
import com.eventverse.app.shared.json.jsonOf
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import kotlinx.datetime.Clock

/**
 * Funnel discovery ber-login (plan §2 A6). Semua endpoint wajib principal (plugin autentikasi menolak
 * sebelum sini) dan gerbang datanya adalah **pemilik draf atau superadmin** (T12) — draf satu prospek
 * tidak pernah terbaca prospek lain (test 403 di `DiscoveryApiTest`).
 *
 * Tulis = fail-closed: keluaran agent divalidasi sebelum disimpan, dokumen PUT didekode ketat, dan
 * LOCKED tidak bisa diubah dari endpoint mana pun (409).
 *
 * Bukan `/api/admin` (prospek adalah pengguna biasa, bukan superadmin) dan bukan `/api/public`
 * (generator kelak bisa jadi mahal — lihat catatan rate-limit di `ProspectRoutes`).
 */
fun Route.discoveryRoutes(
    repository: DiscoveryDraftRepository,
    agent: DiscoveryAgent,
    tenantRepository: TenantRepository
) {
    val create = CreateDiscoveryDraftUseCase(agent, repository)
    val update = UpdateDiscoveryDraftUseCase(repository)
    val lock = LockDiscoveryDraftUseCase(repository)
    val startPreview = StartDiscoveryPreviewUseCase(repository, tenantRepository)
    val endPreview = EndDiscoveryPreviewUseCase(repository)

    route("/api/discovery/drafts") {
        // Narasi → draf. Body: {"narrative": "...", "industryHint"?, "displayName"?, "prospectLeadId"?, "id"?}.
        post {
            val principal = call.callerPrincipalOrNull ?: return@post unauthorized()
            val body = runCatching { JsonParser.parseObject(call.receiveText()) }.getOrNull()
                ?: return@post badRequest("Body harus JSON objek")
            val narrative = body.string("narrative")?.takeIf { it.isNotBlank() }
                ?: return@post badRequest("Field 'narrative' wajib diisi")
            val draftId = body.string("id")?.let { DiscoveryDraftId(it) }
                ?: DiscoveryDraftId("draft-${Clock.System.now().toEpochMilliseconds()}")

            create(
                request = DiscoveryRequest(
                    narrative = narrative,
                    industryHint = body.string("industryHint"),
                    displayName = body.string("displayName")
                ),
                ownerUserId = UserId(principal.userId),
                draftId = draftId,
                prospectLeadId = body.string("prospectLeadId")
            ).onSuccess { call.respondText(summary(it), ContentType.Application.Json, HttpStatusCode.Created) }
                .onFailure { badRequest(it.message ?: "Gagal membuat draf") }
        }

        // Draf milik pemanggil; superadmin melihat seluruhnya (antrean review platform).
        get {
            val principal = call.callerPrincipalOrNull ?: return@get unauthorized()
            val drafts = if (principal.isPlatformSuperadmin) repository.findAll()
            else repository.findByOwner(UserId(principal.userId))
            call.respondText(jsonArrayOf(drafts.map { summaryObj(it) }).encode(), ContentType.Application.Json)
        }

        get("/{id}") {
            val principal = call.callerPrincipalOrNull ?: return@get unauthorized()
            val stored = repository.findById(DiscoveryDraftId(call.parameters["id"].orEmpty()))
                ?: return@get notFound("Draf tidak ditemukan")
            if (!mayAccess(principal, stored)) return@get forbidden()
            call.respondText(summary(stored), ContentType.Application.Json)
        }

        // Revisi dokumen. Body = dokumen DiscoveryDraftCodec (pack + blueprint + screens).
        put("/{id}") {
            val principal = call.callerPrincipalOrNull ?: return@put unauthorized()
            val id = DiscoveryDraftId(call.parameters["id"].orEmpty())
            val existing = repository.findById(id) ?: return@put notFound("Draf tidak ditemukan")
            if (!mayAccess(principal, existing)) return@put forbidden()
            val draft = try {
                DiscoveryDraftCodec.decode(call.receiveText())
            } catch (e: DiscoveryDraftDecodeException) {
                return@put badRequest("Dokumen draf tidak sah (${e.path})")
            }
            update(id, UserId(principal.userId), principal.isPlatformSuperadmin, draft)
                .onSuccess { call.respondText(summary(it), ContentType.Application.Json) }
                .onFailure {
                    val status = when (it) {
                        is UpdateDiscoveryDraftUseCase.LockedException -> HttpStatusCode.Conflict
                        else -> HttpStatusCode.BadRequest
                    }
                    call.respondText(it.message ?: "Gagal merevisi draf", ContentType.Text.Plain, status)
                }
        }

        post("/{id}/lock") {
            val principal = call.callerPrincipalOrNull ?: return@post unauthorized()
            val id = DiscoveryDraftId(call.parameters["id"].orEmpty())
            val existing = repository.findById(id) ?: return@post notFound("Draf tidak ditemukan")
            if (!mayAccess(principal, existing)) return@post forbidden()
            lock(id, UserId(principal.userId), principal.isPlatformSuperadmin)
                .onSuccess { call.respondText(summary(it), ContentType.Application.Json) }
                .onFailure { call.respondText(it.message ?: "Gagal mengunci draf", ContentType.Text.Plain, HttpStatusCode.Conflict) }
        }

        // Pratinjau tanpa kode (A7): daftarkan pack draf ke registry sesi + buat tenant sandbox.
        post("/{id}/preview") {
            val principal = call.callerPrincipalOrNull ?: return@post unauthorized()
            val id = DiscoveryDraftId(call.parameters["id"].orEmpty())
            val existing = repository.findById(id) ?: return@post notFound("Draf tidak ditemukan")
            if (!mayAccess(principal, existing)) return@post forbidden()
            val ttl = call.request.queryParameters["ttlMinutes"]?.toLongOrNull()
                ?: DiscoveryPreviewRegistry.DEFAULT_TTL_MINUTES
            startPreview(id, UserId(principal.userId), principal.isPlatformSuperadmin, ttlMinutes = ttl)
                .onSuccess {
                    call.respondText(
                        jsonObjectOf(
                            "sandboxSlug" to jsonOf(it.sandboxSlug),
                            "sandboxTenantId" to jsonOf(it.sandboxTenantId.value),
                            "packCode" to jsonOf(it.packCode.value),
                            "expiresAt" to jsonOf(it.expiresAt.toString())
                        ).encode(),
                        ContentType.Application.Json
                    )
                }
                .onFailure { call.respondText(it.message ?: "Gagal memulai pratinjau", ContentType.Text.Plain, HttpStatusCode.Conflict) }
        }

        // Akhiri sesi pratinjau lebih awal; pack draf dilepas dari registry.
        delete("/{id}/preview") {
            val principal = call.callerPrincipalOrNull ?: return@delete unauthorized()
            val id = DiscoveryDraftId(call.parameters["id"].orEmpty())
            val existing = repository.findById(id) ?: return@delete notFound("Draf tidak ditemukan")
            if (!mayAccess(principal, existing)) return@delete forbidden()
            endPreview(id, UserId(principal.userId), principal.isPlatformSuperadmin)
                .onSuccess { call.respondText(jsonObjectOf("ended" to jsonOf(true)).encode(), ContentType.Application.Json) }
                .onFailure { call.respondText(it.message ?: "Gagal mengakhiri pratinjau", ContentType.Text.Plain, HttpStatusCode.Conflict) }
        }
    }
}

private suspend fun RoutingContext.unauthorized() =
    call.respondText("Authentication required", ContentType.Text.Plain, HttpStatusCode.Unauthorized)

private suspend fun RoutingContext.badRequest(message: String) =
    call.respondText(message, ContentType.Text.Plain, HttpStatusCode.BadRequest)

private suspend fun RoutingContext.notFound(message: String) =
    call.respondText(message, ContentType.Text.Plain, HttpStatusCode.NotFound)

private suspend fun RoutingContext.forbidden() =
    call.respondText("Draf ini bukan milik Anda", ContentType.Text.Plain, HttpStatusCode.Forbidden)

/** Gerbang data (T12): pemilik draf atau superadmin platform. */
private fun mayAccess(
    principal: com.eventverse.app.plugins.CallerPrincipal,
    stored: StoredDiscoveryDraft
): Boolean = principal.isPlatformSuperadmin || stored.ownerUserId.value == principal.userId

private fun summaryObj(stored: StoredDiscoveryDraft): JsonValue.Obj = jsonObjectOf(
    "id" to jsonOf(stored.id.value),
    "ownerUserId" to jsonOf(stored.ownerUserId.value),
    "prospectLeadId" to jsonOf(stored.prospectLeadId),
    "status" to jsonOf(stored.status.name),
    "schemaVersion" to jsonOf(stored.schemaVersion),
    "packCode" to jsonOf(stored.draft.pack.code.value),
    "packDisplayName" to jsonOf(stored.draft.pack.displayName),
    "blueprintCode" to jsonOf(stored.draft.blueprint.code.value),
    "moduleCount" to jsonOf(stored.draft.pack.modules.size),
    "activeModuleCount" to jsonOf(stored.draft.blueprint.activeModuleCodes.size),
    "screenCount" to jsonOf(stored.draft.screens.size),
    "createdAt" to jsonOf(stored.createdAt?.toString()),
    "lockedAt" to jsonOf(stored.lockedAt?.toString())
)

private fun summary(stored: StoredDiscoveryDraft): String = summaryObj(stored).encode()

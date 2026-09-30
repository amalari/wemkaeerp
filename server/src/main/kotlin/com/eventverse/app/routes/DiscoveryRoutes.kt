package com.eventverse.app.routes

import com.eventverse.app.domain.auth.UserId
import com.eventverse.app.domain.discovery.DiscoveryAgent
import com.eventverse.app.domain.discovery.DiscoveryDraftId
import com.eventverse.app.domain.discovery.DiscoveryDraftRepository
import com.eventverse.app.domain.discovery.DiscoveryPreviewRegistry
import com.eventverse.app.domain.discovery.DiscoveryRequest
import com.eventverse.app.domain.discovery.StoredDiscoveryDraft
import com.eventverse.app.domain.discovery.HandoffScaffoldGenerator
import com.eventverse.app.domain.discovery.DiscoveryDraftStatus
import com.eventverse.app.domain.discovery.PrototypePattern
import com.eventverse.app.domain.discovery.PrototypePatternRepository
import com.eventverse.app.domain.discovery.SavePrototypePatternUseCase
import com.eventverse.app.domain.discovery.WidgetRegistry
import com.eventverse.app.domain.discovery.usecases.CreateDiscoveryDraftUseCase
import com.eventverse.app.domain.discovery.usecases.EndDiscoveryPreviewUseCase
import com.eventverse.app.domain.discovery.usecases.HandoffDiscoveryDraftUseCase
import com.eventverse.app.domain.discovery.usecases.LockDiscoveryDraftUseCase
import com.eventverse.app.domain.discovery.usecases.PriceDiscoveryDraftUseCase
import com.eventverse.app.domain.discovery.usecases.StartDiscoveryPreviewUseCase
import com.eventverse.app.domain.discovery.usecases.SubmitDiscoveryDraftUseCase
import com.eventverse.app.domain.discovery.usecases.UpdateDiscoveryDraftUseCase
import com.eventverse.app.domain.moduledev.Percentage
import com.eventverse.app.domain.pack.DomainPackRegistry
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
import java.io.File

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
    tenantRepository: TenantRepository,
    priceDraft: PriceDiscoveryDraftUseCase,
    submitDraft: SubmitDiscoveryDraftUseCase,
    handoffDraft: HandoffDiscoveryDraftUseCase,
    prototypePatterns: PrototypePatternRepository
) {
    val create = CreateDiscoveryDraftUseCase(agent, repository)
    val update = UpdateDiscoveryDraftUseCase(repository)
    val lock = LockDiscoveryDraftUseCase(repository)
    val startPreview = StartDiscoveryPreviewUseCase(repository, tenantRepository)
    val endPreview = EndDiscoveryPreviewUseCase(repository)
    val savePattern = SavePrototypePatternUseCase(prototypePatterns)

    route("/api/discovery/patterns") {
        // Daftar pola Studio — login cukup (dipakai renderer saat menyusun prototype).
        get {
            call.callerPrincipalOrNull ?: return@get unauthorized()
            call.respondText(
                jsonArrayOf(prototypePatterns.findAll().map(::patternJson)).encode(),
                ContentType.Application.Json
            )
        }

        // Simpan pola — internal Studio, superadmin saja; tulis fail-closed (widget & pack divalidasi).
        post {
            val principal = call.callerPrincipalOrNull ?: return@post unauthorized()
            if (!principal.isPlatformSuperadmin) return@post forbidden()
            val body = runCatching { JsonParser.parseObject(call.receiveText()) }.getOrNull()
                ?: return@post badRequest("Body harus JSON objek")
            val name = body.string("name")?.takeIf { it.isNotBlank() }
                ?: return@post badRequest("Field 'name' wajib diisi")
            val widget = body.string("widget")
                ?: return@post badRequest("Field 'widget' wajib diisi")
            val patternJson = body.obj("pattern")?.encode()
                ?: return@post badRequest("Field 'pattern' wajib objek JSON")
            savePattern(
                id = body.string("id") ?: "pattern-${Clock.System.now().toEpochMilliseconds()}",
                name = name, widgetCode = widget, patternJson = patternJson,
                createdByUserId = UserId(principal.userId), packCode = body.string("packCode")
            ).onSuccess {
                call.respondText(patternJson(it).encode(), ContentType.Application.Json, HttpStatusCode.Created)
            }.onFailure {
                call.respondText(it.message ?: "Gagal menyimpan pola", ContentType.Text.Plain, HttpStatusCode.Conflict)
            }
        }
    }

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
        // B1: estimasi dari draf — berubah bila modul/layar berubah (margin % via query, default 35).
        get("/{id}/price") {
            val principal = call.callerPrincipalOrNull ?: return@get unauthorized()
            val id = DiscoveryDraftId(call.parameters["id"].orEmpty())
            val existing = repository.findById(id) ?: return@get notFound("Draf tidak ditemukan")
            if (!mayAccess(principal, existing)) return@get forbidden()
            val margin = call.request.queryParameters["marginPercent"]?.toDoubleOrNull() ?: 35.0
            priceDraft(existing.draft, Percentage(margin))
                .onSuccess {
                    call.respondText(
                        jsonObjectOf(
                            "packCode" to jsonOf(it.packCode.value),
                            "coveredModuleIds" to jsonArrayOf(it.coveredModuleIds.map(::jsonOf)),
                            "newModuleIds" to jsonArrayOf(it.newModuleIds.map(::jsonOf)),
                            "customScreenCount" to jsonOf(it.customScreenCount),
                            "subscriptionMonthlyIdr" to jsonOf(it.pricing.range.subscriptionMonthly.amount),
                            "gapLowMonthlyIdr" to (it.pricing.range.gapLowMonthly?.let { g -> jsonOf(g.amount) } ?: com.eventverse.app.shared.json.JsonValue.Null),
                            "gapHighMonthlyIdr" to (it.pricing.range.gapHighMonthly?.let { g -> jsonOf(g.amount) } ?: com.eventverse.app.shared.json.JsonValue.Null),
                            "withheld" to jsonOf(!it.pricing.range.isPublishable),
                            "unpriceableGapCount" to jsonOf(it.pricing.range.unpriceableGapCount)
                        ).encode(),
                        ContentType.Application.Json
                    )
                }
                .onFailure { badRequest(it.message ?: "Gagal menghitung estimasi") }
        }

        // B2: CTA "Bangun Sistem Ini" — wajib LOCKED; draf masuk funnel tim sebagai ProspectLead.
        post("/{id}/submit") {
            val principal = call.callerPrincipalOrNull ?: return@post unauthorized()
            val id = DiscoveryDraftId(call.parameters["id"].orEmpty())
            val existing = repository.findById(id) ?: return@post notFound("Draf tidak ditemukan")
            if (!mayAccess(principal, existing)) return@post forbidden()
            val body = runCatching { JsonParser.parseObject(call.receiveText()) }.getOrNull()
                ?: return@post badRequest("Body harus JSON objek")
            val companyName = body.string("companyName")?.trim()?.takeIf { it.isNotBlank() }
                ?: return@post badRequest("Field 'companyName' wajib diisi")
            submitDraft(
                draftId = id, callerUserId = UserId(principal.userId),
                isPlatformSuperadmin = principal.isPlatformSuperadmin,
                companyName = companyName, contactName = body.string("contactName"),
                contactEmail = body.string("contactEmail"), contactPhone = body.string("contactPhone")
            ).onSuccess {
                call.respondText(
                    jsonObjectOf("draftId" to jsonOf(it.draftId.value), "leadId" to jsonOf(it.leadId.value)).encode(),
                    ContentType.Application.Json, HttpStatusCode.Created
                )
            }.onFailure { call.respondText(it.message ?: "Gagal mendaftarkan draf", ContentType.Text.Plain, HttpStatusCode.Conflict) }
        }

        // B3: handoff otomatis — superadmin saja; buat tenant → kunci pack → tetapkan → salin blueprint.
        post("/{id}/handoff") {
            val principal = call.callerPrincipalOrNull ?: return@post unauthorized()
            if (!principal.isPlatformSuperadmin) return@post forbidden()
            val id = DiscoveryDraftId(call.parameters["id"].orEmpty())
            if (repository.findById(id) == null) return@post notFound("Draf tidak ditemukan")
            val body = runCatching { JsonParser.parseObject(call.receiveText()) }.getOrNull()
                ?: return@post badRequest("Body harus JSON objek")
            val tenantSlug = body.string("tenantSlug")?.trim()?.takeIf { it.isNotBlank() }
                ?: return@post badRequest("Field 'tenantSlug' wajib diisi")
            val companyName = body.string("companyName")?.trim()?.takeIf { it.isNotBlank() } ?: tenantSlug
            handoffDraft(id, isPlatformSuperadmin = true, tenantSlug = tenantSlug, companyName = companyName)
                .onSuccess {
                    call.respondText(
                        jsonObjectOf(
                            "tenantSlug" to jsonOf(it.tenant.slug.value),
                            "packCode" to jsonOf(it.packCode.value),
                            "packVersion" to (it.packVersion?.let { v -> jsonOf(v) } ?: com.eventverse.app.shared.json.JsonValue.Null),
                            "blueprintCode" to jsonOf(it.tenant.businessPreset.code.value)
                        ).encode(),
                        ContentType.Application.Json, HttpStatusCode.Created
                    )
                }
                .onFailure { call.respondText(it.message ?: "Gagal handoff", ContentType.Text.Plain, HttpStatusCode.Conflict) }
        }

        // B4: scaffold kandidat PR — superadmin saja. Generator murni: TIDAK menyentuh database
        // maupun pohon sumber; keluarannya teks untuk ditinjau manusia sebelum dijadikan PR.
        post("/{id}/scaffold") {
            val principal = call.callerPrincipalOrNull ?: return@post unauthorized()
            if (!principal.isPlatformSuperadmin) return@post forbidden()
            val id = DiscoveryDraftId(call.parameters["id"].orEmpty())
            val existing = repository.findById(id) ?: return@post notFound("Draf tidak ditemukan")
            if (existing.status != DiscoveryDraftStatus.LOCKED) {
                return@post call.respondText(
                    "Draf ${id.value} masih DRAFT; kunci dulu sebelum scaffold",
                    ContentType.Text.Plain, HttpStatusCode.Conflict
                )
            }
            if (DomainPackRegistry.isShipped(existing.draft.pack.code)) {
                return@post call.respondText(
                    "Pack ${existing.draft.pack.code.value} adalah pack bawaan — tidak perlu scaffold",
                    ContentType.Text.Plain, HttpStatusCode.Conflict
                )
            }
            val scaffold = HandoffScaffoldGenerator().generate(existing.draft.pack, nextMigrationVersion())
            call.respondText(
                jsonObjectOf(
                    "packCode" to jsonOf(scaffold.packCode),
                    "migrationVersion" to jsonOf(scaffold.migrationVersion),
                    "files" to jsonArrayOf(scaffold.files.map { f ->
                        jsonObjectOf("path" to jsonOf(f.path), "content" to jsonOf(f.content))
                    })
                ).encode(),
                ContentType.Application.Json, HttpStatusCode.Created
            )
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
    "blueprintDescription" to jsonOf(stored.draft.blueprint.description),
    "moduleCount" to jsonOf(stored.draft.pack.modules.size),
    "activeModuleCount" to jsonOf(stored.draft.blueprint.activeModuleCodes.size),
    "screenCount" to jsonOf(stored.draft.screens.size),
    "createdAt" to jsonOf(stored.createdAt?.toString()),
    "lockedAt" to jsonOf(stored.lockedAt?.toString()),
    // Data penuh untuk renderer Fase D (ModuleMapPane/DataFlowPane/PrototypeRenderer) — tetap
    // ringkasan: tidak ada parameter, fakta kontrak, atau harga di sini.
    "modules" to jsonArrayOf(stored.draft.pack.modules.map { m ->
        val slot = m.slot
        val slotDef = slot?.let { runCatching { DomainPackRegistry.slotDefinition(it) }.getOrNull() }
        jsonObjectOf(
            "id" to jsonOf(m.id.value),
            "displayName" to jsonOf(m.displayName),
            "section" to jsonOf(m.section.value),
            "kind" to jsonOf(m.kind.name),
            "slot" to jsonOf(slot?.value),
            "slotInput" to jsonOf(slotDef?.defaultInput?.value),
            "slotOutput" to jsonOf(slotDef?.defaultOutput?.value)
        )
    }),
    "activeModuleCodes" to jsonArrayOf(stored.draft.blueprint.activeModuleCodes.map(::jsonOf)),
    "screens" to jsonArrayOf(stored.draft.screens.map { s ->
        jsonObjectOf(
            "screenId" to jsonOf(s.screenId),
            "moduleId" to jsonOf(s.moduleId.value),
            "title" to jsonOf(s.title),
            "widget" to jsonOf(s.widget),
            // Sample data berupa data (plan §4): dihitung WidgetRegistry di server agar klien
            // tidak perlu merekonstruksi DomainPack hanya untuk menggambar pratinjau.
            "sampleRows" to jsonArrayOf(WidgetRegistry.sampleRowsFor(s, stored.draft.pack).map { row ->
                jsonObjectOf(*row.map { (k, v) -> k to jsonOf(v) }.toTypedArray())
            })
        )
    })
)

private fun summary(stored: StoredDiscoveryDraft): String = summaryObj(stored).encode()

private fun patternJson(pattern: PrototypePattern): com.eventverse.app.shared.json.JsonValue = jsonObjectOf(
    "id" to jsonOf(pattern.id),
    "name" to jsonOf(pattern.name),
    "widget" to jsonOf(pattern.widget.code),
    "packCode" to jsonOf(pattern.packCode),
    "pattern" to com.eventverse.app.shared.json.JsonParser.parseObject(pattern.patternJson),
    "createdByUserId" to jsonOf(pattern.createdByUserId.value)
)

/** Nomor migrasi berikutnya dari direktori migrasi; fallback 79 kalau direktori tak terbaca. */
private fun nextMigrationVersion(): Int = runCatching {
    File("src/main/resources/db/migration").listFiles()
        ?.mapNotNull { Regex("V(\\d+)__").find(it.name)?.groupValues?.get(1)?.toInt() }
        ?.maxOrNull()?.plus(1)
}.getOrNull() ?: 79

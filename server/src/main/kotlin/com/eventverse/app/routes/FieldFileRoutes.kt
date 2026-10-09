package com.eventverse.app.routes

import com.eventverse.app.domain.crm.CrmLeadRepository
import com.eventverse.app.domain.crm.LeadId
import com.eventverse.app.domain.customfield.CustomFieldId
import com.eventverse.app.domain.discovery.handoff.PrototypeRowRepository
import com.eventverse.app.domain.pack.GarmentModules
import com.eventverse.app.domain.rbac.AccessLevel
import com.eventverse.app.domain.rbac.BusinessModules
import com.eventverse.app.domain.rbac.ModuleAssignmentRepository
import com.eventverse.app.domain.rbac.RoleRepository
import com.eventverse.app.domain.storage.FileRef
import com.eventverse.app.domain.storage.ObjectStorage
import com.eventverse.app.plugins.tenantContextOrNull
import com.eventverse.app.shared.json.jsonObjectOf
import com.eventverse.app.shared.json.jsonOf
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.slf4j.LoggerFactory

/**
 * Batas ukuran satu berkas field (C8, TRD-FIELD-002 K3/R2): 10 MB, sama dengan PO.
 * Konstanta terpusat di file route ini (bukan tersebar per fitur) supaya perluasan allowlist/
 * batas = satu perubahan + tes (risiko TRD "allowlist terlalu sempit").
 */
internal const val MAX_FIELD_FILE_BYTES = 10 * 1024 * 1024

/** Allowlist tertutup v1 (K4/R2) — fail-closed: yang tidak terdaftar ditolak 415, bukan disaring. */
internal val ALLOWED_FIELD_FILE_MIME_TYPES = setOf(
    "application/pdf",
    "image/png",
    "image/jpeg",
    "image/webp",
    "text/plain",
    "text/csv"
)

private val log = LoggerFactory.getLogger("FieldFileRoutes")

/**
 * Endpoint unggah/unduh berkas untuk tipe field `FILE` (C8, TRD-FIELD-002 §4.4 — kontrak persis,
 * jangan diubah). Berkas hidup di [ObjectStorage]; sel field hanya menyimpan string [FileRef] yang
 * dikembalikan ke klien — byte TIDAK PERNAH masuk DB.
 *
 * Gerbang (Kontrak 7 — RBAC paling awal, sebelum body dibaca):
 * - `POST /api/tenant/modules/{moduleCode}/records/{recordId}/fields/{fieldKey}/upload` — modul
 *   induk pada path wajib OPERATE (`requireModuleAccess`); modul tak dikenal = 403 (fail-closed).
 * - `GET  .../download` — modul induk wajib VIEW; ref dibaca dari nilai field record
 *   ([recordRows]); record/ref tidak ada = 404.
 * - `POST/GET /api/tenant/crm/leads/{leadId}/fields/{fieldId}/upload|download` — gerbang modul CRM
 *   (pola [CrmRoutes]); lead wajib ada + PIC dalam jangkauan data pemanggil.
 *
 * Urutan tolakan setelah RBAC (FR-3): **503** storage belum `isConfigured` (pesan menyebut env) →
 * **400** `fileName` kosong / body kosong → **415** tipe konten di luar
 * [ALLOWED_FIELD_FILE_MIME_TYPES] (dicek dari query, sebelum body dibaca) → **413** >
 * [MAX_FIELD_FILE_BYTES] → **404** record/ref tak ada (unduh). 413/415/503 dicatat WARN tanpa isi
 * body. Metadata lewat query parameter, byte lewat body raw — satu request, tanpa multipart
 * (pola `DealRoutes` yang terbukti).
 *
 * [recordRows] memetakan kode modul → penyimpan baris modul hasil handoff untuk resolve ref
 * unduhan generik. Modul yang nilainya hidup di agregat khusus (mis. deal) dilayani endpoint
 * modulnya masing-masing; modul tanpa entri = 404 fail-closed, bukan fallback.
 */
fun Route.fieldFileRoutes(
    objectStorage: ObjectStorage,
    roleRepository: RoleRepository,
    moduleAssignmentRepository: ModuleAssignmentRepository,
    crmLeadRepository: CrmLeadRepository,
    employeeRepository: com.eventverse.app.domain.orgchart.EmployeeRepository,
    recordRows: Map<String, PrototypeRowRepository> = emptyMap()
) {
    route("/api/tenant/modules/{moduleCode}/records/{recordId}/fields/{fieldKey}") {

        post("/upload") {
            val tenant = call.requireTenant() ?: return@post
            // Modul induk tak dikenal = keputusan RBAC tak bisa dihitung = 403 (Kontrak 7, fail-closed).
            val module = BusinessModules.fromCode(call.parameters["moduleCode"])
            if (module == null) {
                call.respond(HttpStatusCode.Forbidden, "Modul tidak dikenal: ${call.parameters["moduleCode"]}")
                return@post
            }
            val decision = call.moduleDecision(module, tenant, roleRepository, moduleAssignmentRepository)
            if (!call.requireModuleAccess(module, decision, AccessLevel.OPERATE)) return@post
            if (tenant.pack.module(module) == null) {
                call.respond(HttpStatusCode.NotFound, "Modul tidak tersedia untuk tenant ini.")
                return@post
            }
            if (!objectStorage.isConfigured) {
                call.rejectStorageUnavailable()
                return@post
            }
            val query = call.request.queryParameters
            val fileName = query["fileName"]?.takeIf { it.isNotBlank() }
            if (fileName == null) {
                call.respond(HttpStatusCode.BadRequest, "Query 'fileName' wajib diisi")
                return@post
            }
            // 415 dari query — request salah tipe tidak perlu membaca byte-nya.
            val contentType = query["contentType"]?.takeIf { it.isNotBlank() } ?: "application/octet-stream"
            if (contentType !in ALLOWED_FIELD_FILE_MIME_TYPES) {
                call.rejectUnsupportedMediaType(contentType)
                return@post
            }

            val bytes = call.receive<ByteArray>()
            if (bytes.isEmpty()) {
                call.respond(HttpStatusCode.BadRequest, "Body berkas kosong")
                return@post
            }
            if (bytes.size > MAX_FIELD_FILE_BYTES) {
                call.rejectPayloadTooLarge()
                return@post
            }

            // Hanya server yang menyusun key — fileName klien tidak tepercaya (risiko TRD: path traversal).
            val ref = runCatching {
                FileRef.build(
                    tenantId = tenant.tenantId.value,
                    moduleCode = module.value,
                    recordId = call.parameters["recordId"].orEmpty(),
                    fieldKey = call.parameters["fieldKey"].orEmpty(),
                    fileName = fileName
                )
            }.getOrElse {
                call.respond(HttpStatusCode.BadRequest, it.message ?: "Key referensi tidak dapat disusun")
                return@post
            }

            objectStorage.put(ref.value, bytes, contentType)
                .onSuccess {
                    call.respondText(
                        text = jsonObjectOf("ref" to jsonOf(ref.value)).encode(),
                        contentType = ContentType.Application.Json,
                        status = HttpStatusCode.Created
                    )
                }
                .onFailure { call.respond(HttpStatusCode.InternalServerError, it.message ?: "Gagal menyimpan berkas") }
        }

        get("/download") {
            val tenant = call.requireTenant() ?: return@get
            val module = BusinessModules.fromCode(call.parameters["moduleCode"])
            if (module == null) {
                call.respond(HttpStatusCode.Forbidden, "Modul tidak dikenal: ${call.parameters["moduleCode"]}")
                return@get
            }
            val decision = call.moduleDecision(module, tenant, roleRepository, moduleAssignmentRepository)
            if (!call.requireModuleAccess(module, decision, AccessLevel.VIEW)) return@get
            if (tenant.pack.module(module) == null) {
                call.respond(HttpStatusCode.NotFound, "Modul tidak tersedia untuk tenant ini.")
                return@get
            }
            if (!objectStorage.isConfigured) {
                call.rejectStorageUnavailable()
                return@get
            }

            val rows = recordRows[module.value]
            val row = rows?.find(tenant.tenantId, call.parameters["recordId"].orEmpty())
            if (row == null) {
                call.respond(HttpStatusCode.NotFound, "Record tidak ditemukan")
                return@get
            }
            val ref = row.values[call.parameters["fieldKey"]]?.takeIf { FileRef.isValid(it) }
            if (ref == null) {
                call.respond(HttpStatusCode.NotFound, "Field tidak berisi referensi berkas yang sah")
                return@get
            }

            objectStorage.downloadUrl(ref)
                .onSuccess { url ->
                    call.respondText(
                        text = jsonObjectOf("url" to jsonOf(url)).encode(),
                        contentType = ContentType.Application.Json
                    )
                }
                .onFailure { call.respond(HttpStatusCode.InternalServerError, it.message ?: "Gagal membuat URL unduh") }
        }
    }

    route("/api/tenant/crm/leads/{leadId}/fields/{fieldId}") {

        post("/upload") {
            val tenant = call.requireTenant() ?: return@post
            val decision = call.crmDecision(tenant, roleRepository, moduleAssignmentRepository)
            if (!call.requireCrmAccess(decision, AccessLevel.OPERATE)) return@post
            if (!objectStorage.isConfigured) {
                call.rejectStorageUnavailable()
                return@post
            }
            val query = call.request.queryParameters
            val fileName = query["fileName"]?.takeIf { it.isNotBlank() }
            if (fileName == null) {
                call.respond(HttpStatusCode.BadRequest, "Query 'fileName' wajib diisi")
                return@post
            }
            val contentType = query["contentType"]?.takeIf { it.isNotBlank() } ?: "application/octet-stream"
            if (contentType !in ALLOWED_FIELD_FILE_MIME_TYPES) {
                call.rejectUnsupportedMediaType(contentType)
                return@post
            }

            val bytes = call.receive<ByteArray>()
            if (bytes.isEmpty()) {
                call.respond(HttpStatusCode.BadRequest, "Body berkas kosong")
                return@post
            }
            if (bytes.size > MAX_FIELD_FILE_BYTES) {
                call.rejectPayloadTooLarge()
                return@post
            }

            val lead = crmLeadRepository.findById(tenant.tenantId, LeadId(call.parameters["leadId"].orEmpty()))
            if (lead == null) {
                call.respond(HttpStatusCode.NotFound, "Lead tidak ditemukan")
                return@post
            }
            val reach = call.crmOwnerReach(tenant, decision, employeeRepository)
            if (!call.requireReachableOwner(reach, lead.ownerEmployeeId)) return@post

            val ref = runCatching {
                FileRef.build(
                    tenantId = tenant.tenantId.value,
                    moduleCode = GarmentModules.CRM_SALES.value,
                    recordId = lead.id.value,
                    fieldKey = call.parameters["fieldId"].orEmpty(),
                    fileName = fileName
                )
            }.getOrElse {
                call.respond(HttpStatusCode.BadRequest, it.message ?: "Key referensi tidak dapat disusun")
                return@post
            }

            objectStorage.put(ref.value, bytes, contentType)
                .onSuccess {
                    call.respondText(
                        text = jsonObjectOf("ref" to jsonOf(ref.value)).encode(),
                        contentType = ContentType.Application.Json,
                        status = HttpStatusCode.Created
                    )
                }
                .onFailure { call.respond(HttpStatusCode.InternalServerError, it.message ?: "Gagal menyimpan berkas") }
        }

        get("/download") {
            val tenant = call.requireTenant() ?: return@get
            val decision = call.crmDecision(tenant, roleRepository, moduleAssignmentRepository)
            if (!call.requireCrmAccess(decision, AccessLevel.VIEW)) return@get
            if (!objectStorage.isConfigured) {
                call.rejectStorageUnavailable()
                return@get
            }

            val lead = crmLeadRepository.findById(tenant.tenantId, LeadId(call.parameters["leadId"].orEmpty()))
            if (lead == null) {
                call.respond(HttpStatusCode.NotFound, "Lead tidak ditemukan")
                return@get
            }
            val reach = call.crmOwnerReach(tenant, decision, employeeRepository)
            if (!call.requireReachableOwner(reach, lead.ownerEmployeeId)) return@get

            // Sel custom field = sel ter-tag {"t":...,"v":"<ref>"}; ambil v-nya dan wajibkan bentuk FileRef.
            val ref = lead.customAttributes
                .rawCell(CustomFieldId(call.parameters["fieldId"].orEmpty()))
                ?.string("v")
                ?.takeIf { FileRef.isValid(it) }
            if (ref == null) {
                call.respond(HttpStatusCode.NotFound, "Field tidak berisi referensi berkas yang sah")
                return@get
            }

            objectStorage.downloadUrl(ref)
                .onSuccess { url ->
                    call.respondText(
                        text = jsonObjectOf("url" to jsonOf(url)).encode(),
                        contentType = ContentType.Application.Json
                    )
                }
                .onFailure { call.respond(HttpStatusCode.InternalServerError, it.message ?: "Gagal membuat URL unduh") }
        }
    }
}

/** 503 + WARN tanpa isi body; pesan menyebut env yang harus diisi (FR-3). */
private suspend fun ApplicationCall.rejectStorageUnavailable() {
    log.warn("FIELD FILE ditolak 503: object storage belum dikonfigurasi (S3_ENDPOINT/S3_ACCESS_KEY/S3_SECRET_KEY/S3_BUCKET_FILES)")
    respond(
        HttpStatusCode.ServiceUnavailable,
        "Object storage belum dikonfigurasi (S3_ENDPOINT/S3_ACCESS_KEY/S3_SECRET_KEY/S3_BUCKET_FILES)."
    )
}

/** 415 + WARN tanpa isi body — hanya nama tipe yang dicatat. */
private suspend fun ApplicationCall.rejectUnsupportedMediaType(contentType: String) {
    log.warn("FIELD FILE ditolak 415: tipe konten '{}' di luar allowlist", contentType)
    respond(HttpStatusCode.UnsupportedMediaType, "Tipe berkas $contentType tidak diizinkan")
}

/** 413 + WARN tanpa isi body — hanya ukuran yang dicatat. */
private suspend fun ApplicationCall.rejectPayloadTooLarge() {
    log.warn("FIELD FILE ditolak 413: ukuran melebihi {} MB", MAX_FIELD_FILE_BYTES / (1024 * 1024))
    respond(HttpStatusCode.PayloadTooLarge, "Ukuran berkas melebihi ${MAX_FIELD_FILE_BYTES / (1024 * 1024)} MB")
}

private suspend fun ApplicationCall.requireTenant(): com.eventverse.app.domain.tenant.TenantContext? {
    val tenant = tenantContextOrNull
    if (tenant == null) {
        respond(HttpStatusCode.NotFound, "No tenant context found")
    }
    return tenant
}

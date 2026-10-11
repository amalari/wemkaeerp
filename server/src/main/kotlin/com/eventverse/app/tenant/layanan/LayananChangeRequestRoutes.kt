package com.eventverse.app.tenant.layanan

import com.eventverse.app.routes.requireModuleAccess
import com.eventverse.app.routes.moduleDecision
import com.eventverse.app.domain.customfield.RelationTargetResolver
import com.eventverse.app.domain.discovery.handoff.PrototypeRowRepository
import com.eventverse.app.domain.pack.DomainPackRegistry
import com.eventverse.app.domain.pack.ModuleId
import com.eventverse.app.domain.prototype.EntitySpec
import com.eventverse.app.domain.prototype.FieldSpec
import com.eventverse.app.domain.prototype.FieldType
import com.eventverse.app.domain.prototype.PrototypeAction
import com.eventverse.app.domain.prototype.PrototypeReducer
import com.eventverse.app.domain.prototype.PrototypeRow
import com.eventverse.app.domain.prototype.PrototypeSpec
import com.eventverse.app.domain.prototype.PrototypeStore
import com.eventverse.app.domain.prototype.StateMachine
import com.eventverse.app.domain.prototype.selectOptions
import com.eventverse.app.domain.prototype.TextValidation
import com.eventverse.app.domain.prototype.relationTargetProblem
import com.eventverse.app.domain.rbac.AccessLevel
import com.eventverse.app.domain.rbac.ModuleAssignmentRepository
import com.eventverse.app.domain.rbac.RoleRepository
import com.eventverse.app.domain.tenant.TenantContext
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.plugins.tenantContextOrNull
import com.eventverse.app.shared.json.JsonParser
import com.eventverse.app.shared.json.jsonArrayOf
import com.eventverse.app.shared.json.jsonObjectOf
import com.eventverse.app.shared.json.jsonOf
import com.eventverse.app.shared.json.jsonStringMapOf
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import java.util.UUID

// KANDIDAT PR (hasil generator) — modul "layanan_change_request". Setelah diterapkan milik tim.
private val MODULE = ModuleId("layanan_change_request")
private const val ENTITY_ID = "change_request"
private const val ID_PREFIX = "change_request"
private val SPEC = PrototypeSpec(listOf(EntitySpec("change_request", "Permintaan Perubahan", listOf(FieldSpec("judul", "Judul", FieldType.TEXT, selectOptions(), true), FieldSpec("peminta", "Peminta", FieldType.TEXT, selectOptions(), false), FieldSpec("prioritas", "Prioritas", FieldType.ENUM, selectOptions("Rendah", "Sedang", "Tinggi"), false), FieldSpec("status", "Status", FieldType.ENUM, selectOptions("Baru", "Ditinjau", "Disetujui", "Selesai"), true), FieldSpec("perkiraan_jam", "Perkiraan jam", FieldType.NUMBER, selectOptions(), false), FieldSpec("target_selesai", "Target selesai", FieldType.DATE, selectOptions(), false), FieldSpec("mendesak", "Mendesak", FieldType.BOOL, selectOptions(), false), FieldSpec("catatan", "Catatan", FieldType.TEXT, selectOptions(), false), FieldSpec("lampiran", "Lampiran", FieldType.FILE, selectOptions(), false), FieldSpec("rujukan", "Permintaan terkait", FieldType.RELATION, selectOptions(), false, target = "change_request")), StateMachine("status", mapOf("Baru" to setOf("Ditinjau"), "Ditinjau" to setOf("Baru", "Disetujui"), "Disetujui" to setOf("Ditinjau", "Selesai"), "Selesai" to setOf("Disetujui"))))), emptyList())
private val DATE_FIELDS = listOf<FieldSpec>(FieldSpec("target_selesai", "Target selesai", FieldType.DATE))
private val TIME_FIELDS = listOf<FieldSpec>()
private val TEXT_FIELDS = listOf<FieldSpec>()
private val MULTI_FIELDS = listOf<FieldSpec>()

/**
 * CRUD Permintaan Perubahan: baca = VIEW, tambah/ubah (termasuk pindah status) = OPERATE, hapus = MANAGE.
 * Keputusan RBAC tak terhitung = 403 sebelum body dibaca (Kontrak 7).
 */
fun Route.layananChangeRequestRoutes(
    repository: PrototypeRowRepository,
    roleRepository: RoleRepository,
    moduleAssignmentRepository: ModuleAssignmentRepository,
    relationResolver: RelationTargetResolver
) {
    suspend fun ApplicationCall.authorized(required: AccessLevel): TenantContext? {
        val tenant = tenantContextOrNull ?: run { respond(HttpStatusCode.NotFound, "No tenant context found"); return null }
        // Modul tak dikenal proses ini (pack-nya tidak termuat) = keputusan RBAC tak bisa dihitung = 403 (fail-closed, Kontrak 7).
        if (DomainPackRegistry.moduleDefinition(MODULE) == null) { respond(HttpStatusCode.Forbidden, "Modul tidak tersedia."); return null }
        // RBAC DULU: tanpa wewenang = 403 sebelum apa pun tentang isi tenant terungkap (RouteGateTest menegakkan ini).
        val decision = moduleDecision(MODULE, tenant, roleRepository, moduleAssignmentRepository)
        if (!requireModuleAccess(MODULE, decision, required)) return null
        // Yang lolos RBAC (mis. owner, yang melewati matriks) tetap harus berada di tenant yang packnya memuat modul ini.
        if (tenant.pack.module(MODULE) == null) { respond(HttpStatusCode.NotFound, "Modul tidak tersedia untuk tenant ini."); return null }
        return tenant
    }

    /** `{"values":{...}}` → peta string; JSON tak sah = 400 dan `null`. */
    suspend fun ApplicationCall.bodyValues(): Map<String, String>? {
        val body = runCatching { JsonParser.parseObject(receiveText()) }.getOrNull()
        if (body == null) { respond(HttpStatusCode.BadRequest, "Body harus JSON objek."); return null }
        return body.stringMap("values")
    }

    route("/api/tenant/modules/layanan_change_request/change_requests") {
        get {
            val tenant = call.authorized(AccessLevel.VIEW) ?: return@get
            call.respondText(jsonArrayOf(repository.list(tenant.tenantId).map(::rowJson)).encode(), ContentType.Application.Json)
        }
        get("/{id}") {
            val tenant = call.authorized(AccessLevel.VIEW) ?: return@get
            val row = repository.find(tenant.tenantId, call.parameters["id"].orEmpty())
                ?: return@get call.respond(HttpStatusCode.NotFound, "Data tidak ditemukan.")
            call.respondText(rowJson(row).encode(), ContentType.Application.Json)
        }
        post {
            val tenant = call.authorized(AccessLevel.OPERATE) ?: return@post
            val values = call.bodyValues() ?: return@post
            val row = PrototypeRow(ID_PREFIX + "-" + UUID.randomUUID(), values)
            val problem = dateProblem(values) ?: timeProblem(values) ?: textProblem(values) ?: multiProblem(values) ?: fileProblem(tenant.tenantId.value, null, values) ?: relationProblem(tenant.tenantId, values, relationResolver)
                ?: PrototypeReducer.reduce(SPEC, PrototypeStore(), PrototypeAction.Create(ENTITY_ID, row)).exceptionOrNull()?.message
            if (problem != null) return@post call.respond(HttpStatusCode.BadRequest, problem)
            repository.save(tenant.tenantId, row)
            call.respondText(rowJson(row).encode(), ContentType.Application.Json, HttpStatusCode.Created)
        }
        put("/{id}") {
            val tenant = call.authorized(AccessLevel.OPERATE) ?: return@put
            val id = call.parameters["id"].orEmpty()
            val values = call.bodyValues() ?: return@put
            val current = repository.find(tenant.tenantId, id) ?: return@put call.respond(HttpStatusCode.NotFound, "Data tidak ditemukan.")
            var problem = dateProblem(values) ?: timeProblem(values) ?: textProblem(values) ?: multiProblem(values) ?: fileProblem(tenant.tenantId.value, id, values) ?: relationProblem(tenant.tenantId, values, relationResolver)
            var store = PrototypeStore(mapOf(ENTITY_ID to listOf(current)))
            if (problem == null) {
                for ((key, value) in values) {
                    val next = PrototypeReducer.reduce(SPEC, store, PrototypeAction.SetField(ENTITY_ID, id, key, value))
                    if (next.isFailure) { problem = next.exceptionOrNull()?.message; break }
                    store = next.getOrThrow()
                }
            }
            if (problem != null) return@put call.respond(HttpStatusCode.BadRequest, problem)
            val updated = store.rowsOf(ENTITY_ID).single()
            repository.save(tenant.tenantId, updated)
            call.respondText(rowJson(updated).encode(), ContentType.Application.Json)
        }
        delete("/{id}") {
            val tenant = call.authorized(AccessLevel.MANAGE) ?: return@delete
            val removed = repository.delete(tenant.tenantId, call.parameters["id"].orEmpty())
            if (!removed) return@delete call.respond(HttpStatusCode.NotFound, "Data tidak ditemukan.")
            call.respondText(jsonObjectOf("deleted" to jsonOf(true)).encode(), ContentType.Application.Json)
        }
    }
}

private fun rowJson(row: PrototypeRow) = jsonObjectOf("id" to jsonOf(row.id), "values" to jsonStringMapOf(row.values))

/** Field tanggal wajib ISO (TTTT-BB-HH, atau TTTT-BB-HHTJJ:MM bila withTime); kolom DATE/TIMESTAMP tidak menerima teks bebas. */
private fun dateProblem(values: Map<String, String>): String? = DATE_FIELDS.firstNotNullOfOrNull { f ->
    val v = values[f.key].orEmpty()
    if (v.isBlank()) null
    else if (f.withTime) {
        val ok = v.length == 16 && v[10] == 'T' && runCatching { LocalDateTime.parse(v) }.isSuccess
        if (ok) null else "'" + f.label + "' harus berformat TTTT-BB-HHTJJ:MM."
    } else if (runCatching { LocalDate.parse(v) }.isFailure) "'" + f.label + "' harus berformat TTTT-BB-HH." else null
}

/** Field TIME: nilai wajib jam dinding JJ:MM 24-jam tepat menit (aturan tunggal `FieldSpec.accepts`). */
private fun timeProblem(values: Map<String, String>): String? = TIME_FIELDS.firstNotNullOfOrNull { f ->
    val v = values[f.key].orEmpty()
    if (v.isNotBlank() && !f.accepts(v)) "'" + f.label + "' harus berformat JJ:MM (24 jam, tepat menit)." else null
}

/** Field TEXT berkunci validasi bentuk (email/telepon): nilai disimpan apa adanya, hanya bentuknya yang diperiksa (aturan tunggal `FieldSpec.accepts`). */
private fun textProblem(values: Map<String, String>): String? = TEXT_FIELDS.firstNotNullOfOrNull { f ->
    val v = values[f.key].orEmpty()
    if (v.isNotBlank() && !f.accepts(v)) "'" + f.label + "' bukan " + f.validation.name.lowercase() + " yang sah." else null
}

/** Field FILE: ref wajib milik tenant DAN record penulis; recordId null = record baru (ditolak). */
private fun fileProblem(tenantId: String, recordId: String?, values: Map<String, String>): String? =
    SPEC.entities.single().fileOwnershipProblem(tenantId, recordId, values)

/** Field RELATION: target harus ada pada tenant penulis; target tanpa ':' dinormalkan ke modul ini. */
private suspend fun relationProblem(tenantId: TenantId, values: Map<String, String>, resolver: RelationTargetResolver): String? =
    SPEC.entities.single().relationTargetProblem(MODULE.value, tenantId, values, resolver)

/** Field MULTI_SELECT: nilai wajib array JSON nama opsi (aturan tunggal `FieldSpec.accepts`). */
private fun multiProblem(values: Map<String, String>): String? = MULTI_FIELDS.firstNotNullOfOrNull { f ->
    val v = values[f.key].orEmpty()
    if (v.isNotBlank() && !f.accepts(v)) "'" + f.label + "' bukan pilihan ganda yang sah." else null
}

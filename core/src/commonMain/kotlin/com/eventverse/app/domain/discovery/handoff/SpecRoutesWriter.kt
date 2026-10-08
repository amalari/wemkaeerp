package com.eventverse.app.domain.discovery.handoff

import com.eventverse.app.domain.prototype.EntitySpec
import com.eventverse.app.domain.prototype.FieldType
import com.eventverse.app.domain.prototype.NumberFormat
import com.eventverse.app.domain.prototype.TextValidation

/**
 * Route CRUD **fail-closed** dari spec (kontrak §3.4). Urutan gerbang tidak boleh diubah:
 * konteks tenant → modul dikenal (403 bila tidak) → keputusan RBAC + level (403) → modul ada di pack tenant (404, hanya untuk yang lolos RBAC) → **baru** body dibaca.
 * Validasi isi memakai `PrototypeReducer` dengan spec yang sama dengan prototype (required, opsi enum,
 * transisi status), jadi aturan di prototype dan di server tidak bisa berbeda. Route di bawah
 * `/api/tenant/…` supaya tercakup `RouteGateTest` dan `RouteOwnershipTest`.
 */
internal object SpecRoutesWriter {

    fun routesFile(moduleId: String, t: SpecTable): String {
        val fn = SpecNaming.camel(t.schema) + "Routes"
        val path = "/api/tenant/modules/" + t.schema + "/" + t.table
        val dateCols = t.columns.filter { it.field.type == FieldType.DATE }
        val validatedTextCols = t.columns.filter { it.field.type == FieldType.TEXT && it.field.validation != TextValidation.NONE }
        return buildString {
            appendLine("package com.eventverse.app.routes")
            appendLine()
            listOf(
                "com.eventverse.app.domain.discovery.handoff.PrototypeRowRepository", "com.eventverse.app.domain.pack.DomainPackRegistry",
                "com.eventverse.app.domain.pack.ModuleId",
                "com.eventverse.app.domain.prototype.EntitySpec", "com.eventverse.app.domain.prototype.FieldSpec",
                "com.eventverse.app.domain.prototype.FieldType", "com.eventverse.app.domain.prototype.NumberFormat", "com.eventverse.app.domain.prototype.PrototypeAction",
                "com.eventverse.app.domain.prototype.PrototypeReducer", "com.eventverse.app.domain.prototype.PrototypeRow",
                "com.eventverse.app.domain.prototype.PrototypeSpec", "com.eventverse.app.domain.prototype.PrototypeStore",
                "com.eventverse.app.domain.prototype.StateMachine", "com.eventverse.app.domain.prototype.TextValidation", "com.eventverse.app.domain.rbac.AccessLevel",
                "com.eventverse.app.domain.rbac.ModuleAssignmentRepository", "com.eventverse.app.domain.rbac.RoleRepository",
                "com.eventverse.app.domain.tenant.TenantContext", "com.eventverse.app.plugins.tenantContextOrNull",
                "com.eventverse.app.shared.json.JsonParser", "com.eventverse.app.shared.json.jsonArrayOf",
                "com.eventverse.app.shared.json.jsonObjectOf", "com.eventverse.app.shared.json.jsonOf",
                "com.eventverse.app.shared.json.jsonStringMapOf", "io.ktor.http.ContentType", "io.ktor.http.HttpStatusCode",
                "io.ktor.server.application.ApplicationCall", "io.ktor.server.request.receiveText",
                "io.ktor.server.response.respond", "io.ktor.server.response.respondText", "io.ktor.server.routing.Route",
                "io.ktor.server.routing.delete", "io.ktor.server.routing.get", "io.ktor.server.routing.post",
                "io.ktor.server.routing.put", "io.ktor.server.routing.route", "kotlinx.datetime.LocalDate", "kotlinx.datetime.LocalDateTime", "java.util.UUID"
            ).forEach { appendLine("import $it") }
            appendLine()
            appendLine("// KANDIDAT PR (hasil generator) — modul ${SpecNaming.kString(moduleId)}. Setelah diterapkan milik tim.")
            appendLine("private val MODULE = ModuleId(${SpecNaming.kString(moduleId)})")
            appendLine("private const val ENTITY_ID = ${SpecNaming.kString(t.entity.id)}")
            appendLine("private const val ID_PREFIX = ${SpecNaming.kString(SpecNaming.ident(t.entity.id, "Entitas").take(20))}")
            appendLine("private val SPEC = PrototypeSpec(listOf(${entityLiteral(t.entity)}), emptyList())")
            appendLine("private val DATE_FIELDS = listOf<FieldSpec>(${dateCols.joinToString(", ") { "FieldSpec(" + SpecNaming.kString(it.field.key) + ", " + SpecNaming.kString(it.field.label) + ", FieldType.DATE" + (if (it.field.withTime) ", withTime = true" else "") + ")" }})")
            appendLine("private val TEXT_FIELDS = listOf<FieldSpec>(${validatedTextCols.joinToString(", ") { "FieldSpec(" + SpecNaming.kString(it.field.key) + ", " + SpecNaming.kString(it.field.label) + ", FieldType.TEXT, validation = TextValidation." + it.field.validation.name + ")" }})")
            appendLine()
            appendLine("/**")
            appendLine(" * CRUD ${t.entity.label}: baca = VIEW, tambah/ubah (termasuk pindah status) = OPERATE, hapus = MANAGE.")
            appendLine(" * Keputusan RBAC tak terhitung = 403 sebelum body dibaca (Kontrak 7).")
            appendLine(" */")
            appendLine("fun Route.$fn(")
            appendLine("    repository: PrototypeRowRepository,")
            appendLine("    roleRepository: RoleRepository,")
            appendLine("    moduleAssignmentRepository: ModuleAssignmentRepository")
            appendLine(") {")
            appendLine("    suspend fun ApplicationCall.authorized(required: AccessLevel): TenantContext? {")
            appendLine("        val tenant = tenantContextOrNull ?: run { respond(HttpStatusCode.NotFound, \"No tenant context found\"); return null }")
            appendLine("        // Modul tak dikenal proses ini (pack-nya tidak termuat) = keputusan RBAC tak bisa dihitung = 403 (fail-closed, Kontrak 7).")
            appendLine("        if (DomainPackRegistry.moduleDefinition(MODULE) == null) { respond(HttpStatusCode.Forbidden, \"Modul tidak tersedia.\"); return null }")
            appendLine("        // RBAC DULU: tanpa wewenang = 403 sebelum apa pun tentang isi tenant terungkap (RouteGateTest menegakkan ini).")
            appendLine("        val decision = moduleDecision(MODULE, tenant, roleRepository, moduleAssignmentRepository)")
            appendLine("        if (!requireModuleAccess(MODULE, decision, required)) return null")
            appendLine("        // Yang lolos RBAC (mis. owner, yang melewati matriks) tetap harus berada di tenant yang packnya memuat modul ini.")
            appendLine("        if (tenant.pack.module(MODULE) == null) { respond(HttpStatusCode.NotFound, \"Modul tidak tersedia untuk tenant ini.\"); return null }")
            appendLine("        return tenant")
            appendLine("    }")
            appendLine()
            appendLine("    /** `{\"values\":{...}}` → peta string; JSON tak sah = 400 dan `null`. */")
            appendLine("    suspend fun ApplicationCall.bodyValues(): Map<String, String>? {")
            appendLine("        val body = runCatching { JsonParser.parseObject(receiveText()) }.getOrNull()")
            appendLine("        if (body == null) { respond(HttpStatusCode.BadRequest, \"Body harus JSON objek.\"); return null }")
            appendLine("        return body.stringMap(\"values\")")
            appendLine("    }")
            appendLine()
            appendLine("    route(${SpecNaming.kString(path)}) {")
            appendLine("        get {")
            appendLine("            val tenant = call.authorized(AccessLevel.VIEW) ?: return@get")
            appendLine("            call.respondText(jsonArrayOf(repository.list(tenant.tenantId).map(::rowJson)).encode(), ContentType.Application.Json)")
            appendLine("        }")
            appendLine("        get(\"/{id}\") {")
            appendLine("            val tenant = call.authorized(AccessLevel.VIEW) ?: return@get")
            appendLine("            val row = repository.find(tenant.tenantId, call.parameters[\"id\"].orEmpty())")
            appendLine("                ?: return@get call.respond(HttpStatusCode.NotFound, \"Data tidak ditemukan.\")")
            appendLine("            call.respondText(rowJson(row).encode(), ContentType.Application.Json)")
            appendLine("        }")
            appendLine("        post {")
            appendLine("            val tenant = call.authorized(AccessLevel.OPERATE) ?: return@post")
            appendLine("            val values = call.bodyValues() ?: return@post")
            appendLine("            val row = PrototypeRow(ID_PREFIX + \"-\" + UUID.randomUUID(), values)")
            appendLine("            val problem = dateProblem(values) ?: textProblem(values)")
            appendLine("                ?: PrototypeReducer.reduce(SPEC, PrototypeStore(), PrototypeAction.Create(ENTITY_ID, row)).exceptionOrNull()?.message")
            appendLine("            if (problem != null) return@post call.respond(HttpStatusCode.BadRequest, problem)")
            appendLine("            repository.save(tenant.tenantId, row)")
            appendLine("            call.respondText(rowJson(row).encode(), ContentType.Application.Json, HttpStatusCode.Created)")
            appendLine("        }")
            appendLine("        put(\"/{id}\") {")
            appendLine("            val tenant = call.authorized(AccessLevel.OPERATE) ?: return@put")
            appendLine("            val id = call.parameters[\"id\"].orEmpty()")
            appendLine("            val values = call.bodyValues() ?: return@put")
            appendLine("            val current = repository.find(tenant.tenantId, id) ?: return@put call.respond(HttpStatusCode.NotFound, \"Data tidak ditemukan.\")")
            appendLine("            var problem = dateProblem(values) ?: textProblem(values)")
            appendLine("            var store = PrototypeStore(mapOf(ENTITY_ID to listOf(current)))")
            appendLine("            if (problem == null) {")
            appendLine("                for ((key, value) in values) {")
            appendLine("                    val next = PrototypeReducer.reduce(SPEC, store, PrototypeAction.SetField(ENTITY_ID, id, key, value))")
            appendLine("                    if (next.isFailure) { problem = next.exceptionOrNull()?.message; break }")
            appendLine("                    store = next.getOrThrow()")
            appendLine("                }")
            appendLine("            }")
            appendLine("            if (problem != null) return@put call.respond(HttpStatusCode.BadRequest, problem)")
            appendLine("            val updated = store.rowsOf(ENTITY_ID).single()")
            appendLine("            repository.save(tenant.tenantId, updated)")
            appendLine("            call.respondText(rowJson(updated).encode(), ContentType.Application.Json)")
            appendLine("        }")
            appendLine("        delete(\"/{id}\") {")
            appendLine("            val tenant = call.authorized(AccessLevel.MANAGE) ?: return@delete")
            appendLine("            val removed = repository.delete(tenant.tenantId, call.parameters[\"id\"].orEmpty())")
            appendLine("            if (!removed) return@delete call.respond(HttpStatusCode.NotFound, \"Data tidak ditemukan.\")")
            appendLine("            call.respondText(jsonObjectOf(\"deleted\" to jsonOf(true)).encode(), ContentType.Application.Json)")
            appendLine("        }")
            appendLine("    }")
            appendLine("}")
            appendLine()
            appendLine("private fun rowJson(row: PrototypeRow) = jsonObjectOf(\"id\" to jsonOf(row.id), \"values\" to jsonStringMapOf(row.values))")
            appendLine()
            appendLine("/** Field tanggal wajib ISO (TTTT-BB-HH, atau TTTT-BB-HHTJJ:MM bila withTime); kolom DATE/TIMESTAMP tidak menerima teks bebas. */")
            appendLine("private fun dateProblem(values: Map<String, String>): String? = DATE_FIELDS.firstNotNullOfOrNull { f ->")
            appendLine("    val v = values[f.key].orEmpty()")
            appendLine("    if (v.isBlank()) null")
            appendLine("    else if (f.withTime) {")
            appendLine("        val ok = v.length == 16 && v[10] == 'T' && runCatching { LocalDateTime.parse(v) }.isSuccess")
            appendLine("        if (ok) null else \"'\" + f.label + \"' harus berformat TTTT-BB-HHTJJ:MM.\"")
            appendLine("    } else if (runCatching { LocalDate.parse(v) }.isFailure) \"'\" + f.label + \"' harus berformat TTTT-BB-HH.\" else null")
            appendLine("}")
            appendLine()
            appendLine("/** Field TEXT berkunci validasi bentuk (email/telepon): nilai disimpan apa adanya, hanya bentuknya yang diperiksa (aturan tunggal `FieldSpec.accepts`). */")
            appendLine("private fun textProblem(values: Map<String, String>): String? = TEXT_FIELDS.firstNotNullOfOrNull { f ->")
            appendLine("    val v = values[f.key].orEmpty()")
            appendLine("    if (v.isNotBlank() && !f.accepts(v)) \"'\" + f.label + \"' bukan \" + f.validation.name.lowercase() + \" yang sah.\" else null")
            appendLine("}")
        }
    }

    /** Literal Kotlin `EntitySpec(...)` dengan seluruh teks di-escape ([SpecNaming.kString]). */
    fun entityLiteral(e: EntitySpec): String = buildString {
        append("EntitySpec(").append(SpecNaming.kString(e.id)).append(", ").append(SpecNaming.kString(e.label)).append(", listOf(")
        append(e.fields.joinToString(", ") { f ->
            "FieldSpec(" + SpecNaming.kString(f.key) + ", " + SpecNaming.kString(f.label) + ", FieldType." + f.type.name + ", listOf(" +
                f.options.joinToString(", ") { SpecNaming.kString(it) } + "), " + f.required +
                (if (f.format == NumberFormat.PLAIN) "" else ", NumberFormat." + f.format.name +
                    (f.currencyCode?.let { ", " + SpecNaming.kString(it) } ?: "")) +
                (if (f.withTime) ", withTime = true" else "") +
                (if (f.validation != TextValidation.NONE) ", validation = TextValidation." + f.validation.name else "") + ")"
        })
        append(")")
        e.stateMachine?.let { sm ->
            append(", StateMachine(").append(SpecNaming.kString(sm.field)).append(", mapOf(")
            append(sm.transitions.entries.joinToString(", ") { (from, tos) ->
                SpecNaming.kString(from) + " to setOf(" + tos.joinToString(", ") { SpecNaming.kString(it) } + ")"
            })
            append("))")
        }
        append(")")
    }
}

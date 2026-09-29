package com.eventverse.app.routes

import com.eventverse.app.domain.rbac.AccessLevel
import com.eventverse.app.domain.rbac.BusinessModule
import com.eventverse.app.domain.rbac.DataScope
import com.eventverse.app.domain.rbac.DepartmentModuleAssignment
import com.eventverse.app.domain.rbac.ModuleAssignmentRepository
import com.eventverse.app.plugins.tenantContextOrNull
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/**
 * Penugasan modul ke divisi — sumbu kedua wewenang, di samping jabatan.
 *
 * Sebelumnya konfigurasi ini hanya hidup di state view model: layarnya bisa diubah, terlihat
 * tersimpan, dan hilang begitu halaman dimuat ulang.
 */
fun Route.moduleAssignmentRoutes(assignmentRepository: ModuleAssignmentRepository, roleRepository: com.eventverse.app.domain.rbac.RoleRepository) {

    route("/api/tenant/module-assignments") {
        // B5: tulis = MANAGE fail-closed. Baca belum digerbang: klien menghitung menu setiap pengguna dari daftar ini
        // (RbacAccessPolicyRepository). Tutup setelah endpoint "wewenang saya" dihitung di server.
        moduleGate(BusinessModule.DYNAMIC_RBAC, roleRepository, assignmentRepository, read = com.eventverse.app.domain.rbac.AccessLevel.NONE)

        get {
            val tenant = call.tenantContextOrNull ?: run {
                call.respond(HttpStatusCode.NotFound, "No tenant context found")
                return@get
            }
            val grouped = assignmentRepository.findAllByTenant(tenant.tenantId)
            call.respondText(assignmentsJson(grouped), contentType = ContentType.Application.Json)
        }

        put {
            val tenant = call.tenantContextOrNull ?: run {
                call.respond(HttpStatusCode.NotFound, "No tenant context found")
                return@put
            }

            val body = call.receiveText()
            val module = stringField(body, "module")
                ?.let { name -> runCatching { BusinessModule.valueOf(name) }.getOrNull() }
                ?: run {
                    call.respond(HttpStatusCode.BadRequest, "Field 'module' tidak valid")
                    return@put
                }
            val departmentId = stringField(body, "departmentId") ?: run {
                call.respond(HttpStatusCode.BadRequest, "Field 'departmentId' wajib diisi")
                return@put
            }

            val assignment = DepartmentModuleAssignment(
                departmentId = departmentId,
                departmentName = stringField(body, "departmentName") ?: departmentId,
                accessLevel = stringField(body, "accessLevel")
                    ?.let { runCatching { AccessLevel.valueOf(it) }.getOrNull() }
                    ?: AccessLevel.OPERATE,
                scope = stringField(body, "scope")
                    ?.let { runCatching { DataScope.valueOf(it) }.getOrNull() }
                    ?: DataScope.ALL_TENANT_DATA,
                specificRoleIds = stringArrayField(body, "specificRoleIds"),
                allowedDesks = optionalStringArrayField(body, "allowedDesks")
                    // Array kosong berarti "tanpa batasan meja", sama dengan field tidak dikirim.
                    ?.takeIf { it.isNotEmpty() },
                id = stringField(body, "id") ?: ""
            )

            assignmentRepository.upsert(tenant.tenantId, module, assignment)
                .onSuccess {
                    call.respondText(
                        assignmentJson(module, it),
                        contentType = ContentType.Application.Json
                    )
                }
                .onFailure {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        it.message ?: "Gagal menyimpan penugasan modul"
                    )
                }
        }

        delete("/{module}/{assignmentKey}") {
            val tenant = call.tenantContextOrNull ?: run {
                call.respond(HttpStatusCode.NotFound, "No tenant context found")
                return@delete
            }
            val module = call.parameters["module"]
                ?.let { name -> runCatching { BusinessModule.valueOf(name) }.getOrNull() }
                ?: run {
                    call.respond(HttpStatusCode.BadRequest, "Modul tidak valid")
                    return@delete
                }
            val assignmentKey = call.parameters["assignmentKey"] ?: run {
                call.respond(HttpStatusCode.BadRequest, "Kunci penugasan wajib diisi")
                return@delete
            }

            assignmentRepository.remove(tenant.tenantId, module, assignmentKey)
                .onSuccess { call.respond(HttpStatusCode.NoContent) }
                .onFailure {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        it.message ?: "Gagal menghapus penugasan modul"
                    )
                }
        }
    }
}

private fun assignmentsJson(grouped: Map<BusinessModule, List<DepartmentModuleAssignment>>): String {
    val entries = grouped.entries.joinToString(",") { (module, assignments) ->
        "\"${module.name}\":[${assignments.joinToString(",") { bodyJson(it) }}]"
    }
    return "{$entries}"
}

private fun assignmentJson(module: BusinessModule, assignment: DepartmentModuleAssignment): String =
    "{\"module\":\"${module.name}\",\"assignment\":${bodyJson(assignment)}}"

private fun bodyJson(assignment: DepartmentModuleAssignment): String {
    val roleIds = assignment.specificRoleIds.sorted().joinToString(",") { "\"${escape(it)}\"" }
    val desks = assignment.allowedDesks.orEmpty().sorted().joinToString(",") { "\"${escape(it)}\"" }
    return "{\"id\":\"${escape(assignment.id)}\"," +
        "\"departmentId\":\"${escape(assignment.departmentId)}\"," +
        "\"departmentName\":\"${escape(assignment.departmentName)}\"," +
        "\"accessLevel\":\"${assignment.accessLevel.name}\"," +
        "\"scope\":\"${assignment.scope.name}\"," +
        "\"specificRoleIds\":[$roleIds]," +
        "\"allowedDesks\":[$desks]}"
}

private fun escape(raw: String): String =
    raw.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "")

private fun stringField(json: String, field: String): String? =
    "\"$field\"\\s*:\\s*\"([^\"]*)\"".toRegex().find(json)?.groupValues?.get(1)?.ifBlank { null }

private fun stringArrayField(json: String, field: String): Set<String> {
    val raw = "\"$field\"\\s*:\\s*\\[([^\\]]*)\\]".toRegex().find(json)?.groupValues?.get(1)
        ?: return emptySet()
    return "\"([^\"]+)\"".toRegex().findAll(raw).map { it.groupValues[1] }.toSet()
}

/** Sama dengan [stringArrayField], tapi `null` saat field tidak dikirim sama sekali. */
private fun optionalStringArrayField(json: String, field: String): Set<String>? {
    val raw = "\"$field\"\\s*:\\s*\\[([^\\]]*)\\]".toRegex().find(json)?.groupValues?.get(1) ?: return null
    return "\"([^\"]+)\"".toRegex().findAll(raw).map { it.groupValues[1] }.toSet()
}

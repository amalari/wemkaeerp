package com.eventverse.app.routes

import com.eventverse.app.domain.rbac.ModuleAssignmentRepository
import com.eventverse.app.domain.rbac.RoleRepository
import com.eventverse.app.plugins.tenantContextOrNull
import com.eventverse.app.shared.rbac.AccessDecisionCodec
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

/**
 * `GET /api/tenant/me/access` — wewenang **pemanggil sendiri** per modul, dihitung di server (B5).
 *
 * Sebelumnya klien mengunduh daftar jabatan & penugasan divisi **semua orang** lalu menghitung menunya sendiri. Artinya
 * matriks wewenang pabrik terbaca oleh setiap anggota tenant, dan server tidak bisa menggerbang daftar itu tanpa
 * mengosongkan menu semua orang. Dengan endpoint ini, daftar tersebut hanya untuk admin RBAC/Org Chart.
 */
fun Route.myAccessRoutes(roleRepository: RoleRepository, moduleAssignmentRepository: ModuleAssignmentRepository) {
    get("/api/tenant/me/access") {
        val tenant = call.tenantContextOrNull ?: return@get call.respond(HttpStatusCode.NotFound, "No tenant context found")
        val decisions = call.callerDecisions(tenant, roleRepository, moduleAssignmentRepository)
        call.respondText(AccessDecisionCodec.encode(decisions).encode(), ContentType.Application.Json)
    }
}

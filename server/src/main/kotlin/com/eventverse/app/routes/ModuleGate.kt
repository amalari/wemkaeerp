package com.eventverse.app.routes

import com.eventverse.app.domain.rbac.AccessLevel
import com.eventverse.app.domain.rbac.BusinessModule
import com.eventverse.app.domain.rbac.ModuleAssignmentRepository
import com.eventverse.app.domain.rbac.RoleRepository
import com.eventverse.app.plugins.tenantContextOrNull
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.request.httpMethod
import io.ktor.server.response.respond
import io.ktor.server.routing.Route

/**
 * Gerbang RBAC **per grup route** (B5). Dipasang sekali di `route("/api/tenant/…") { moduleGate(…) }` dan berlaku
 * untuk setiap handler di bawahnya — termasuk handler yang ditambahkan kelak — **sebelum** body dibaca atau data
 * dicari. Menambal handler satu per satu mudah terlewat; itulah asal 151 route tanpa gerbang.
 *
 * - Baca (`GET`/`HEAD`) butuh [read]; tulis butuh [write] (atau [writeFor] per path).
 * - Keputusan memakai [moduleDecision]: pemanggil tanpa identitas atau tanpa jabatan yang memberi akses = `NONE`,
 *   jadi tulis selalu **fail-closed** (tenant-variability-rules Kontrak 7). Owner/superadmin tanpa jabatan
 *   tenant melewati matriks, sama seperti guard lain.
 * - [alsoAllowed]: modul lain yang juga boleh (aturan "atau"), mis. aksi lantai SPK oleh Operator Exec.
 */
internal fun Route.moduleGate(
    module: BusinessModule,
    roleRepository: RoleRepository,
    moduleAssignmentRepository: ModuleAssignmentRepository,
    read: AccessLevel = AccessLevel.VIEW,
    write: AccessLevel = AccessLevel.MANAGE,
    alsoAllowed: List<BusinessModule> = emptyList(),
    writeFor: (path: String) -> AccessLevel? = { null }
) {
    install(
        createRouteScopedPlugin("ModuleGate-${module.code}-${hashCode()}") {
            onCall { call ->
                val tenant = call.tenantContextOrNull ?: return@onCall // handler menjawab 404 seperti biasa
                val isRead = call.request.httpMethod == HttpMethod.Get || call.request.httpMethod == HttpMethod.Head
                val required = if (isRead) read else writeFor(call.request.local.uri.substringBefore('?')) ?: write
                if (required == AccessLevel.NONE) return@onCall // baca sengaja belum digerbang (lihat pemanggil)
                val allowed = (listOf(module) + alsoAllowed).any { m ->
                    call.moduleDecision(m, tenant, roleRepository, moduleAssignmentRepository).config.level.isAtLeast(required)
                }
                if (!allowed) {
                    call.respond(
                        HttpStatusCode.Forbidden,
                        "Butuh wewenang ${required.displayName} atas modul \"${module.displayName}\"" +
                            alsoAllowed.joinToString("") { " atau \"${it.displayName}\"" } + "."
                    )
                }
            }
        }
    )
}

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
 * Syarat satu panggilan: [level] atas **salah satu** [modules] (aturan "atau").
 * `level = NONE` berarti sengaja tidak digerbang — pemanggil wajib menulis alasannya.
 */
internal data class GateRule(val level: AccessLevel, val modules: List<BusinessModule>)

/**
 * Gerbang RBAC **per grup route** (B5). Dipasang sekali di `route("/api/tenant/…") { moduleGate(…) }` dan berlaku
 * untuk setiap handler di bawahnya — termasuk handler yang ditambahkan kelak — **sebelum** body dibaca atau data
 * dicari. Menambal handler satu per satu mudah terlewat; itulah asal 151 route tanpa gerbang.
 *
 * - Default: baca (`GET`/`HEAD`) butuh [read], tulis butuh [write], atas [module] atau [alsoAllowed].
 * - [ruleFor] menimpa default per path (mis. harga bahan hanya untuk Master Data/Costing). Path dicocokkan
 *   relatif terhadap grup, tanpa query string. Kembalikan `null` untuk memakai default.
 * - Keputusan memakai [moduleDecision]: tanpa identitas atau tanpa jabatan yang memberi akses = `NONE`, jadi tulis
 *   selalu **fail-closed** (tenant-variability-rules Kontrak 7). Owner/superadmin tanpa jabatan tenant lolos.
 */
internal fun Route.moduleGate(
    module: BusinessModule,
    roleRepository: RoleRepository,
    moduleAssignmentRepository: ModuleAssignmentRepository,
    read: AccessLevel = AccessLevel.VIEW,
    write: AccessLevel = AccessLevel.MANAGE,
    alsoAllowed: List<BusinessModule> = emptyList(),
    ruleFor: (method: HttpMethod, path: String) -> GateRule? = { _, _ -> null }
) {
    install(
        createRouteScopedPlugin("ModuleGate-${module.code}-${hashCode()}") {
            onCall { call ->
                val tenant = call.tenantContextOrNull ?: return@onCall // handler menjawab 404 seperti biasa
                val method = call.request.httpMethod
                val isRead = method == HttpMethod.Get || method == HttpMethod.Head
                val rule = ruleFor(method, call.request.local.uri.substringBefore('?'))
                    ?: GateRule(if (isRead) read else write, listOf(module) + alsoAllowed)
                if (rule.level == AccessLevel.NONE) return@onCall // sengaja tidak digerbang (lihat pemanggil)
                val allowed = rule.modules.any { m ->
                    call.moduleDecision(m, tenant, roleRepository, moduleAssignmentRepository).config.level.isAtLeast(rule.level)
                }
                if (!allowed) {
                    call.respond(
                        HttpStatusCode.Forbidden,
                        "Butuh wewenang ${rule.level.displayName} atas modul " +
                            rule.modules.joinToString(" atau ") { "\"${it.displayName}\"" } + "."
                    )
                }
            }
        }
    )
}

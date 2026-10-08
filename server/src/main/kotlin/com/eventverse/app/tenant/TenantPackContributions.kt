package com.eventverse.app.tenant

import com.eventverse.app.domain.pack.DomainPack
import com.eventverse.app.domain.pack.ModuleId
import com.eventverse.app.domain.pack.tenant.layanan.LayananPilotPack
import com.eventverse.app.domain.rbac.ModuleAssignmentRepository
import com.eventverse.app.domain.rbac.RoleRepository
import com.eventverse.app.tenant.layanan.PostgresLayananChangeRequestRepository
import com.eventverse.app.tenant.layanan.layananChangeRequestRoutes
import io.ktor.server.routing.Route

/**
 * Registri **kontribusi modul khusus tenant (J3)** — TRD-PLAT-004 P2.
 *
 * Satu-satunya tempat di luar paket J3 (`…pack.tenant.*` dan `…tenant.<pack>.*`) yang boleh mengimpor kode J3.
 * Mesin (`RouteOwnership`, `ModuleSchemaMap`, `DomainRouteWiring`) membaca registri ini; ia tidak menyebut pack
 * tenant mana pun. Menambah modul tenant baru = menambah satu entri di sini, tanpa menyentuh mesin.
 * Dijaga `TenantCodeBoundaryTest` (tes memblokir).
 */
object TenantPackContributions {

    class Contribution(
        val pack: DomainPack,
        /** Tabel per modul (kunci [com.eventverse.app.infrastructure.ModuleSchemaMap.byModule]). */
        val tables: Map<ModuleId, Set<String>>,
        /** Awalan route `/api/tenant/...` → modul pemiliknya (kunci `RouteOwnership`). */
        val routePrefixes: Map<String, ModuleId>,
        /** Mendaftarkan route modul ke aplikasi. */
        val registerRoutes: Route.(RoleRepository, ModuleAssignmentRepository) -> Unit
    )

    val all: List<Contribution> = listOf(
        Contribution(
            pack = LayananPilotPack.pack,
            tables = mapOf(LayananPilotPack.CHANGE_REQUEST to setOf("change_requests")),
            routePrefixes = mapOf("/api/tenant/modules/layanan_change_request" to LayananPilotPack.CHANGE_REQUEST),
            registerRoutes = { roles, assignments ->
                layananChangeRequestRoutes(
                    PostgresLayananChangeRequestRepository(), roles, assignments
                )
            }
        )
    )

    val tables: Map<ModuleId, Set<String>> get() = all.flatMap { it.tables.entries }.associate { it.key to it.value }
    val routePrefixes: List<Pair<String, ModuleId>> get() = all.flatMap { c -> c.routePrefixes.map { it.key to it.value } }
}

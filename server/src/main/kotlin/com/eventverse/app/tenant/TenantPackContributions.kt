package com.eventverse.app.tenant

import com.eventverse.app.domain.discovery.handoff.PrototypeRowRepository
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
        /**
         * Penyimpan baris per **kode modul** milik pack ini — satu-satunya sumber baris produksi modul hasil
         * generate (unduh FILE generik + resolver RELATION, TRD-FIELD-004 FR-2.3). Wajib eksplisit (tanpa default):
         * modul tanpa entri = fail-closed (404 / `false`), bukan fallback. Instans yang sama dipakai route modul.
         */
        val rows: Map<String, PrototypeRowRepository>,
        /** Mendaftarkan route modul ke aplikasi. */
        val registerRoutes: Route.(RoleRepository, ModuleAssignmentRepository) -> Unit
    )

    // Satu instans per modul: route-nya dan registri sumber baris memakai penyimpan yang sama.
    private val layananChangeRequestRows = PostgresLayananChangeRequestRepository()

    val all: List<Contribution> = listOf(
        Contribution(
            pack = LayananPilotPack.pack,
            tables = mapOf(LayananPilotPack.CHANGE_REQUEST to setOf("change_requests")),
            routePrefixes = mapOf("/api/tenant/modules/layanan_change_request" to LayananPilotPack.CHANGE_REQUEST),
            rows = mapOf(LayananPilotPack.CHANGE_REQUEST.value to layananChangeRequestRows),
            registerRoutes = { roles, assignments ->
                layananChangeRequestRoutes(
                    layananChangeRequestRows, roles, assignments
                )
            }
        )
    )

    val tables: Map<ModuleId, Set<String>> get() = all.flatMap { it.tables.entries }.associate { it.key to it.value }
    val routePrefixes: List<Pair<String, ModuleId>> get() = all.flatMap { c -> c.routePrefixes.map { it.key to it.value } }

    /** Sumber baris produksi seluruh kontribusi terdaftar (kode modul → penyimpan). */
    val rows: Map<String, PrototypeRowRepository> get() = mergeRows(all)

    /**
     * Menggabungkan [Contribution.rows] semua [contributions], lalu menimpanya dengan [overrides] (titik injeksi
     * tes — menang atas produksi). Fail-loud: kunci yang bukan modul pack kontribusinya, atau modul yang
     * didaftarkan dua kali, ditolak — tidak ada penimpaan diam-diam antar kontribusi.
     */
    fun mergeRows(
        contributions: List<Contribution>,
        overrides: Map<String, PrototypeRowRepository> = emptyMap()
    ): Map<String, PrototypeRowRepository> {
        val merged = LinkedHashMap<String, PrototypeRowRepository>()
        contributions.forEach { c ->
            c.rows.forEach { (code, repo) ->
                require(c.pack.module(ModuleId(code)) != null) { "Sumber baris '$code' bukan modul pack ${c.pack.code.value}" }
                require(code !in merged) { "Sumber baris modul '$code' terdaftar lebih dari satu kontribusi" }
                merged[code] = repo
            }
        }
        merged.putAll(overrides)
        return merged
    }
}

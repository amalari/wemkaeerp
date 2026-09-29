package com.eventverse.app.routes

import com.eventverse.app.TestAuth
import com.eventverse.app.domain.auth.Role
import com.eventverse.app.domain.pack.GarmentBlueprints
import com.eventverse.app.domain.tenant.SubscriptionTier
import com.eventverse.app.domain.tenant.Tenant
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.domain.tenant.TenantName
import com.eventverse.app.domain.tenant.TenantSlug
import com.eventverse.app.domain.tenant.TenantStatus
import com.eventverse.app.infrastructure.DatabaseFactory
import com.eventverse.app.infrastructure.InMemoryDepartmentRepository
import com.eventverse.app.infrastructure.InMemoryEmployeeRepository
import com.eventverse.app.infrastructure.InMemoryModuleAssignmentRepository
import com.eventverse.app.infrastructure.InMemoryRoleRepository
import com.eventverse.app.infrastructure.InMemoryTenantEntitlementRepository
import com.eventverse.app.infrastructure.InMemoryTenantPipelineRepository
import com.eventverse.app.infrastructure.InMemoryTenantRepository
import com.eventverse.app.module
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.server.application.ApplicationStarted
import io.ktor.server.application.pluginOrNull
import io.ktor.server.routing.RoutingRoot
import io.ktor.server.routing.getAllRoutes
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * **B5 — gerbang keamanan** (prasyarat B6). Setiap route `/api/tenant/…` dipanggil sebagai anggota tenant
 * **tanpa wewenang modul apa pun** (jabatan tak dikenal, bukan admin). Route yang tidak menjawab 401/403 harus
 * tercatat di [RouteGateLedger]; kalau tidak, test merah.
 *
 * Kenapa 400/404 juga dihitung "tidak menolak": gerbang harus jalan **sebelum** body dibaca atau resource
 * dicari. 404 untuk orang tak berwenang berarti handler sudah menyentuh data.
 *
 * Integration test: butuh Postgres (repository default modul), sama seperti test `Postgres*IntegrationTest`.
 */
class RouteGateTest {

    private val slug = "gate-probe"

    @Test
    fun everyTenantRoute_deniesMemberWithoutModuleAccess_orIsInLedger() = testApplication {
        DatabaseFactory.init()
        val tenants = InMemoryTenantRepository()
        runBlocking {
            tenants.save(
                Tenant(TenantId("ten-gate"), TenantSlug(slug), TenantName("Gate Probe"), TenantStatus.ACTIVE,
                    SubscriptionTier.PRO, businessPreset = GarmentBlueprints.CMT_MAKLOON)
            )
        }
        var routes = emptyList<String>()
        application {
            module(
                tenantRepository = tenants,
                pipelineRepository = InMemoryTenantPipelineRepository(),
                entitlementRepository = InMemoryTenantEntitlementRepository(),
                roleRepository = InMemoryRoleRepository(),
                moduleAssignmentRepository = InMemoryModuleAssignmentRepository(),
                departmentRepository = InMemoryDepartmentRepository(),
                employeeRepository = InMemoryEmployeeRepository()
            )
            monitor.subscribe(ApplicationStarted) {
                routes = pluginOrNull(RoutingRoot)?.getAllRoutes().orEmpty().mapNotNull(::methodAndPath)
                    .filter { it.substringAfter(' ').startsWith("/api/tenant") }.distinct()
            }
        }
        startApplication()
        assertTrue(routes.size > 150, "Enumerasi route gagal: hanya ${routes.size} route")

        val token = TestAuth.staffToken(slug, customRoleId = "role-tanpa-akses", role = Role.OPERATOR)
        val notDenied = routes.filter { route ->
            val (method, path) = route.split(' ', limit = 2)
            val status = runCatching {
                client.request(path.replace(Regex("\\{[^}]+}"), "x1")) {
                    this.method = HttpMethod.parse(method)
                    header("X-Tenant-Slug", slug)
                    header(HttpHeaders.Authorization, "Bearer $token")
                    contentType(ContentType.Application.Json)
                    if (method != "GET") setBody("{}")
                }.status.value
            }.getOrDefault(-1)
            status != 401 && status != 403
        }.toSet()

        val allowed = RouteGateLedger.ungated + RouteGateLedger.openByDesign
        val newlyUngated = notDenied - allowed
        val nowGated = RouteGateLedger.ungated - notDenied
        val problems = buildList {
            if (newlyUngated.isNotEmpty()) add(
                "Route tanpa gerbang RBAC — tambahkan requireModuleAccess sebelum body dibaca:\n" +
                    newlyUngated.sorted().joinToString("\n") { "  $it" }
            )
            if (nowGated.isNotEmpty()) add(
                "Route sudah menolak (atau hilang) — hapus dari RouteGateLedger.ungated:\n" +
                    nowGated.sorted().joinToString("\n") { "  $it" }
            )
        }
        if (problems.isNotEmpty()) fail(problems.joinToString("\n\n"))
    }

    /** `/api/tenant/x/(method:GET)` → `GET /api/tenant/x`. */
    private fun methodAndPath(route: io.ktor.server.routing.RoutingNode): String? {
        val raw = route.toString()
        val method = Regex("\\(method:(\\w+)\\)").find(raw)?.groupValues?.get(1) ?: return null
        val path = raw.replace(Regex("/\\(method:\\w+\\)"), "")
            .replace(Regex("/\\[[^]]*]"), "")
            .replace(Regex("/\\(authenticate[^)]*\\)"), "")
        return "$method $path"
    }
}

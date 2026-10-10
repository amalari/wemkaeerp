package com.eventverse.app

import com.eventverse.app.domain.auth.Role
import com.eventverse.app.domain.pack.GarmentBlueprints
import com.eventverse.app.domain.pack.GarmentModules
import com.eventverse.app.domain.rbac.AccessLevel
import com.eventverse.app.domain.rbac.CustomRole
import com.eventverse.app.domain.rbac.DataScope
import com.eventverse.app.domain.rbac.ModuleAccessConfig
import com.eventverse.app.domain.rbac.RoleId
import com.eventverse.app.domain.stageflow.IndustryTemplateCode
import com.eventverse.app.domain.tenant.SubscriptionTier
import com.eventverse.app.domain.tenant.Tenant
import com.eventverse.app.domain.tenant.TenantContext
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.domain.tenant.TenantName
import com.eventverse.app.domain.tenant.TenantSlug
import com.eventverse.app.domain.tenant.TenantStatus
import com.eventverse.app.infrastructure.InMemoryDepartmentRepository
import com.eventverse.app.infrastructure.InMemoryEmployeeRepository
import com.eventverse.app.infrastructure.InMemoryModuleAssignmentRepository
import com.eventverse.app.infrastructure.InMemoryRoleRepository
import com.eventverse.app.infrastructure.InMemoryTenantRepository
import com.eventverse.app.routes.factoryFlowDecision
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.*
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * TRD-PLAT-012: gerbang Factory Flow (pipeline, stage-flow, locations) fail-closed bagi token tanpa
 * identitas pabrik. Pola mengikuti [OrgChartAccessApiTest].
 *
 * Dua lapis dijaga: gerbang terpusat `TenantRouteGatePolicy` (baca) dan guard handler `factoryFlowDecision`
 * (baca + tulis). Dulu `GET /api/tenant/locations` hanya punya lapis kedua, yang `null`-permisif untuk token
 * tanpa jabatan/divisi: SALES/OPERATOR tanpa identitas menerima 200.
 *
 * Tes dijalankan di tenant garmen (`pabrik-ff`) dan tenant non-rajut (`bordir-uji`, template bordir).
 */
class FactoryFlowAccessApiTest {

    private val garmentSlug = "pabrik-ff"
    private val embroiderySlug = "bordir-uji"
    private val garmentId = TenantId("ten-ff-1")
    private val embroideryId = TenantId("ten-ff-2")
    private val allSlugs = listOf(garmentSlug, embroiderySlug)

    private val reads = listOf(
        "/api/tenant/locations", "/api/tenant/pipeline", "/api/tenant/pipeline/modules",
        "/api/tenant/pipeline/telemetry", "/api/tenant/stage-flow"
    )
    private val writes = listOf(
        HttpMethod.Put to "/api/tenant/locations",
        HttpMethod.Put to "/api/tenant/pipeline",
        HttpMethod.Post to "/api/tenant/pipeline/reset",
        HttpMethod.Post to "/api/tenant/stage-flow/reset",
        HttpMethod.Post to "/api/tenant/stage-flow/stages"
    )

    private fun tenantRepo() = InMemoryTenantRepository().also { repo ->
        runBlocking {
            repo.save(Tenant(garmentId, TenantSlug(garmentSlug), TenantName("Pabrik FF"), TenantStatus.ACTIVE,
                SubscriptionTier.PRO, businessPreset = GarmentBlueprints.CMT_MAKLOON))
            repo.save(Tenant(embroideryId, TenantSlug(embroiderySlug), TenantName("Bordir Uji"), TenantStatus.ACTIVE,
                SubscriptionTier.PRO, businessPreset = GarmentBlueprints.CMT_MAKLOON,
                industryTemplate = IndustryTemplateCode.EMBROIDERY))
        }
    }

    private fun role(tenantId: TenantId, id: String, level: AccessLevel) = CustomRole(
        id = RoleId(id), tenantId = tenantId, name = "Jabatan $id", description = "", departmentId = null,
        modulePermissions = mapOf(GarmentModules.FACTORY_FLOW to ModuleAccessConfig(level, DataScope.ALL_TENANT_DATA))
    )

    private fun ApplicationTestBuilder.installApp() {
        val roles = InMemoryRoleRepository()
        runBlocking {
            listOf(garmentId, embroideryId).forEach { t ->
                roles.save(role(t, "ff-none-${t.value}", AccessLevel.NONE))
                roles.save(role(t, "ff-view-${t.value}", AccessLevel.VIEW))
                roles.save(role(t, "ff-manage-${t.value}", AccessLevel.MANAGE))
            }
        }
        application {
            module(
                tenantRepository = tenantRepo(), roleRepository = roles,
                pipelineRepository = com.eventverse.app.infrastructure.InMemoryTenantPipelineRepository(),
                entitlementRepository = com.eventverse.app.infrastructure.InMemoryTenantEntitlementRepository(),
                moduleAssignmentRepository = InMemoryModuleAssignmentRepository(),
                departmentRepository = InMemoryDepartmentRepository(), employeeRepository = InMemoryEmployeeRepository()
            )
        }
    }

    private fun idOf(slug: String) = if (slug == garmentSlug) garmentId else embroideryId

    private suspend fun ApplicationTestBuilder.call(method: HttpMethod, path: String, slug: String, token: String): HttpStatusCode =
        client.request(path) {
            this.method = method
            header("X-Tenant-Slug", slug)
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            if (method != HttpMethod.Get) setBody("{}")
        }.status

    /**
     * "Lolos gerbang" = bukan 401/403. Repositori kerangka tahap bawaan Postgres dan tenant uji ini tidak ada di
     * DB, jadi isi balasan (mis. 500 FK) bukan urusan tes gerbang.
     */
    private fun assertAllowed(status: HttpStatusCode, message: String) {
        assertTrue(status != HttpStatusCode.Forbidden && status != HttpStatusCode.Unauthorized, "$message dapat $status")
    }

    // ── Token tanpa identitas pabrik, peran bukan Owner (AC1) ─────────────────────────────────

    @Test
    fun readsAndWrites_withNoIdentityNonOwnerToken_shouldBeForbidden() = testApplication {
        installApp()
        for (slug in allSlugs) for (role in listOf(Role.SALES, Role.OPERATOR)) {
            val token = TestAuth.tenantToken(slug, role)
            reads.forEach { assertEquals(HttpStatusCode.Forbidden, call(HttpMethod.Get, it, slug, token), "GET $it $slug $role") }
            writes.forEach { (m, p) -> assertEquals(HttpStatusCode.Forbidden, call(m, p, slug, token), "$m $p $slug $role") }
        }
    }

    // ── Owner & superadmin (AC2) ──────────────────────────────────────────────────────────────

    @Test
    fun readsAndWrites_withNoIdentityOwnerToken_shouldPass() = testApplication {
        installApp()
        for (slug in allSlugs) {
            val token = TestAuth.tenantToken(slug, Role.TENANT_ADMIN)
            reads.forEach { assertAllowed(call(HttpMethod.Get, it, slug, token), "GET $it $slug owner") }
            assertEquals(HttpStatusCode.OK, call(HttpMethod.Get, "/api/tenant/locations", slug, token))
            writes.forEach { (m, p) ->
                val s = call(m, p, slug, token)
                assertNotEquals(HttpStatusCode.Forbidden, s, "$m $p $slug owner")
                assertNotEquals(HttpStatusCode.Unauthorized, s, "$m $p $slug owner")
            }
        }
    }

    @Test
    fun locations_withSuperadminActingAs_shouldPass() = testApplication {
        installApp()
        val status = client.get("/api/tenant/locations") { asSuperadminActingAs(garmentSlug) }.status
        assertEquals(HttpStatusCode.OK, status)
    }

    // ── Jabatan VIEW / NONE / MANAGE (AC3) ────────────────────────────────────────────────────

    @Test
    fun roles_viewReadsButCannotWrite_noneIsDenied_manageWrites() = testApplication {
        installApp()
        for (slug in allSlugs) {
            val t = idOf(slug).value
            val view = TestAuth.staffToken(slug, "ff-view-$t")
            val none = TestAuth.staffToken(slug, "ff-none-$t")
            val manage = TestAuth.staffToken(slug, "ff-manage-$t")
            reads.forEach {
                assertAllowed(call(HttpMethod.Get, it, slug, view), "VIEW GET $it $slug")
                assertEquals(HttpStatusCode.Forbidden, call(HttpMethod.Get, it, slug, none), "NONE GET $it $slug")
            }
            writes.forEach { (m, p) ->
                assertEquals(HttpStatusCode.Forbidden, call(m, p, slug, view), "VIEW $m $p $slug")
                assertEquals(HttpStatusCode.Forbidden, call(m, p, slug, none), "NONE $m $p $slug")
                assertNotEquals(HttpStatusCode.Forbidden, call(m, p, slug, manage), "MANAGE $m $p $slug")
            }
        }
    }

    // ── Isolasi tenant ────────────────────────────────────────────────────────────────────────

    @Test
    fun locations_withTokenOfAnotherTenant_shouldBeRejected() = testApplication {
        installApp()
        val status = client.get("/api/tenant/locations") { asTenantTargeting(garmentSlug, embroiderySlug) }.status
        assertTrue(
            status == HttpStatusCode.Forbidden || status == HttpStatusCode.NotFound || status == HttpStatusCode.Unauthorized,
            "Token tenant lain tidak boleh membaca lokasi, dapat $status"
        )
    }

    @Test
    fun roleOfAnotherTenant_shouldNotGrantAccess() = testApplication {
        installApp()
        // Jabatan MANAGE milik tenant garmen dibawa ke tenant bordir: ID jabatan tak ada di sana -> NONE.
        val token = TestAuth.staffToken(embroiderySlug, "ff-manage-${garmentId.value}")
        assertEquals(HttpStatusCode.Forbidden, call(HttpMethod.Get, "/api/tenant/locations", embroiderySlug, token))
    }

    // ── Karakterisasi: repositori wewenang tidak terpasang (TRD-PLAT-012 §3 Availability) ─────

    @Test
    fun factoryFlowDecision_withoutAuthorizationRepositories_staysNull() = testApplication {
        val ctx = TenantContext(garmentId, TenantSlug(garmentSlug), SubscriptionTier.PRO, true)
        var decision: Any? = "belum"
        application {
            routing {
                get("/probe") {
                    decision = call.factoryFlowDecision(ctx, null, null)
                    call.respondText("ok")
                }
            }
        }
        client.get("/probe")
        assertNull(decision, "Tanpa repositori wewenang keputusan tak terhitung (null) dan guard meloloskan — disengaja")
    }
}

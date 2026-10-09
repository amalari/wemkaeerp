package com.eventverse.app

import com.eventverse.app.domain.auth.Role
import com.eventverse.app.domain.auth.UserId
import com.eventverse.app.domain.discovery.DeterministicDiscoveryAgent
import com.eventverse.app.domain.discovery.DiscoveryDraftId
import com.eventverse.app.domain.discovery.DiscoveryRequest
import com.eventverse.app.domain.discovery.usecases.CreateDiscoveryDraftUseCase
import com.eventverse.app.domain.discovery.usecases.HandoffDiscoveryDraftUseCase
import com.eventverse.app.domain.discovery.usecases.LockDiscoveryDraftUseCase
import com.eventverse.app.domain.pack.DomainPackCode
import com.eventverse.app.domain.pack.DomainPackRegistry
import com.eventverse.app.domain.pack.GarmentModules
import com.eventverse.app.domain.rbac.AccessLevel
import com.eventverse.app.domain.rbac.CustomRole
import com.eventverse.app.domain.rbac.DataScope
import com.eventverse.app.domain.rbac.ModuleAccessConfig
import com.eventverse.app.domain.rbac.RoleId
import com.eventverse.app.domain.tenant.SubscriptionTier
import com.eventverse.app.domain.tenant.Tenant
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.domain.tenant.TenantName
import com.eventverse.app.domain.tenant.TenantSlug
import com.eventverse.app.domain.tenant.TenantStatus
import com.eventverse.app.infrastructure.DatabaseFactory
import com.eventverse.app.infrastructure.InMemoryAuditLogRepository
import com.eventverse.app.infrastructure.InMemoryDepartmentRepository
import com.eventverse.app.infrastructure.InMemoryDiscoveryDraftRepository
import com.eventverse.app.infrastructure.InMemoryDomainPackRepository
import com.eventverse.app.infrastructure.InMemoryEmployeeRepository
import com.eventverse.app.infrastructure.InMemoryModuleAssignmentRepository
import com.eventverse.app.infrastructure.InMemoryRoleRepository
import com.eventverse.app.infrastructure.InMemoryTenantEntitlementRepository
import com.eventverse.app.infrastructure.InMemoryTenantPipelineRepository
import com.eventverse.app.infrastructure.InMemoryTenantRepository
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * TRD-PLAT-011: rute Bagan Organisasi fail-closed untuk token tanpa `customRoleId` dan tanpa `departmentId`.
 *
 * Sebelumnya `orgChartDecision` mengembalikan `null` untuk token seperti itu dan `requireOrgChartAccess` meloloskannya,
 * sehingga `SALES`/`OPERATOR` tanpa identitas pabrik membaca/menulis karyawan. Fixture non-default: tenant klinik
 * hasil handoff, selain tenant garment.
 */
class OrgChartFailClosedApiTest {

    private val garmentSlug = "pabrik-fail-closed"
    private val garmentId = TenantId("ten-fail-closed")
    private val klinikSlug = "klinik-fail-closed"
    private val owner = UserId("usr-pemilik")

    private val packs = InMemoryDomainPackRepository()
    private val tenants = InMemoryTenantRepository()
    private val departments = InMemoryDepartmentRepository()
    private val employees = InMemoryEmployeeRepository()
    private val roles = InMemoryRoleRepository()

    @AfterTest
    fun cleanup() = DomainPackRegistry.unregister(DomainPackCode("klinik"))

    private suspend fun seedGarment() {
        tenants.save(
            Tenant(garmentId, TenantSlug(garmentSlug), TenantName("Pabrik Fail Closed"), TenantStatus.ACTIVE, SubscriptionTier.PRO)
        )
        departments.restoreDefaultPresets(garmentId)
        employees.restoreDefaultPresets(garmentId, departments.findAllByTenant(garmentId))
        roles.save(role("role-viewer", AccessLevel.VIEW))
        roles.save(role("role-none", AccessLevel.NONE))
    }

    private fun role(id: String, level: AccessLevel) = CustomRole(
        id = RoleId(id), tenantId = garmentId, name = "Jabatan $id", description = "", departmentId = "dept-sales",
        modulePermissions = mapOf(GarmentModules.ORG_CHART to ModuleAccessConfig(level, DataScope.ALL_TENANT_DATA))
    )

    private suspend fun handoffKlinik() {
        val drafts = InMemoryDiscoveryDraftRepository()
        val id = DiscoveryDraftId("draft-klinik-fc")
        CreateDiscoveryDraftUseCase(DeterministicDiscoveryAgent(), drafts)(
            DiscoveryRequest("Kami klinik dengan antrean pasien dan tagihan kasir.", industryHint = "klinik"), owner, id
        ).getOrThrow()
        LockDiscoveryDraftUseCase(drafts)(id, owner, isPlatformSuperadmin = false).getOrThrow()
        HandoffDiscoveryDraftUseCase(drafts, tenants, packs) { false }(id, true, klinikSlug, "Klinik FC").getOrThrow()
    }

    private fun ApplicationTestBuilder.boot() {
        DatabaseFactory.init()
        application {
            module(
                tenantRepository = tenants, pipelineRepository = InMemoryTenantPipelineRepository(),
                entitlementRepository = InMemoryTenantEntitlementRepository(), roleRepository = roles,
                moduleAssignmentRepository = InMemoryModuleAssignmentRepository(), departmentRepository = departments,
                employeeRepository = employees, auditLogRepository = InMemoryAuditLogRepository(),
                domainPackRepository = packs
            )
        }
    }

    /** Rute yang dilaporkan permisif: (metode, path, tulis?). */
    private val probes = listOf(
        Triple("GET", "/api/tenant/employees", false),
        Triple("GET", "/api/tenant/employees/emp-ten-fail-closed-joko", false),
        Triple("GET", "/api/tenant/employees/emp-ten-fail-closed-joko/t-shape", false),
        Triple("POST", "/api/tenant/employees", true),
        Triple("PUT", "/api/tenant/employees/emp-ten-fail-closed-joko", true),
        Triple("DELETE", "/api/tenant/employees/emp-ten-fail-closed-joko", true),
        Triple("GET", "/api/tenant/employees/archived", false),
        Triple("POST", "/api/tenant/employees/emp-ten-fail-closed-joko/restore", true),
        Triple("GET", "/api/tenant/departments", false)
    )

    private suspend fun ApplicationTestBuilder.status(
        method: String, path: String, write: Boolean, auth: HttpRequestBuilder.() -> Unit
    ): HttpStatusCode = client.request(path) {
        this.method = HttpMethod.parse(method)
        auth()
        if (write) { contentType(ContentType.Application.Json); setBody("{}") }
    }.status

    private fun ApplicationTestBuilder.assertAllForbidden(label: String, auth: HttpRequestBuilder.() -> Unit) {
        runBlocking {
            probes.forEach { (m, p, w) ->
                assertEquals(HttpStatusCode.Forbidden, status(m, p, w, auth), "$label: $m $p harus 403")
            }
        }
    }

    @Test
    fun noIdentityToken_nonOwnerRoles_getForbiddenOnEveryReportedRoute_garment() = testApplication {
        runBlocking { seedGarment() }
        boot()
        listOf(Role.SALES, Role.OPERATOR).forEach { r ->
            assertAllForbidden("garment $r") { asTenant(garmentSlug, r) }
        }
    }

    @Test
    fun noIdentityToken_nonOwnerRoles_getForbiddenOnEveryReportedRoute_klinik() = testApplication {
        runBlocking { handoffKlinik() }
        boot()
        listOf(Role.SALES, Role.OPERATOR).forEach { r ->
            assertAllForbidden("klinik $r") { asTenant(klinikSlug, r) }
        }
    }

    @Test
    fun tenantAdminWithoutRole_ownerStillPasses() = testApplication {
        runBlocking { seedGarment() }
        boot()
        runBlocking {
            assertEquals(HttpStatusCode.OK, status("GET", "/api/tenant/employees", false) { asTenant(garmentSlug) })
            assertEquals(HttpStatusCode.OK, status("GET", "/api/tenant/employees/emp-ten-fail-closed-joko", false) { asTenant(garmentSlug) })
            assertEquals(HttpStatusCode.OK, status("GET", "/api/tenant/employees/archived", false) { asTenant(garmentSlug) })
            assertEquals(HttpStatusCode.OK, status("GET", "/api/tenant/departments", false) { asTenant(garmentSlug) })
        }
    }

    @Test
    fun superadminActingAs_passes() = testApplication {
        runBlocking { seedGarment() }
        boot()
        runBlocking {
            assertEquals(HttpStatusCode.OK, status("GET", "/api/tenant/employees", false) { asSuperadminActingAs(garmentSlug) })
            assertEquals(HttpStatusCode.OK, status("GET", "/api/tenant/departments", false) { asSuperadminActingAs(garmentSlug) })
        }
    }

    @Test
    fun viewRole_readsOk_writesForbidden() = testApplication {
        runBlocking { seedGarment() }
        boot()
        val auth: HttpRequestBuilder.() -> Unit = { asStaff(garmentSlug, "role-viewer", "dept-sales") }
        runBlocking {
            probes.forEach { (m, p, w) ->
                val s = status(m, p, w, auth)
                val expectRead = !w && p != "/api/tenant/employees/archived" // archived butuh MANAGE
                if (expectRead) assertEquals(HttpStatusCode.OK, s, "VIEW: $m $p")
                else assertEquals(HttpStatusCode.Forbidden, s, "VIEW: $m $p")
            }
        }
    }

    @Test
    fun noneRole_forbiddenEverywhere() = testApplication {
        runBlocking { seedGarment() }
        boot()
        assertAllForbidden("NONE") { asStaff(garmentSlug, "role-none", "dept-sales") }
    }

    @Test
    fun noIdentityToken_cannotEscapeToAnotherTenant() = testApplication {
        runBlocking { seedGarment(); handoffKlinik() }
        boot()
        runBlocking {
            val s = status("GET", "/api/tenant/employees", false) { asTenantTargeting(garmentSlug, klinikSlug) }
            assertTrue(
                s == HttpStatusCode.Forbidden || s == HttpStatusCode.NotFound || s == HttpStatusCode.Unauthorized,
                "lintas tenant: $s"
            )
        }
    }
}

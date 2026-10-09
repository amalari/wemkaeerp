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
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * TRD-PLAT-010 T2: `POST …/restore-presets` Org Chart sadar-pack.
 *
 * Fixture non-default = tenant klinik hasil handoff (Kontrak 6). Urutan yang dijaga: wewenang dulu (403, fail-closed),
 * baru pack (409). Tenant garment tetap 200 (paritas) dan tenant klinik tidak pernah ditulisi divisi garment.
 */
class StarterOrgChartRestoreApiTest {

    private val klinikSlug = "klinik-handoff"
    private val garmentSlug = "pabrik-garment-uji"
    private val garmentId = TenantId("ten-garment-uji")
    private val owner = UserId("usr-pemilik")

    private val packs = InMemoryDomainPackRepository()
    private val tenants = InMemoryTenantRepository()
    private val departments = InMemoryDepartmentRepository()
    private val employees = InMemoryEmployeeRepository()
    private val roles = InMemoryRoleRepository()

    @AfterTest
    fun cleanup() = DomainPackRegistry.unregister(DomainPackCode("klinik"))

    private suspend fun handoffKlinik(): TenantId {
        val drafts = InMemoryDiscoveryDraftRepository()
        val id = DiscoveryDraftId("draft-klinik-org")
        CreateDiscoveryDraftUseCase(DeterministicDiscoveryAgent(), drafts)(
            DiscoveryRequest("Kami klinik dengan antrean pasien dan tagihan kasir.", industryHint = "klinik"), owner, id
        ).getOrThrow()
        LockDiscoveryDraftUseCase(drafts)(id, owner, isPlatformSuperadmin = false).getOrThrow()
        HandoffDiscoveryDraftUseCase(drafts, tenants, packs) { false }(id, true, klinikSlug, "Klinik Handoff").getOrThrow()
        return requireNotNull(tenants.findBySlug(TenantSlug(klinikSlug))) { "tenant klinik tidak terbentuk" }.id
    }

    private suspend fun seedGarmentTenant() {
        tenants.save(
            Tenant(
                id = garmentId, slug = TenantSlug(garmentSlug), name = TenantName("Pabrik Garment Uji"),
                status = TenantStatus.ACTIVE, tier = SubscriptionTier.PRO
            )
        )
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

    private val resources = listOf("departments", "employees")

    @Test
    fun restorePresets_klinikOwner_gets409WithPackWordingAndWritesNothing() = testApplication {
        val klinikId = runBlocking { handoffKlinik() }
        boot()

        resources.forEach { resource ->
            val res = client.post("/api/tenant/$resource/restore-presets") { asTenant(klinikSlug) }
            assertEquals(HttpStatusCode.Conflict, res.status, "$resource: ${res.bodyAsText()}")
            val body = res.bodyAsText()
            assertTrue("belum punya contoh bagan organisasi" in body, body)
            assertTrue("klinik" in body, "Pesan memakai kosakata pack: $body")
        }
        assertTrue(runBlocking { departments.findAllByTenant(klinikId) }.isEmpty(), "Tidak boleh ada divisi garment ditulis")
        assertTrue(runBlocking { employees.findAllByTenant(klinikId) }.isEmpty(), "Tidak boleh ada karyawan garment ditulis")
    }

    @Test
    fun restorePresets_klinikUnauthorizedRole_gets403BeforePackIsConsulted() = testApplication {
        runBlocking { handoffKlinik() }
        boot()

        resources.forEach { resource ->
            val res = client.post("/api/tenant/$resource/restore-presets") { asTenant(klinikSlug, Role.SALES) }
            assertEquals(HttpStatusCode.Forbidden, res.status, "$resource: ${res.bodyAsText()}")
        }
    }

    @Test
    fun restorePresets_garmentStaffWithoutManage_gets403ForBothResources() = testApplication {
        runBlocking {
            seedGarmentTenant()
            roles.save(
                CustomRole(
                    id = RoleId("role-viewer"), tenantId = garmentId, name = "Pelihat Bagan", description = "",
                    departmentId = "dept-sales",
                    modulePermissions = mapOf(GarmentModules.ORG_CHART to ModuleAccessConfig(AccessLevel.VIEW, DataScope.ALL_TENANT_DATA))
                )
            )
        }
        boot()

        resources.forEach { resource ->
            val res = client.post("/api/tenant/$resource/restore-presets") {
                asStaff(garmentSlug, customRoleId = "role-viewer", departmentId = "dept-sales")
            }
            assertEquals(HttpStatusCode.Forbidden, res.status, "$resource: ${res.bodyAsText()}")
        }
        assertTrue(runBlocking { departments.findAllByTenant(garmentId) }.isEmpty())
    }

    @Test
    fun restorePresets_garmentOwner_staysOkAndWritesGarmentStarter() = testApplication {
        runBlocking { seedGarmentTenant() }
        boot()

        val depts = client.post("/api/tenant/departments/restore-presets") { asTenant(garmentSlug) }
        assertEquals(HttpStatusCode.OK, depts.status, depts.bodyAsText())
        val emps = client.post("/api/tenant/employees/restore-presets") { asTenant(garmentSlug) }
        assertEquals(HttpStatusCode.OK, emps.status, emps.bodyAsText())

        assertTrue(runBlocking { departments.findAllByTenant(garmentId) }.isNotEmpty())
        assertTrue(runBlocking { employees.findAllByTenant(garmentId) }.isNotEmpty())
    }

    @Test
    fun restorePresets_klinikRestoreDoesNotAffectGarmentTenant() = testApplication {
        val klinikId = runBlocking { handoffKlinik().also { seedGarmentTenant() } }
        boot()

        assertEquals(HttpStatusCode.OK, client.post("/api/tenant/departments/restore-presets") { asTenant(garmentSlug) }.status)
        val garmentBefore = runBlocking { departments.findAllByTenant(garmentId) }.map { it.id }.toSet()

        assertEquals(HttpStatusCode.Conflict, client.post("/api/tenant/departments/restore-presets") { asTenant(klinikSlug) }.status)

        assertEquals(garmentBefore, runBlocking { departments.findAllByTenant(garmentId) }.map { it.id }.toSet())
        assertTrue(runBlocking { departments.findAllByTenant(klinikId) }.isEmpty())
    }
}

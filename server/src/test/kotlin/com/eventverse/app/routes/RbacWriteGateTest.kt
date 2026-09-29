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
import com.eventverse.app.infrastructure.InMemoryDepartmentRepository
import com.eventverse.app.infrastructure.InMemoryEmployeeRepository
import com.eventverse.app.infrastructure.InMemoryModuleAssignmentRepository
import com.eventverse.app.infrastructure.InMemoryRoleRepository
import com.eventverse.app.infrastructure.InMemoryTenantEntitlementRepository
import com.eventverse.app.infrastructure.InMemoryTenantPipelineRepository
import com.eventverse.app.infrastructure.InMemoryTenantRepository
import com.eventverse.app.module
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * B5: gerbang harus menghentikan handler, bukan sekadar mengganti status. `restore-presets` dipilih karena
 * efeknya terlihat (jabatan/divisi bawaan tercipta) dan dampaknya paling berat: ia menimpa matriks wewenang.
 */
class RbacWriteGateTest {

    private val slug = "gate-rbac"
    private val tenantId = TenantId("ten-gate-rbac")

    @Test
    fun restorePresets_asMemberWithoutRbacAccess_is403_andCreatesNothing() = testApplication {
        val tenants = InMemoryTenantRepository()
        runBlocking { tenants.save(Tenant(tenantId, TenantSlug(slug), TenantName("Gate RBAC"), TenantStatus.ACTIVE, SubscriptionTier.PRO, businessPreset = GarmentBlueprints.CMT_MAKLOON)) }
        val roles = InMemoryRoleRepository()
        val departments = InMemoryDepartmentRepository()
        application {
            module(tenantRepository = tenants, pipelineRepository = InMemoryTenantPipelineRepository(),
                entitlementRepository = InMemoryTenantEntitlementRepository(), roleRepository = roles,
                moduleAssignmentRepository = InMemoryModuleAssignmentRepository(), departmentRepository = departments,
                employeeRepository = InMemoryEmployeeRepository())
        }
        val operator = TestAuth.staffToken(slug, customRoleId = "role-tanpa-akses", role = Role.OPERATOR)
        val before = runBlocking { roles.findAllByTenant(tenantId).size to departments.findAllByTenant(tenantId).size }

        val r = client.post("/api/tenant/roles/restore-presets") { header("X-Tenant-Slug", slug); header(HttpHeaders.Authorization, "Bearer $operator") }
        val d = client.post("/api/tenant/departments/restore-presets") { header("X-Tenant-Slug", slug); header(HttpHeaders.Authorization, "Bearer $operator") }

        assertEquals(HttpStatusCode.Forbidden, r.status)
        assertEquals(HttpStatusCode.Forbidden, d.status)
        assertEquals(before, runBlocking { roles.findAllByTenant(tenantId).size to departments.findAllByTenant(tenantId).size }, "handler tidak boleh jalan")

        // Kontrol: admin tenant tetap bisa — gerbang tidak mengunci pemilik pabrik.
        val admin = TestAuth.tenantToken(slug, Role.TENANT_ADMIN)
        val ok = client.post("/api/tenant/roles/restore-presets") { header("X-Tenant-Slug", slug); header(HttpHeaders.Authorization, "Bearer $admin") }
        assertEquals(HttpStatusCode.OK, ok.status)
        assertTrue(runBlocking { roles.findAllByTenant(tenantId).isNotEmpty() })
    }
}

package com.eventverse.app

import com.eventverse.app.domain.tenant.*
import com.eventverse.app.infrastructure.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class TenantIsolationApiTest {

    private val tenantRepo = InMemoryTenantRepository()
    private val roleRepo = InMemoryRoleRepository()
    private val deptRepo = InMemoryDepartmentRepository()
    private val empRepo = InMemoryEmployeeRepository()

    private val tenantAlphaId = TenantId("ten-alpha-001")
    private val tenantBetaId = TenantId("ten-beta-002")

    @BeforeTest
    fun setup() {
        runBlocking {
            tenantRepo.save(
                Tenant(tenantAlphaId, TenantSlug("pabrik-alpha"), TenantName("PT Alpha"), TenantStatus.ACTIVE, SubscriptionTier.PRO)
            )
            tenantRepo.save(
                Tenant(tenantBetaId, TenantSlug("pabrik-beta"), TenantName("PT Beta"), TenantStatus.ACTIVE, SubscriptionTier.PRO)
            )
        }
    }

    @Test
    fun tenantData_shouldBeStrictlyIsolatedBetweenTenants() = testApplication {
        application {
            module(
                tenantRepository = tenantRepo,
                roleRepository = roleRepo,
                departmentRepository = deptRepo,
                employeeRepository = empRepo
            )
        }

        // 1. Tenant Alpha creates custom role
        val createRoleRes = client.post("/api/tenant/roles") {
            header("X-Tenant-Slug", "pabrik-alpha")
            contentType(ContentType.Application.Json)
            setBody("{\"name\":\"Alpha Unique Specialist Role\"}")
        }
        assertEquals(HttpStatusCode.Created, createRoleRes.status)

        // Tenant Beta lists roles -> Must NOT contain Alpha's role
        val betaRolesRes = client.get("/api/tenant/roles") {
            header("X-Tenant-Slug", "pabrik-beta")
        }
        assertEquals(HttpStatusCode.OK, betaRolesRes.status)
        assertFalse(betaRolesRes.bodyAsText().contains("Alpha Unique Specialist Role"))

        // 2. Tenant Alpha creates department
        val createDeptRes = client.post("/api/tenant/departments") {
            header("X-Tenant-Slug", "pabrik-alpha")
            contentType(ContentType.Application.Json)
            setBody("{\"displayName\":\"Divisi Khusus Alpha\",\"shortName\":\"AlphaDept\"}")
        }
        assertEquals(HttpStatusCode.Created, createDeptRes.status)

        // Tenant Beta lists departments -> Must NOT contain Alpha's department
        val betaDeptsRes = client.get("/api/tenant/departments") {
            header("X-Tenant-Slug", "pabrik-beta")
        }
        assertEquals(HttpStatusCode.OK, betaDeptsRes.status)
        assertFalse(betaDeptsRes.bodyAsText().contains("Divisi Khusus Alpha"))
    }

    @Test
    fun multipleTenants_restorePresetsConcurrently_shouldNeverCollideIds() = testApplication {
        application {
            module(
                tenantRepository = tenantRepo,
                roleRepository = roleRepo,
                departmentRepository = deptRepo,
                employeeRepository = empRepo
            )
        }

        // 1. Both tenants restore default departments
        val alphaRestoreDept = client.post("/api/tenant/departments/restore-presets") {
            header("X-Tenant-Slug", "pabrik-alpha")
        }
        assertEquals(HttpStatusCode.OK, alphaRestoreDept.status)

        val betaRestoreDept = client.post("/api/tenant/departments/restore-presets") {
            header("X-Tenant-Slug", "pabrik-beta")
        }
        assertEquals(HttpStatusCode.OK, betaRestoreDept.status)

        // 2. Both tenants restore default roles
        val alphaRestoreRole = client.post("/api/tenant/roles/restore-presets") {
            header("X-Tenant-Slug", "pabrik-alpha")
        }
        assertEquals(HttpStatusCode.OK, alphaRestoreRole.status)

        val betaRestoreRole = client.post("/api/tenant/roles/restore-presets") {
            header("X-Tenant-Slug", "pabrik-beta")
        }
        assertEquals(HttpStatusCode.OK, betaRestoreRole.status)

        // 3. Both tenants restore default employees
        val alphaRestoreEmp = client.post("/api/tenant/employees/restore-presets") {
            header("X-Tenant-Slug", "pabrik-alpha")
        }
        assertEquals(HttpStatusCode.OK, alphaRestoreEmp.status)

        val betaRestoreEmp = client.post("/api/tenant/employees/restore-presets") {
            header("X-Tenant-Slug", "pabrik-beta")
        }
        assertEquals(HttpStatusCode.OK, betaRestoreEmp.status)

        // 4. Verify distinct tenant-scoped department IDs
        val alphaDepts = client.get("/api/tenant/departments") {
            header("X-Tenant-Slug", "pabrik-alpha")
        }
        val betaDepts = client.get("/api/tenant/departments") {
            header("X-Tenant-Slug", "pabrik-beta")
        }

        val alphaText = alphaDepts.bodyAsText()
        val betaText = betaDepts.bodyAsText()

        // Alpha's IDs are scoped to ten-alpha-001
        assertTrue(alphaText.contains("dept-ten-alpha-001-sales"))
        // Beta's IDs are scoped to ten-beta-002
        assertTrue(betaText.contains("dept-ten-beta-002-sales"))

        // Neither contains the other's scoped ID
        assertFalse(alphaText.contains("dept-ten-beta-002-sales"))
        assertFalse(betaText.contains("dept-ten-alpha-001-sales"))
    }
}

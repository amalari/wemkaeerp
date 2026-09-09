package com.eventverse.app

import com.eventverse.app.domain.tenant.*
import com.eventverse.app.infrastructure.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class EmployeeApiTest {

    private val tenantSlug = "wemade-demo"
    private val tenantId = TenantId("ten-demo-001")

    private fun setupTestTenantRepo(): InMemoryTenantRepository {
        val repo = InMemoryTenantRepository()
        runBlocking {
            repo.save(
                Tenant(
                    id = tenantId,
                    slug = TenantSlug(tenantSlug),
                    name = TenantName("PT WeMade Demo"),
                    status = TenantStatus.ACTIVE,
                    tier = SubscriptionTier.PRO
                )
            )
        }
        return repo
    }

    @Test
    fun createEmployee_andGetTShapeHierarchy_shouldSucceed() = testApplication {
        val tenantRepo = setupTestTenantRepo()
        val deptRepo = InMemoryDepartmentRepository()
        val empRepo = InMemoryEmployeeRepository()

        // Seed preset depts & employees
        deptRepo.restoreDefaultPresets(tenantId)
        val depts = deptRepo.findAllByTenant(tenantId)
        empRepo.restoreDefaultPresets(tenantId, depts)

        application {
            module(
                tenantRepository = tenantRepo,
                departmentRepository = deptRepo,
                employeeRepository = empRepo
            )
        }

        // 1. Check list of employees
        val listRes = client.get("/api/tenant/employees") {
            header("X-Tenant-Slug", tenantSlug)
        }
        assertEquals(HttpStatusCode.OK, listRes.status)
        val listBody = listRes.bodyAsText()
        assertTrue(listBody.contains("emp-hendra"))
        assertTrue(listBody.contains("emp-budi"))

        // 2. Query T-Shape view for Budi (Head of Sales)
        val tShapeRes = client.get("/api/tenant/employees/emp-budi/t-shape") {
            header("X-Tenant-Slug", tenantSlug)
        }
        assertEquals(HttpStatusCode.OK, tShapeRes.status)
        val tShapeBody = tShapeRes.bodyAsText()
        assertTrue(tShapeBody.contains("\"focusNode\""))
        assertTrue(tShapeBody.contains("Budi Santoso"))
        assertTrue(tShapeBody.contains("\"superior\""))
        assertTrue(tShapeBody.contains("Bpk. Hendra Kusuma"))
        assertTrue(tShapeBody.contains("\"peerHeads\""))
        assertTrue(tShapeBody.contains("\"subordinates\""))

        // 3. Create a new employee in Sales
        val createRes = client.post("/api/tenant/employees") {
            header("X-Tenant-Slug", tenantSlug)
            contentType(ContentType.Application.Json)
            setBody("{\"name\":\"Ilham Pratama\",\"email\":\"ilham@wemade.id\",\"departmentId\":\"dept-sales\",\"level\":\"STAFF_OPERATOR\",\"roleTitle\":\"Sales Canvassing\",\"reportsToId\":\"emp-budi\",\"phone\":\"081234567\"}")
        }
        assertEquals(HttpStatusCode.Created, createRes.status)
        val createBody = createRes.bodyAsText()
        assertTrue(createBody.contains("Ilham Pratama"))
        val empId = createBody.substringAfter("\"id\":\"").substringBefore("\"")

        // 4. Update employee
        val updateRes = client.put("/api/tenant/employees/$empId") {
            header("X-Tenant-Slug", tenantSlug)
            contentType(ContentType.Application.Json)
            setBody("{\"name\":\"Ilham Pratama Putra\",\"roleTitle\":\"Senior Sales Canvassing\"}")
        }
        assertEquals(HttpStatusCode.OK, updateRes.status)
        assertTrue(updateRes.bodyAsText().contains("Ilham Pratama Putra"))
        assertTrue(updateRes.bodyAsText().contains("Senior Sales Canvassing"))

        // 5. Delete employee
        val delRes = client.delete("/api/tenant/employees/$empId") {
            header("X-Tenant-Slug", tenantSlug)
        }
        assertEquals(HttpStatusCode.OK, delRes.status)
    }

    @Test
    fun createHeadOfDepartment_withDemoteSuccession_shouldDemotePreviousHead() = testApplication {
        val tenantRepo = setupTestTenantRepo()
        val deptRepo = InMemoryDepartmentRepository()
        val empRepo = InMemoryEmployeeRepository()

        deptRepo.restoreDefaultPresets(tenantId)
        val depts = deptRepo.findAllByTenant(tenantId)
        empRepo.restoreDefaultPresets(tenantId, depts)

        application {
            module(
                tenantRepository = tenantRepo,
                departmentRepository = deptRepo,
                employeeRepository = empRepo
            )
        }

        // Appoint new Head of Sales with DEMOTE_TO_STAFF succession
        val res = client.post("/api/tenant/employees") {
            header("X-Tenant-Slug", tenantSlug)
            contentType(ContentType.Application.Json)
            setBody("{\"name\":\"Sultan Akbar\",\"email\":\"sultan.sales@wemade.id\",\"departmentId\":\"dept-sales\",\"level\":\"HEAD_OF_DEPARTMENT\",\"roleTitle\":\"General Manager Sales\",\"successionAction\":\"DEMOTE_TO_STAFF\"}")
        }
        assertEquals(HttpStatusCode.Created, res.status)
        val newHeadId = res.bodyAsText().substringAfter("\"id\":\"").substringBefore("\"")

        // Verify previous head Budi is now staff and reports to Sultan
        val budiRes = client.get("/api/tenant/employees/emp-budi") {
            header("X-Tenant-Slug", tenantSlug)
        }
        assertEquals(HttpStatusCode.OK, budiRes.status)
        val budiBody = budiRes.bodyAsText()
        assertTrue(budiBody.contains("STAFF_OPERATOR"))
        assertTrue(budiBody.contains("\"reportsToId\":\"$newHeadId\""))
    }

    @Test
    fun createEmployee_withDuplicateEmail_shouldReturn409ConflictWithDetails() = testApplication {
        val tenantRepo = setupTestTenantRepo()
        val deptRepo = InMemoryDepartmentRepository()
        val empRepo = InMemoryEmployeeRepository()

        deptRepo.restoreDefaultPresets(tenantId)
        val depts = deptRepo.findAllByTenant(tenantId)
        empRepo.restoreDefaultPresets(tenantId, depts)

        application {
            module(
                tenantRepository = tenantRepo,
                departmentRepository = deptRepo,
                employeeRepository = empRepo
            )
        }

        val activeBudi = requireNotNull(empRepo.findAllByTenant(tenantId).find { it.name.contains("Budi") }) { "Active Budi not found" }
        val res = client.post("/api/tenant/employees") {
            header("X-Tenant-Slug", tenantSlug)
            contentType(ContentType.Application.Json)
            setBody("{\"name\":\"Budi Duplikat\",\"email\":\"${activeBudi.email}\",\"departmentId\":\"${activeBudi.department?.id?.value}\",\"level\":\"STAFF_OPERATOR\"}")
        }

        assertEquals(HttpStatusCode.Conflict, res.status)
        val body = res.bodyAsText()
        assertTrue(body.contains("EMAIL_CONFLICT"))
        assertTrue(body.contains(activeBudi.name))
        assertTrue(body.contains(activeBudi.department?.displayName ?: "Direksi"))
        assertTrue(body.contains("\"isArchived\":false"))
    }
}


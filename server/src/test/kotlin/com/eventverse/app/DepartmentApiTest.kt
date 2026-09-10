package com.eventverse.app

import com.eventverse.app.domain.tenant.*
import com.eventverse.app.infrastructure.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class DepartmentApiTest {

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
    fun createDepartment_withJsonBody_shouldReturn201AndPersist() = testApplication {
        val tenantRepo = setupTestTenantRepo()
        val deptRepo = InMemoryDepartmentRepository()

        application {
            module(
                tenantRepository = tenantRepo,
                departmentRepository = deptRepo
            )
        }

        val res = client.post("/api/tenant/departments") {
            asTenant(tenantSlug)
            contentType(ContentType.Application.Json)
            setBody("{\"displayName\":\"Bordir & Sablon Printing\",\"shortName\":\"Bordir\",\"colorHex\":4292976968}")
        }

        assertEquals(HttpStatusCode.Created, res.status)
        val body = res.bodyAsText()
        assertTrue(body.contains("Bordir & Sablon Printing"))
        assertTrue(body.contains("Bordir"))
        assertTrue(body.contains("bordir"))

        val deptId = body.substringAfter("\"id\":\"").substringBefore("\"")
        val getRes = client.get("/api/tenant/departments/$deptId") {
            asTenant(tenantSlug)
        }
        assertEquals(HttpStatusCode.OK, getRes.status)
        assertTrue(getRes.bodyAsText().contains("Bordir & Sablon Printing"))
    }

    @Test
    fun updateDepartment_shouldModifyMetadata() = testApplication {
        val tenantRepo = setupTestTenantRepo()
        val deptRepo = InMemoryDepartmentRepository()

        application {
            module(
                tenantRepository = tenantRepo,
                departmentRepository = deptRepo
            )
        }

        val createRes = client.post("/api/tenant/departments") {
            asTenant(tenantSlug)
            contentType(ContentType.Application.Json)
            setBody("{\"displayName\":\"Desain Pola Baju\",\"shortName\":\"Pola\"}")
        }
        val deptId = createRes.bodyAsText().substringAfter("\"id\":\"").substringBefore("\"")

        val updateRes = client.put("/api/tenant/departments/$deptId") {
            asTenant(tenantSlug)
            contentType(ContentType.Application.Json)
            setBody("{\"displayName\":\"Desain Pola & Tech Pack Marker\",\"shortName\":\"TechPack\"}")
        }
        assertEquals(HttpStatusCode.OK, updateRes.status)
        val body = updateRes.bodyAsText()
        assertTrue(body.contains("Desain Pola & Tech Pack Marker"))
        assertTrue(body.contains("TechPack"))
    }

    @Test
    fun deleteDepartment_shouldArchiveDepartment() = testApplication {
        val tenantRepo = setupTestTenantRepo()
        val deptRepo = InMemoryDepartmentRepository()
        val empRepo = InMemoryEmployeeRepository()

        application {
            module(
                tenantRepository = tenantRepo,
                departmentRepository = deptRepo,
                employeeRepository = empRepo
            )
        }

        val createRes = client.post("/api/tenant/departments") {
            asTenant(tenantSlug)
            contentType(ContentType.Application.Json)
            setBody("{\"displayName\":\"Divisi Uji Coba\",\"shortName\":\"Uji\"}")
        }
        val deptId = createRes.bodyAsText().substringAfter("\"id\":\"").substringBefore("\"")

        val delRes = client.delete("/api/tenant/departments/$deptId") {
            asTenant(tenantSlug)
        }
        assertEquals(HttpStatusCode.OK, delRes.status)

        // Archived departments no longer show up in the active list...
        val listRes = client.get("/api/tenant/departments") {
            asTenant(tenantSlug)
        }
        assertFalse(listRes.bodyAsText().contains(deptId))

        // ...but remain findable by id (so it can be restored) and appear in the archived list.
        val getRes = client.get("/api/tenant/departments/$deptId") {
            asTenant(tenantSlug)
        }
        assertEquals(HttpStatusCode.OK, getRes.status)

        val archivedRes = client.get("/api/tenant/departments/archived") {
            asTenant(tenantSlug)
        }
        assertTrue(archivedRes.bodyAsText().contains(deptId))
    }

    @Test
    fun restorePresets_shouldLoad5StandardGarmentDepartments() = testApplication {
        val tenantRepo = setupTestTenantRepo()
        val deptRepo = InMemoryDepartmentRepository()

        application {
            module(
                tenantRepository = tenantRepo,
                departmentRepository = deptRepo
            )
        }

        val res = client.post("/api/tenant/departments/restore-presets") {
            asTenant(tenantSlug)
        }
        assertEquals(HttpStatusCode.OK, res.status)
        val body = res.bodyAsText()
        assertTrue(body.contains("dept-sales"))
        assertTrue(body.contains("dept-ppic"))
        assertTrue(body.contains("dept-warehouse"))
        assertTrue(body.contains("dept-qc"))
        assertTrue(body.contains("dept-finance") || body.contains("dept-exec"))
    }
}

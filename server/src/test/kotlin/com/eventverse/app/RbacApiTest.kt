package com.eventverse.app

import com.eventverse.app.domain.tenant.*
import com.eventverse.app.infrastructure.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class RbacApiTest {

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
    fun getRoles_withoutTenantHeader_shouldReturn404() = testApplication {
        val tenantRepo = setupTestTenantRepo()
        val roleRepo = InMemoryRoleRepository()

        application {
            module(
                tenantRepository = tenantRepo,
                roleRepository = roleRepo
            )
        }

        val response = client.get("/api/tenant/roles")
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun getRoles_withValidTenant_shouldReturnEmptyOrPresetRoles() = testApplication {
        val tenantRepo = setupTestTenantRepo()
        val roleRepo = InMemoryRoleRepository()
        runBlocking { roleRepo.restoreDefaultPresets(tenantId) }

        application {
            module(
                tenantRepository = tenantRepo,
                roleRepository = roleRepo
            )
        }

        val response = client.get("/api/tenant/roles") {
            header("X-Tenant-Slug", tenantSlug)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("role-owner"))
        assertTrue(body.contains("Owner / Direktur Pabrik"))
    }

    @Test
    fun createRole_withJsonBody_shouldCreateAndReturn201() = testApplication {
        val tenantRepo = setupTestTenantRepo()
        val roleRepo = InMemoryRoleRepository()

        application {
            module(
                tenantRepository = tenantRepo,
                roleRepository = roleRepo
            )
        }

        val response = client.post("/api/tenant/roles") {
            header("X-Tenant-Slug", tenantSlug)
            contentType(ContentType.Application.Json)
            setBody("{\"name\":\"Quality Assurance Lead\",\"description\":\"Memimpin inspeksi defect\",\"modulePermissions\":{\"QUALITY_CONTROL\":{\"level\":\"MANAGE\",\"scope\":\"ALL_TENANT_DATA\"}}}")
        }

        assertEquals(HttpStatusCode.Created, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("Quality Assurance Lead"))
        assertTrue(body.contains("QUALITY_CONTROL"))

        // Check it can be retrieved via GET /api/tenant/roles/{id}
        val roleId = body.substringAfter("\"id\":\"").substringBefore("\"")
        val getDetail = client.get("/api/tenant/roles/$roleId") {
            header("X-Tenant-Slug", tenantSlug)
        }
        assertEquals(HttpStatusCode.OK, getDetail.status)
        assertTrue(getDetail.bodyAsText().contains("Quality Assurance Lead"))
    }

    @Test
    fun updateRole_shouldModifyMetadataAndPermissions() = testApplication {
        val tenantRepo = setupTestTenantRepo()
        val roleRepo = InMemoryRoleRepository()

        application {
            module(
                tenantRepository = tenantRepo,
                roleRepository = roleRepo
            )
        }

        // Create
        val createRes = client.post("/api/tenant/roles") {
            header("X-Tenant-Slug", tenantSlug)
            contentType(ContentType.Application.Json)
            setBody("{\"name\":\"Staf Gudang Bahan\",\"description\":\"Bahan mentah\"}")
        }
        val roleId = createRes.bodyAsText().substringAfter("\"id\":\"").substringBefore("\"")

        // Update
        val updateRes = client.put("/api/tenant/roles/$roleId") {
            header("X-Tenant-Slug", tenantSlug)
            contentType(ContentType.Application.Json)
            setBody("{\"name\":\"Staf Gudang Bahan & Aksesoris\",\"description\":\"Bahan baku dan kancing\"}")
        }
        assertEquals(HttpStatusCode.OK, updateRes.status)
        val body = updateRes.bodyAsText()
        assertTrue(body.contains("Staf Gudang Bahan & Aksesoris"))
        assertTrue(body.contains("Bahan baku dan kancing"))
    }

    @Test
    fun deleteRole_customRole_shouldSucceed() = testApplication {
        val tenantRepo = setupTestTenantRepo()
        val roleRepo = InMemoryRoleRepository()

        application {
            module(
                tenantRepository = tenantRepo,
                roleRepository = roleRepo
            )
        }

        // Create custom role
        val createRes = client.post("/api/tenant/roles") {
            header("X-Tenant-Slug", tenantSlug)
            contentType(ContentType.Application.Json)
            setBody("{\"name\":\"Role Sementara\",\"description\":\"Untuk dihapus\"}")
        }
        val roleId = createRes.bodyAsText().substringAfter("\"id\":\"").substringBefore("\"")

        // Delete
        val delRes = client.delete("/api/tenant/roles/$roleId") {
            header("X-Tenant-Slug", tenantSlug)
        }
        assertEquals(HttpStatusCode.OK, delRes.status)

        // Ensure not found now
        val getRes = client.get("/api/tenant/roles/$roleId") {
            header("X-Tenant-Slug", tenantSlug)
        }
        assertEquals(HttpStatusCode.NotFound, getRes.status)
    }

    @Test
    fun restorePresets_shouldLoadAllFactoryRoles() = testApplication {
        val tenantRepo = setupTestTenantRepo()
        val roleRepo = InMemoryRoleRepository()

        application {
            module(
                tenantRepository = tenantRepo,
                roleRepository = roleRepo
            )
        }

        val res = client.post("/api/tenant/roles/restore-presets") {
            header("X-Tenant-Slug", tenantSlug)
        }
        assertEquals(HttpStatusCode.OK, res.status)
        val body = res.bodyAsText()
        assertTrue(body.contains("role-owner"))
        assertTrue(body.contains("role-ppic"))
        assertTrue(body.contains("role-sales"))
        assertTrue(body.contains("role-warehouse"))
        assertTrue(body.contains("role-operator"))
    }
}

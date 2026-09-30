package com.eventverse.app.routes

import com.eventverse.app.TestAuth
import com.eventverse.app.domain.auth.Role
import com.eventverse.app.domain.pack.GarmentBlueprints
import com.eventverse.app.domain.pack.GarmentModules
import com.eventverse.app.domain.rbac.AccessLevel
import com.eventverse.app.domain.rbac.CustomRole
import com.eventverse.app.domain.rbac.ModuleAccessConfig
import com.eventverse.app.domain.rbac.RoleId
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
import com.eventverse.app.shared.help.HelpCodec
import com.eventverse.app.shared.json.JsonParser
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * `POST /api/tenant/help/ask` (TRD-HELP-001 FR-6): tutorial disaring dengan wewenang pemanggil yang **sama** dengan
 * gerbang modul — peran tanpa akses modul tidak pernah menerima tutorial modul itu.
 */
class HelpApiTest {

    private val slug = "help-uji"
    private val tenantId = TenantId("ten-help-uji")

    private fun ApplicationTestBuilder.setUp() {
        DatabaseFactory.init()
        val tenants = InMemoryTenantRepository()
        val roles = InMemoryRoleRepository()
        runBlocking {
            tenants.save(Tenant(tenantId, TenantSlug(slug), TenantName("Help Uji"), TenantStatus.ACTIVE, SubscriptionTier.PRO, businessPreset = GarmentBlueprints.CMT_MAKLOON))
            roles.save(CustomRole(RoleId("role-sales"), tenantId, "Sales", "uji", modulePermissions = mapOf(
                GarmentModules.CRM_SALES to ModuleAccessConfig(level = AccessLevel.OPERATE))))
            roles.save(CustomRole(RoleId("role-qc"), tenantId, "QC", "uji", modulePermissions = mapOf(
                GarmentModules.QUALITY_CONTROL to ModuleAccessConfig(level = AccessLevel.OPERATE))))
        }
        application {
            module(tenantRepository = tenants, pipelineRepository = InMemoryTenantPipelineRepository(),
                entitlementRepository = InMemoryTenantEntitlementRepository(), roleRepository = roles,
                moduleAssignmentRepository = InMemoryModuleAssignmentRepository(), departmentRepository = InMemoryDepartmentRepository(),
                employeeRepository = InMemoryEmployeeRepository())
        }
    }

    private suspend fun ApplicationTestBuilder.ask(body: String, token: String?): HttpResponse = client.post("/api/tenant/help/ask") {
        header("X-Tenant-Slug", slug)
        token?.let { header(HttpHeaders.Authorization, "Bearer $it") }
        contentType(ContentType.Application.Json)
        setBody(body)
    }

    private fun question(q: String) = HelpCodec.encodeRequest(q, GarmentModules.CRM_SALES).encode()

    @Test
    fun salesRole_getsCrmTutorial() = testApplication {
        setUp()
        val response = ask(question("gimana cara bikin lead baru"), TestAuth.staffToken(slug, customRoleId = "role-sales", role = Role.OPERATOR))
        assertEquals(HttpStatusCode.OK, response.status)
        val result = HelpCodec.decodeResult(JsonParser.parseObject(response.bodyAsText()))
        assertEquals("crm_new_lead", result.suggestion?.tutorialId?.value)
        assertEquals("deterministic/help-v1", result.agentRef)
    }

    @Test
    fun qcRole_withoutCrmAccess_neverReceivesCrmTutorial() = testApplication {
        setUp()
        val response = ask(question("gimana cara bikin lead baru"), TestAuth.staffToken(slug, customRoleId = "role-qc", role = Role.OPERATOR))
        assertEquals(HttpStatusCode.OK, response.status)
        val result = HelpCodec.decodeResult(JsonParser.parseObject(response.bodyAsText()))
        assertNull(result.suggestion)
        assertTrue(result.alternatives.none { it.moduleId == GarmentModules.CRM_SALES })
    }

    @Test
    fun ownerBypass_seesPlatformTutorials() = testApplication {
        setUp()
        val response = ask(question("cara ubah hak akses role"), TestAuth.tenantToken(slug, Role.TENANT_ADMIN))
        val result = HelpCodec.decodeResult(JsonParser.parseObject(response.bodyAsText()))
        assertEquals("platform_rbac_role_access", result.suggestion?.tutorialId?.value)
    }

    @Test
    fun anonymous_is401_andBadBodies_are400() = testApplication {
        setUp()
        assertEquals(HttpStatusCode.Unauthorized, ask(question("lead"), token = null).status)
        val token = TestAuth.tenantToken(slug, Role.TENANT_ADMIN)
        assertEquals(HttpStatusCode.BadRequest, ask("{}", token).status)
        assertEquals(HttpStatusCode.BadRequest, ask("bukan json", token).status)
        assertEquals(HttpStatusCode.BadRequest, ask(question("a".repeat(501)), token).status)
    }
}

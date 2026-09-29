package com.eventverse.app.routes

import com.eventverse.app.domain.rbac.isScopeSupported

import com.eventverse.app.domain.rbac.isFoundation

import com.eventverse.app.domain.rbac.isOperational

import com.eventverse.app.domain.rbac.isGovernance

import com.eventverse.app.domain.rbac.isHierarchical

import com.eventverse.app.domain.rbac.isGlobalOnly

import com.eventverse.app.domain.rbac.category

import com.eventverse.app.domain.rbac.supportedScopes

import com.eventverse.app.domain.rbac.kind

import com.eventverse.app.domain.rbac.scopeCapability

import com.eventverse.app.domain.rbac.iconKey

import com.eventverse.app.domain.rbac.description

import com.eventverse.app.domain.rbac.displayName

import com.eventverse.app.domain.rbac.name

import com.eventverse.app.domain.rbac.code

import com.eventverse.app.domain.rbac.BusinessModules

import com.eventverse.app.domain.pack.GarmentModules

import com.eventverse.app.TestAuth
import com.eventverse.app.domain.auth.Role
import com.eventverse.app.domain.pack.GarmentBlueprints
import com.eventverse.app.domain.rbac.AccessLevel
import com.eventverse.app.domain.rbac.BusinessModule
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
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * `GET /api/tenant/me/access` (B5): menu pengguna dihitung di server dengan jalur yang **sama** dengan gerbang.
 * Kalau keduanya berbeda pendapat, pengguna melihat menu yang lalu menolaknya (atau sebaliknya).
 */
class MyAccessApiTest {

    private val slug = "gate-me"
    private val tenantId = TenantId("ten-gate-me")

    @Test
    fun myAccess_matchesWhatTheGateEnforces_forAStaffRole_andOwnerBypasses() = testApplication {
        DatabaseFactory.init()
        val tenants = InMemoryTenantRepository()
        val roles = InMemoryRoleRepository()
        runBlocking {
            tenants.save(Tenant(tenantId, TenantSlug(slug), TenantName("Gate Me"), TenantStatus.ACTIVE, SubscriptionTier.PRO, businessPreset = GarmentBlueprints.CMT_MAKLOON))
            roles.save(CustomRole(RoleId("role-qc"), tenantId, "QC", "uji", modulePermissions = mapOf(
                GarmentModules.QUALITY_CONTROL to ModuleAccessConfig(level = AccessLevel.OPERATE))))
        }
        application {
            module(tenantRepository = tenants, pipelineRepository = InMemoryTenantPipelineRepository(),
                entitlementRepository = InMemoryTenantEntitlementRepository(), roleRepository = roles,
                moduleAssignmentRepository = InMemoryModuleAssignmentRepository(), departmentRepository = InMemoryDepartmentRepository(),
                employeeRepository = InMemoryEmployeeRepository())
        }
        suspend fun access(token: String) = com.eventverse.app.shared.rbac.AccessDecisionCodec.decode(
            com.eventverse.app.shared.json.JsonParser.parseObject(
                client.get("/api/tenant/me/access") { header("X-Tenant-Slug", slug); header(HttpHeaders.Authorization, "Bearer $token") }.bodyAsText()
            )
        )

        val qc = access(TestAuth.staffToken(slug, customRoleId = "role-qc", role = Role.OPERATOR))
        assertEquals(AccessLevel.OPERATE, qc.getValue(GarmentModules.QUALITY_CONTROL).config.level)
        assertEquals(AccessLevel.NONE, qc.getValue(GarmentModules.DYNAMIC_RBAC).config.level)
        assertEquals(BusinessModules.entries.toSet(), qc.keys, "semua modul dikirim — modul tanpa entri tidak ditebak klien")

        // Gerbang setuju dengan menu: QC boleh inspeksi, tidak boleh membaca daftar jabatan.
        val qcToken = TestAuth.staffToken(slug, customRoleId = "role-qc", role = Role.OPERATOR)
        val roleList = client.get("/api/tenant/roles") { header("X-Tenant-Slug", slug); header(HttpHeaders.Authorization, "Bearer $qcToken") }
        assertEquals(HttpStatusCode.Forbidden, roleList.status)

        val owner = access(TestAuth.tenantToken(slug, Role.TENANT_ADMIN))
        assertEquals(AccessLevel.MANAGE, owner.getValue(GarmentModules.DYNAMIC_RBAC).config.level)
        assertEquals(com.eventverse.app.domain.rbac.AccessSource.OWNER_BYPASS, owner.getValue(GarmentModules.DYNAMIC_RBAC).source)
    }
}

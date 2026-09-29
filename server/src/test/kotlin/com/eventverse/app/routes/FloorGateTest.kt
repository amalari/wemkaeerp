package com.eventverse.app.routes

import com.eventverse.app.domain.rbac.isScopeSupported

import com.eventverse.app.domain.rbac.isFoundation

import com.eventverse.app.domain.rbac.isOperational

import com.eventverse.app.domain.rbac.isGovernance

import com.eventverse.app.domain.rbac.isHierarchical

import com.eventverse.app.domain.rbac.isGlobalOnly


import com.eventverse.app.domain.rbac.supportedScopes

import com.eventverse.app.domain.rbac.kind

import com.eventverse.app.domain.rbac.scopeCapability

import com.eventverse.app.domain.rbac.iconKey

import com.eventverse.app.domain.rbac.description

import com.eventverse.app.domain.rbac.displayName

import com.eventverse.app.domain.rbac.name

import com.eventverse.app.domain.rbac.code

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
 * B5 sisi positif untuk SPK & lantai produksi: gerbang terpusat tidak boleh mengunci orang dari pekerjaannya.
 * Operator & QC mengerjakan aksi lantai; Sales CRM melihat SPK dari detail deal tapi tidak mengubahnya.
 */
class FloorGateTest {

    private val slug = "gate-floor"
    private val tenantId = TenantId("ten-gate-floor")

    private fun role(id: String, module: BusinessModule, level: AccessLevel) =
        CustomRole(RoleId(id), tenantId, id, "uji gerbang", modulePermissions = mapOf(module to ModuleAccessConfig(level = level)))

    private suspend fun HttpClient.status(token: String, method: HttpMethod, path: String): HttpStatusCode =
        request(path) {
            this.method = method
            header("X-Tenant-Slug", slug)
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            if (method != HttpMethod.Get) setBody("{}")
        }.status

    @Test
    fun operatorQcAndSales_eachReachTheirWork_andNothingMore() = testApplication {
        DatabaseFactory.init()
        val tenants = InMemoryTenantRepository()
        val roles = InMemoryRoleRepository()
        runBlocking {
            tenants.save(Tenant(tenantId, TenantSlug(slug), TenantName("Gate Floor"), TenantStatus.ACTIVE, SubscriptionTier.PRO, businessPreset = GarmentBlueprints.CMT_MAKLOON))
            roles.save(role("role-operator", GarmentModules.OPERATOR_EXEC, AccessLevel.OPERATE))
            roles.save(role("role-qc", GarmentModules.QUALITY_CONTROL, AccessLevel.OPERATE))
            roles.save(role("role-sales", GarmentModules.CRM_SALES, AccessLevel.OPERATE))
        }
        application {
            module(tenantRepository = tenants, pipelineRepository = InMemoryTenantPipelineRepository(),
                entitlementRepository = InMemoryTenantEntitlementRepository(), roleRepository = roles,
                moduleAssignmentRepository = InMemoryModuleAssignmentRepository(), departmentRepository = InMemoryDepartmentRepository(),
                employeeRepository = InMemoryEmployeeRepository())
        }
        val operator = TestAuth.staffToken(slug, customRoleId = "role-operator", role = Role.OPERATOR)
        val qc = TestAuth.staffToken(slug, customRoleId = "role-qc", role = Role.OPERATOR)
        val sales = TestAuth.staffToken(slug, customRoleId = "role-sales", role = Role.SALES)
        val forbidden = HttpStatusCode.Forbidden
        val spk = "/api/tenant/sampling/orders/tidak-ada"

        // Operator: lihat SPK, kerjakan meja, pakai antrian kerja — tapi tidak mengubah SPK.
        assertNotEquals(forbidden, client.status(operator, HttpMethod.Get, "/api/tenant/sampling/orders"))
        assertNotEquals(forbidden, client.status(operator, HttpMethod.Post, "$spk/work/start"))
        assertNotEquals(forbidden, client.status(operator, HttpMethod.Get, "/api/tenant/work-queue/board"))
        assertEquals(forbidden, client.status(operator, HttpMethod.Put, spk))
        assertEquals(forbidden, client.status(operator, HttpMethod.Post, "/api/tenant/sampling/orders"))

        // QC: inspeksi ya, antrian kerja produksi tidak.
        assertNotEquals(forbidden, client.status(qc, HttpMethod.Post, "$spk/qc/inspect"))
        assertEquals(forbidden, client.status(qc, HttpMethod.Get, "/api/tenant/work-queue/board"))

        // Sales: lihat SPK & cetak kartu dari detail deal; aksi lantai dan katalog proses tidak.
        assertNotEquals(forbidden, client.status(sales, HttpMethod.Get, "/api/tenant/sampling/orders"))
        assertNotEquals(forbidden, client.status(sales, HttpMethod.Get, "/api/tenant/traceability/work-orders/SAMPLING/tidak-ada/spk-card.pdf"))
        assertEquals(forbidden, client.status(sales, HttpMethod.Post, "$spk/work/start"))
        assertEquals(forbidden, client.status(sales, HttpMethod.Post, "/api/tenant/process-catalog"))
    }
}

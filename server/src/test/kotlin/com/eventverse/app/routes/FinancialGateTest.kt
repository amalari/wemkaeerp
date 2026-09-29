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
import com.eventverse.app.infrastructure.InMemoryMaterialItemRepository
import com.eventverse.app.infrastructure.InMemoryMaterialPriceRepository
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
 * B5 sisi positif untuk data keuangan: gerbang **tidak** boleh mengunci pengguna yang sah. Dropdown bahan di form
 * Sampling harus tetap jalan, tetapi harga bahan hanya untuk Master Data/Costing (TRD aturan B5).
 */
class FinancialGateTest {

    private val slug = "gate-fin"
    private val tenantId = TenantId("ten-gate-fin")

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
    fun samplingUser_readsMaterialCatalog_butNotPrices_andCostingUserReadsPrices() = testApplication {
        DatabaseFactory.init()
        val tenants = InMemoryTenantRepository()
        val roles = InMemoryRoleRepository()
        runBlocking {
            tenants.save(Tenant(tenantId, TenantSlug(slug), TenantName("Gate Fin"), TenantStatus.ACTIVE, SubscriptionTier.PRO, businessPreset = GarmentBlueprints.CMT_MAKLOON))
            roles.save(role("role-sampler", GarmentModules.SAMPLING_ORDER, AccessLevel.VIEW))
            roles.save(role("role-coster", GarmentModules.COSTING_HPP, AccessLevel.OPERATE))
        }
        application {
            module(tenantRepository = tenants, pipelineRepository = InMemoryTenantPipelineRepository(),
                entitlementRepository = InMemoryTenantEntitlementRepository(), roleRepository = roles,
                moduleAssignmentRepository = InMemoryModuleAssignmentRepository(), departmentRepository = InMemoryDepartmentRepository(),
                employeeRepository = InMemoryEmployeeRepository(), materialItemRepository = InMemoryMaterialItemRepository(),
                materialPriceRepository = InMemoryMaterialPriceRepository())
        }
        val sampler = TestAuth.staffToken(slug, customRoleId = "role-sampler", role = Role.SALES)
        val coster = TestAuth.staffToken(slug, customRoleId = "role-coster", role = Role.SALES)
        val forbidden = HttpStatusCode.Forbidden

        // Sampling: katalog bahan ya (dropdown), harga/kebijakan/tulis tidak, keuangan lain tidak.
        assertNotEquals(forbidden, client.status(sampler, HttpMethod.Get, "/api/tenant/master-data/materials"))
        assertEquals(forbidden, client.status(sampler, HttpMethod.Get, "/api/tenant/master-data/materials/x1/prices"))
        assertEquals(forbidden, client.status(sampler, HttpMethod.Get, "/api/tenant/master-data/price-policy"))
        assertEquals(forbidden, client.status(sampler, HttpMethod.Post, "/api/tenant/master-data/materials"))
        assertEquals(forbidden, client.status(sampler, HttpMethod.Get, "/api/tenant/invoicing"))
        assertEquals(forbidden, client.status(sampler, HttpMethod.Get, "/api/tenant/costing/sheets"))

        // Costing OPERATE: harga bahan ya, lembar HPP ya, tapi rate card (MANAGE) tidak.
        assertNotEquals(forbidden, client.status(coster, HttpMethod.Get, "/api/tenant/master-data/materials/x1/prices"))
        assertNotEquals(forbidden, client.status(coster, HttpMethod.Get, "/api/tenant/costing/sheets"))
        assertEquals(forbidden, client.status(coster, HttpMethod.Put, "/api/tenant/costing/rate-card"))
        assertEquals(forbidden, client.status(coster, HttpMethod.Put, "/api/tenant/master-data/price-policy"))
    }
}

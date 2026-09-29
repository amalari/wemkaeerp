package com.eventverse.app

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

import com.eventverse.app.domain.rbac.AccessLevel
import com.eventverse.app.domain.rbac.BusinessModule
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
import com.eventverse.app.infrastructure.InMemoryModuleAssignmentRepository
import com.eventverse.app.infrastructure.InMemoryRoleRepository
import com.eventverse.app.infrastructure.InMemoryTenantRepository
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Gerbang wewenang Kontak Vendor. Yang diuji adalah janji utama fitur ini: **hanya admin
 * produksi (MANAGE) yang bisa menambah vendor dan menunjuk vendor** — staf sampling yang
 * tokennya VIEW tetap ditolak walau memanggil endpoint langsung tanpa lewat UI.
 *
 * Semua kasus berhenti di gerbang sebelum repository disentuh, sehingga tidak butuh Postgres.
 */
class VendorAccessApiTest {

    private val slug = "wemade-demo"
    private val tenantId = TenantId("ten-demo-001")

    private fun ApplicationTestBuilder.installApp(level: AccessLevel) {
        val tenantRepo = InMemoryTenantRepository()
        val roleRepo = InMemoryRoleRepository()
        runBlocking {
            tenantRepo.save(
                Tenant(
                    id = tenantId,
                    slug = TenantSlug(slug),
                    name = TenantName("Pabrik Uji Vendor"),
                    status = TenantStatus.ACTIVE,
                    tier = SubscriptionTier.PRO
                )
            )
            roleRepo.save(
                CustomRole(
                    id = RoleId("role-sampling"),
                    tenantId = tenantId,
                    name = "Staf Sampling",
                    description = "",
                    modulePermissions = mapOf(
                        GarmentModules.VENDOR_CONTACTS to ModuleAccessConfig(level, DataScope.ALL_TENANT_DATA)
                    )
                )
            )
        }
        application {
            module(
                tenantRepository = tenantRepo,
                roleRepository = roleRepo,
                moduleAssignmentRepository = InMemoryModuleAssignmentRepository()
            )
        }
    }

    @Test
    fun `register vendor with view only access should be forbidden`() = testApplication {
        installApp(AccessLevel.VIEW)
        val response = client.post("/api/tenant/vendors") {
            asStaff(slug, customRoleId = "role-sampling")
            contentType(ContentType.Application.Json)
            setBody("""{"name":"CV Sablon Liar"}""")
        }
        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `assign vendor with operate access should be forbidden`() = testApplication {
        installApp(AccessLevel.OPERATE)
        val response = client.post("/api/tenant/vendor-assignments") {
            asStaff(slug, customRoleId = "role-sampling")
            contentType(ContentType.Application.Json)
            setBody("""{"subjectId":"smp-1","processCode":"SABLON","vendorId":"vnd-demo-001"}""")
        }
        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `set vendor rate with view only access should be forbidden`() = testApplication {
        installApp(AccessLevel.VIEW)
        val response = client.post("/api/tenant/vendors/vnd-demo-001/rates") {
            asStaff(slug, customRoleId = "role-sampling")
            contentType(ContentType.Application.Json)
            setBody("""{"serviceCode":"SABLON","serviceName":"Sablon","priceIdr":1,"effectiveFrom":"2026-09-01"}""")
        }
        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `list vendors when module closed should be forbidden`() = testApplication {
        installApp(AccessLevel.NONE)
        val response = client.get("/api/tenant/vendors") { asStaff(slug, customRoleId = "role-sampling") }
        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `vendor queue when module closed should be forbidden`() = testApplication {
        installApp(AccessLevel.NONE)
        val response = client.get("/api/tenant/vendor-queue") { asStaff(slug, customRoleId = "role-sampling") }
        assertEquals(HttpStatusCode.Forbidden, response.status)
    }
}

package com.eventverse.app.routes

import com.eventverse.app.TestAuth
import com.eventverse.app.domain.auth.Role
import com.eventverse.app.domain.builder.Deployment
import com.eventverse.app.domain.builder.DeploymentId
import com.eventverse.app.domain.builder.DeploymentNumber
import com.eventverse.app.domain.builder.DeploymentStatus
import com.eventverse.app.domain.pack.DomainPackCode
import com.eventverse.app.domain.tenant.SubscriptionTier
import com.eventverse.app.domain.tenant.Tenant
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.domain.tenant.TenantName
import com.eventverse.app.domain.tenant.TenantSlug
import com.eventverse.app.domain.tenant.TenantStatus
import com.eventverse.app.infrastructure.InMemoryBuilderDeploymentRepository
import com.eventverse.app.infrastructure.InMemoryDepartmentRepository
import com.eventverse.app.infrastructure.InMemoryDomainPackRepository
import com.eventverse.app.infrastructure.InMemoryEmployeeRepository
import com.eventverse.app.infrastructure.InMemoryModuleAssignmentRepository
import com.eventverse.app.infrastructure.InMemoryRoleRepository
import com.eventverse.app.infrastructure.InMemoryTenantEntitlementRepository
import com.eventverse.app.infrastructure.InMemoryTenantPipelineRepository
import com.eventverse.app.infrastructure.InMemoryTenantRepository
import com.eventverse.app.module
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Gerbang Builder M0 (PLAN-builder-console AC-M0-1/4/5, F2, F3): overview hanya untuk peran pembawa
 * `MANAGE_BUILDER` (fail-closed), superadmin tetap masuk lewat subdomain (carve-out act-as), dan
 * aturan host-vs-JWT untuk user tenant.
 *
 * Tanpa Postgres: repositori in-memory, sama seperti `RouteGateTest` versi in-memory.
 */
class BuilderRouteGateTest {

    private val slug = "wemade-demo"

    private fun seedRepo(): InMemoryTenantRepository = InMemoryTenantRepository().also { repo ->
        runBlocking {
            repo.save(
                Tenant(
                    TenantId("ten-wemade-demo"), TenantSlug(slug), TenantName("WeMade Demo"),
                    TenantStatus.ACTIVE, SubscriptionTier.PRO
                )
            )
            repo.save(
                Tenant(
                    TenantId("ten-lain"), TenantSlug("pabrik-lain"), TenantName("Pabrik Lain"),
                    TenantStatus.ACTIVE, SubscriptionTier.PRO
                )
            )
        }
    }

    private fun seedDeployments() = InMemoryBuilderDeploymentRepository().also { repo ->
        runBlocking {
            repo.save(
                Deployment(
                    id = DeploymentId("dep-ten-wemade-demo-1"),
                    tenantId = TenantId("ten-wemade-demo"),
                    number = DeploymentNumber(1),
                    packCode = DomainPackCode("garment"),
                    appBuild = "build-uji",
                    status = DeploymentStatus.IMPORTED
                )
            ).getOrThrow()
        }
    }

    /** Set repositori in-memory penuh — tanpa Postgres (pola `RouteGateTest`). */
    private fun ApplicationTestBuilder.installModule() {
        application {
            module(
                tenantRepository = seedRepo(),
                pipelineRepository = InMemoryTenantPipelineRepository(),
                entitlementRepository = InMemoryTenantEntitlementRepository(),
                roleRepository = InMemoryRoleRepository(),
                moduleAssignmentRepository = InMemoryModuleAssignmentRepository(),
                departmentRepository = InMemoryDepartmentRepository(),
                employeeRepository = InMemoryEmployeeRepository(),
                domainPackRepository = InMemoryDomainPackRepository(),
                builderDeploymentRepository = seedDeployments()
            )
        }
    }


    @Test
    fun overview_forTenantOwner_showsImportedDeployment() = testApplication {
        installModule()

        val response = client.get("/api/builder/overview") {
            header("Host", "$slug.wemakeerp.com")
            header(HttpHeaders.Authorization, "Bearer ${TestAuth.tenantToken(slug, tenantId = "ten-wemade-demo")}")
        }

        assertEquals(200, response.status.value)
        assertTrue(response.bodyAsText().contains("IMPORTED"), "Deployment #1 IMPORTED tampil di overview")
        assertTrue(response.bodyAsText().contains("build-uji"))
        assertTrue(response.bodyAsText().contains("\"domainPackVersion\":null"), "tenant lama belum pin versi (paritas B7)")
    }

    @Test
    fun overview_forTenantStaffWithoutBuilderPermission_returns403() = testApplication {
        installModule()

        val response = client.get("/api/builder/overview") {
            header("X-Tenant-Slug", slug)
            header(HttpHeaders.Authorization, "Bearer ${TestAuth.tenantToken(slug, role = Role.SALES)}")
        }

        assertEquals(403, response.status.value)
    }

    @Test
    fun overview_withoutCredentials_returns401() = testApplication {
        installModule()

        assertEquals(401, client.get("/api/builder/overview").status.value)
    }

    @Test
    fun overview_forSuperadminViaSubdomain_returns200_evenWithHostVsJwtRule() = testApplication {
        // F2 carve-out: superadmin (tenantId = null) bebas masuk subdomain tenant mana pun.
        installModule()

        val response = client.get("/api/builder/overview") {
            header("Host", "$slug.wemakeerp.com")
            header(HttpHeaders.Authorization, "Bearer ${TestAuth.superadminToken()}")
        }

        assertEquals(200, response.status.value)
        assertTrue(response.bodyAsText().contains(slug))
    }

    @Test
    fun tenantBoundUser_openingAnotherTenantsSubdomain_returns403() = testApplication {
        // F2: host adalah pintu tenant kedua — user tenant A membuka subdomain tenant B = 403.
        installModule()

        val response = client.get("/api/builder/overview") {
            header("Host", "pabrik-lain.wemakeerp.com")
            header(HttpHeaders.Authorization, "Bearer ${TestAuth.tenantToken(slug, tenantId = "ten-wemade-demo")}")
        }

        assertEquals(403, response.status.value)
    }

    @Test
    fun tenantBoundUser_targetingAnotherTenantViaHeader_returns403() = testApplication {
        installModule()

        val response = client.get("/api/builder/overview") {
            header("X-Tenant-Slug", "pabrik-lain")
            header(HttpHeaders.Authorization, "Bearer ${TestAuth.tenantToken(slug, tenantId = "ten-wemade-demo")}")
        }

        assertEquals(403, response.status.value)
    }

    @Test
    fun mayOpenBuilder_isFailClosed_forUnknownRoles() {
        assertEquals(false, mayOpenBuilder(null), "peran tidak bisa dihitung = tolak")
        assertEquals(false, mayOpenBuilder(Role.OPERATOR))
        assertEquals(true, mayOpenBuilder(Role.TENANT_ADMIN))
        assertEquals(true, mayOpenBuilder(Role.PLATFORM_SUPERADMIN))
    }
}

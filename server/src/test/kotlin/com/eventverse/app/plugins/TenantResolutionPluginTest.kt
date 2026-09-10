package com.eventverse.app.plugins

import com.eventverse.app.asSuperadmin
import com.eventverse.app.asSuperadminActingAs
import com.eventverse.app.asTenant
import com.eventverse.app.asTenantByTokenOnly
import com.eventverse.app.asTenantTargeting
import com.eventverse.app.domain.tenant.*
import com.eventverse.app.infrastructure.InMemoryTenantRepository
import com.eventverse.app.module
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class TenantResolutionPluginTest {

    private fun twoTenantRepo(): InMemoryTenantRepository {
        val repo = InMemoryTenantRepository()
        runBlocking {
            repo.save(
                Tenant(
                    TenantId("ten-alpha-001"), TenantSlug("pabrik-alpha"), TenantName("PT Alpha"),
                    TenantStatus.ACTIVE, SubscriptionTier.PRO
                )
            )
            repo.save(
                Tenant(
                    TenantId("ten-beta-002"), TenantSlug("pabrik-beta"), TenantName("PT Beta"),
                    TenantStatus.ACTIVE, SubscriptionTier.PRO
                )
            )
        }
        return repo
    }

    @Test
    fun public_onboarding_check_subdomain_should_bypass_tenant_check() = testApplication {
        application { module() }

        val response = client.get("/api/public/onboarding/check-subdomain?slug=brand-new-convection")
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("\"isAvailable\":true"))
    }

    @Test
    fun tenant_route_with_valid_session_should_resolve_tenant_context() = testApplication {
        application { module() }

        val response = client.get("/api/tenant/info") { asTenant("wemade-demo") }

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("wemade-demo"))
        assertTrue(response.bodyAsText().contains("PRO"))
    }

    @Test
    fun tenant_route_without_any_credentials_should_return_401() = testApplication {
        application { module() }

        val response = client.get("/api/tenant/info")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertTrue(response.bodyAsText().contains("Authentication required"))
    }

    @Test
    fun tenant_route_with_only_tenant_header_should_return_401() = testApplication {
        // The core vulnerability this plugin closes: naming a tenant is not authentication.
        application { module() }

        val response = client.get("/api/tenant/info") {
            header("X-Tenant-Slug", "wemade-demo")
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun tenant_route_with_forged_token_should_return_401() = testApplication {
        application { module() }

        val response = client.get("/api/tenant/info") {
            header("X-Tenant-Slug", "wemade-demo")
            header(HttpHeaders.Authorization, "Bearer not-a-real-signed-token")
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertTrue(response.bodyAsText().contains("tidak valid"))
    }

    @Test
    fun tenant_route_without_header_should_resolve_tenant_from_the_token() = testApplication {
        application { module(twoTenantRepo()) }

        val response = client.get("/api/tenant/info") { asTenantByTokenOnly("pabrik-alpha") }

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("pabrik-alpha"))
    }

    @Test
    fun tenantBoundUser_targetingAnotherTenant_shouldReturn403() = testApplication {
        application { module(twoTenantRepo()) }

        val response = client.get("/api/tenant/info") {
            asTenantTargeting(tokenSlug = "pabrik-alpha", targetSlug = "pabrik-beta")
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertTrue(
            response.bodyAsText().contains("tidak boleh mengakses tenant lain"),
            "Body: ${response.bodyAsText()}"
        )
        // Crucially it must NOT quietly serve Alpha's data as if the request had succeeded.
        assertFalse(response.bodyAsText().contains("pabrik-beta\""))
    }

    @Test
    fun platformSuperadmin_shouldBeAbleToActAsAnyTenant() = testApplication {
        application { module(twoTenantRepo()) }

        val alpha = client.get("/api/tenant/info") { asSuperadminActingAs("pabrik-alpha") }
        val beta = client.get("/api/tenant/info") { asSuperadminActingAs("pabrik-beta") }

        assertEquals(HttpStatusCode.OK, alpha.status)
        assertEquals(HttpStatusCode.OK, beta.status)
        assertTrue(alpha.bodyAsText().contains("pabrik-alpha"))
        assertTrue(beta.bodyAsText().contains("pabrik-beta"))
    }

    @Test
    fun platformSuperadmin_withoutNamingAWorkspace_shouldReturn404WithGuidance() = testApplication {
        application { module(twoTenantRepo()) }

        val response = client.get("/api/tenant/info") { asSuperadmin() }

        assertEquals(HttpStatusCode.NotFound, response.status)
        assertTrue(response.bodyAsText().contains("X-Tenant-Slug"))
    }

    @Test
    fun suspended_tenant_should_return_403_forbidden() = testApplication {
        val testRepo = InMemoryTenantRepository()
        runBlocking {
            testRepo.save(
                Tenant(
                    id = TenantId("ten-suspended-1"),
                    slug = TenantSlug("pabrik-macet"),
                    name = TenantName("Pabrik Macet"),
                    status = TenantStatus.SUSPENDED,
                    tier = SubscriptionTier.STARTER
                )
            )
        }

        application { module(testRepo) }

        val response = client.get("/api/tenant/info") { asTenant("pabrik-macet") }

        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertTrue(response.bodyAsText().contains("suspended"))
    }

    @Test
    fun unknownTenantInToken_shouldReturn404() = testApplication {
        application { module(twoTenantRepo()) }

        val response = client.get("/api/tenant/info") { asTenant("pabrik-yang-tidak-ada") }

        assertEquals(HttpStatusCode.NotFound, response.status)
    }
}

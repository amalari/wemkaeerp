package com.eventverse.app.plugins

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

    @Test
    fun public_onboarding_check_subdomain_should_bypass_tenant_check() = testApplication {
        application {
            module()
        }

        val response = client.get("/api/public/onboarding/check-subdomain?slug=brand-new-convection")
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("\"isAvailable\":true"))
    }

    @Test
    fun tenant_route_with_valid_header_should_resolve_tenant_context() = testApplication {
        application {
            module()
        }

        val response = client.get("/api/tenant/info") {
            header("X-Tenant-Slug", "wemade-demo")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("wemade-demo"))
        assertTrue(response.bodyAsText().contains("PRO"))
    }

    @Test
    fun tenant_route_without_tenant_header_should_return_404() = testApplication {
        application {
            module()
        }

        val response = client.get("/api/tenant/info")
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun suspended_tenant_should_return_403_forbidden() = testApplication {
        val testRepo = InMemoryTenantRepository()
        runBlocking {
            val suspended = Tenant(
                id = TenantId("ten-suspended-1"),
                slug = TenantSlug("pabrik-macet"),
                name = TenantName("Pabrik Macet"),
                status = TenantStatus.SUSPENDED,
                tier = SubscriptionTier.STARTER
            )
            testRepo.save(suspended)
        }

        application {
            module(testRepo)
        }

        val response = client.get("/api/tenant/info") {
            header("X-Tenant-Slug", "pabrik-macet")
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertTrue(response.bodyAsText().contains("suspended"))
    }
}

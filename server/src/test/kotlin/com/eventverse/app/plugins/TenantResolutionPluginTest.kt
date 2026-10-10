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
import kotlinx.datetime.Clock
import kotlin.time.Duration.Companion.days
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
    fun tokenWithUnknownRole_shouldReturn403_notTenantAdmin() = testApplication {
        application { module(twoTenantRepo()) }

        listOf("GALAXY_EMPEROR", null).forEach { raw ->
            val response = client.get("/api/tenant/info") {
                header(HttpHeaders.Authorization, "Bearer ${com.eventverse.app.TestAuth.tokenWithRawRole("pabrik-alpha", raw)}")
            }
            assertEquals(HttpStatusCode.Forbidden, response.status, "role=$raw")
        }
    }

    @Test
    fun public_onboarding_check_subdomain_should_bypass_tenant_check()= testApplication {
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
    fun ipaymu_webhook_bypasses_jwt_because_it_is_guarded_by_signature() = testApplication {
        // Regresi nyata: webhook dulu tertangkap TenantResolutionPlugin (401) karena tidak
        // terdaftar di prefix publik — route test lulus karena hanya memasang routing tanpa
        // plugin. Webhook wajib 200 selalu (docs iPaymu); payload tanpa trx_id/sid dijawab
        // accepted:false tanpa menyentuh repository.
        application { module() }

        val response = client.post("/api/payment/ipaymu/notify") {
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("status=berhasil&amount=1")
        }

        assertEquals(HttpStatusCode.OK, response.status, "webhook iPaymu tidak boleh 401")
        assertTrue(response.bodyAsText().contains("\"accepted\":false"))
    }

    @Test
    fun trial_expired_tenant_workspace_should_return_402_payment_required() = testApplication {
        val expired = InMemoryTenantRepository()
        runBlocking {
            expired.save(
                Tenant(
                    id = TenantId("ten-expired-1"), slug = TenantSlug("pabrik-lunas"),
                    name = TenantName("Pabrik Trial Habis"), status = TenantStatus.TRIAL,
                    tier = SubscriptionTier.PRO, trialEndsAt = Clock.System.now() - 1.days
                )
            )
        }

        application { module(expired) }

        val response = client.get("/api/tenant/info") { asTenant("pabrik-lunas") }

        assertEquals(HttpStatusCode.PaymentRequired, response.status)
        assertTrue(response.bodyAsText().contains("trial"), "Body: ${response.bodyAsText()}")
    }

    @Test
    fun trial_running_tenant_workspace_stays_open() = testApplication {
        val running = InMemoryTenantRepository()
        runBlocking {
            running.save(
                Tenant(
                    id = TenantId("ten-running-1"), slug = TenantSlug("pabrik-jalan"),
                    name = TenantName("Pabrik Trial Jalan"), status = TenantStatus.TRIAL,
                    tier = SubscriptionTier.PRO, trialEndsAt = Clock.System.now() + 7.days
                )
            )
        }

        application { module(running) }

        val response = client.get("/api/tenant/info") { asTenant("pabrik-jalan") }

        assertEquals(HttpStatusCode.OK, response.status, "trial masih berjalan: akses penuh")
    }

    @Test
    fun trial_expired_tenant_builder_console_stays_open_so_they_can_pay() = testApplication {
        val expired = InMemoryTenantRepository()
        runBlocking {
            expired.save(
                Tenant(
                    id = TenantId("ten-expired-2"), slug = TenantSlug("pabrik-bayar"),
                    name = TenantName("Pabrik Bayar"), status = TenantStatus.TRIAL,
                    tier = SubscriptionTier.PRO, trialEndsAt = Clock.System.now() - 1.days
                )
            )
        }

        application { module(expired) }

        // Path di bawah prefix builder: plugin lolos (404 dari routing = gerbang tidak memblokir);
        // bila gerbang salah, responsnya 402, bukan 404.
        val response = client.get("/api/builder/route-yang-tidak-ada") { asTenant("pabrik-bayar") }

        assertEquals(HttpStatusCode.NotFound, response.status, "builder tidak boleh diblokir gerbang trial (402)")
        assertFalse(response.bodyAsText().contains("trial sudah habis"))
    }

    @Test
    fun superadmin_act_as_still_reaches_expired_tenant_for_audit() = testApplication {
        val expired = InMemoryTenantRepository()
        runBlocking {
            expired.save(
                Tenant(
                    id = TenantId("ten-expired-3"), slug = TenantSlug("pabrik-audit"),
                    name = TenantName("Pabrik Audit"), status = TenantStatus.TRIAL,
                    tier = SubscriptionTier.PRO, trialEndsAt = Clock.System.now() - 1.days
                )
            )
        }

        application { module(expired) }

        val response = client.get("/api/tenant/info") { asSuperadminActingAs("pabrik-audit") }

        assertEquals(HttpStatusCode.OK, response.status, "superadmin tetap bisa mengaudit tenant expired")
    }

    @Test
    fun unknownTenantInToken_shouldReturn404() = testApplication {
        application { module(twoTenantRepo()) }

        val response = client.get("/api/tenant/info") { asTenant("pabrik-yang-tidak-ada") }

        assertEquals(HttpStatusCode.NotFound, response.status)
    }
}

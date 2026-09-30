package com.eventverse.app.routes

import com.eventverse.app.domain.auth.EmailAddress
import com.eventverse.app.domain.auth.User
import com.eventverse.app.domain.auth.UserId
import com.eventverse.app.domain.auth.UserRepository
import com.eventverse.app.domain.auth.Username
import com.eventverse.app.domain.tenant.CheckSubdomainAvailabilityUseCase
import com.eventverse.app.domain.tenant.RegisterTenantUseCase
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.infrastructure.InMemoryTenantRepository
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Gerbang daftar publik tenant (FR-M2-7, plan M2).
 *
 * Yang diuji bukan "form daftar tampil", melainkan **arah gagalnya**: dengan flag tertutup
 * (keadaan bawaan) `POST /register` harus menolak, bukan diam-diam membuat tenant. Endpoint tetangga
 * (`/config`, `/check-subdomain`) tetap hidup supaya alur undangan M0–M1 tidak ikut mati.
 */
class OnboardingSignupFlagTest {

    private class TestUserRepository : UserRepository {
        private val users = mutableMapOf<UserId, User>()
        override suspend fun findById(id: UserId): User? = users[id]
        override suspend fun findByUsername(tenantId: TenantId?, username: Username): User? =
            users.values.find { it.tenantId == tenantId && it.username == username }
        override suspend fun findByEmail(email: EmailAddress): User? = users.values.find { it.email == email }
        override suspend fun save(user: User): Result<User> { users[user.id] = user; return Result.success(user) }
        override suspend fun findAllByTenant(tenantId: TenantId): List<User> =
            users.values.filter { it.tenantId == tenantId }
    }

    /** Install minimal: cukup route onboarding, tanpa Postgres (pola `RouteGateTest`). */
    private fun io.ktor.server.testing.ApplicationTestBuilder.installOnboarding(publicSignup: Boolean) {
        val tenants = InMemoryTenantRepository()
        application {
            routing {
                onboardingRoutes(
                    RegisterTenantUseCase(tenants, TestUserRepository()),
                    CheckSubdomainAvailabilityUseCase(tenants),
                    publicSignupEnabled = publicSignup
                )
            }
        }
    }

    @Test
    fun config_reportsSignupClosed_byDefault() = testApplication {
        installOnboarding(publicSignup = false)

        val response = client.get("/api/public/onboarding/config")

        assertEquals(200, response.status.value)
        assertTrue(
            response.bodyAsText().contains("\"publicSignupEnabled\":false"),
            "bawaan tertutup: ${response.bodyAsText()}"
        )
    }

    @Test
    fun register_whenFlagClosed_returns403() = testApplication {
        installOnboarding(publicSignup = false)

        val response = client.post("/api/public/onboarding/register") {
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("id=ten-tak-sengaja&slug=pabrik-anonim&name=Pabrik%20Anonim")
        }

        assertEquals(403, response.status.value, "flag tertutup → pendaftaran ditolak")
    }

    @Test
    fun subdomainCheck_stillWorks_whenFlagClosed() = testApplication {
        installOnboarding(publicSignup = false)

        val response = client.get("/api/public/onboarding/check-subdomain?slug=pabrik-baru")

        assertEquals(200, response.status.value, "flag hanya menggerbangi register, bukan pemeriksaan slug")
        assertTrue(response.bodyAsText().contains("\"isAvailable\""))
    }

    @Test
    fun register_whenFlagOpen_createsTenant() = testApplication {
        installOnboarding(publicSignup = true)

        val response = client.post("/api/public/onboarding/register") {
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("id=ten-bordir-uji&slug=bordir-uji&name=Bordir%20Uji")
        }

        assertEquals(201, response.status.value, "flag terbuka → tenant dibuat: ${response.bodyAsText()}")
        assertTrue(response.bodyAsText().contains("\"slug\":\"bordir-uji\""))
    }
}

package com.eventverse.app.routes

import com.eventverse.app.domain.auth.AuthenticateWithGoogleUseCase
import com.eventverse.app.domain.auth.EmailAddress
import com.eventverse.app.domain.auth.Role
import com.eventverse.app.domain.auth.User
import com.eventverse.app.domain.auth.UserId
import com.eventverse.app.domain.auth.UserRepository
import com.eventverse.app.domain.auth.Username
import com.eventverse.app.domain.tenant.SubscriptionTier
import com.eventverse.app.domain.tenant.Tenant
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.domain.tenant.TenantName
import com.eventverse.app.domain.tenant.TenantSlug
import com.eventverse.app.domain.tenant.TenantStatus
import com.eventverse.app.infrastructure.InMemoryRoleRepository
import com.eventverse.app.infrastructure.InMemoryTenantRepository
import com.eventverse.app.infrastructure.auth.GoogleAuthService
import com.eventverse.app.infrastructure.auth.JwtTokenService
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.parameters
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `POST /api/public/auth/google` membaca permukaan host (discovery-M3-login-split): di `app.` tenant
 * diturunkan dari akun, di `<slug>.` host yang menentukan, dan slug yang bertentangan dengan host ditolak.
 */
class PublicAuthHostSurfaceTest {

    private val base = "wemakeerp.com"

    private class TestUserRepository : UserRepository {
        val users = mutableMapOf<UserId, User>()
        override suspend fun findById(id: UserId): User? = users[id]
        override suspend fun findByUsername(tenantId: TenantId?, username: Username): User? =
            users.values.find { it.tenantId == tenantId && it.username == username }
        override suspend fun findByEmail(email: EmailAddress): User? = users.values.find { it.email == email }
        override suspend fun save(user: User): Result<User> { users[user.id] = user; return Result.success(user) }
        override suspend fun findAllByTenant(tenantId: TenantId): List<User> = users.values.filter { it.tenantId == tenantId }
    }

    private fun ApplicationTestBuilder.install() {
        val tenants = InMemoryTenantRepository()
        val users = TestUserRepository()
        runBlocking {
            tenants.save(
                Tenant(TenantId("ten-bordir"), TenantSlug("bordir-uji"), TenantName("Bordir Uji"), TenantStatus.TRIAL, SubscriptionTier.PRO)
            )
            users.save(
                User(UserId("usr-bordir-owner"), TenantId("ten-bordir"), Username("owner_bordir"), EmailAddress("owner@bordir.id"), Role.TENANT_ADMIN, isActive = true)
            )
        }
        application {
            routing {
                publicAuthRoutes(
                    GoogleAuthService(clientId = "test", clientSecret = "test"),
                    AuthenticateWithGoogleUseCase(users, tenants),
                    JwtTokenService(),
                    tenants,
                    users,
                    InMemoryRoleRepository(),
                    platformBaseDomain = base
                )
            }
        }
    }

    private suspend fun ApplicationTestBuilder.google(host: String, slug: String?): HttpResponse =
        client.submitForm(
            "/api/public/auth/google",
            parameters {
                append("idToken", "mock-google-token:owner@bordir.id")
                if (slug != null) append("tenantSlug", slug)
            }
        ) { header(HttpHeaders.Host, host) }

    @Test
    fun `google on platform host without slug should resolve tenant from account`() = testApplication {
        install()
        val response = google("app.$base", slug = null)

        assertEquals(200, response.status.value)
        assertTrue(response.bodyAsText().contains("\"tenantSlug\":\"bordir-uji\""))
    }

    @Test
    fun `google on tenant host without slug should use host tenant`() = testApplication {
        install()
        assertEquals(200, google("bordir-uji.$base", slug = null).status.value)
    }

    @Test
    fun `google on tenant host with conflicting slug should be 403`() = testApplication {
        install()
        assertEquals(403, google("bordir-uji.$base", slug = "wemade-demo").status.value)
    }

    @Test
    fun `google on local host without slug should be 400 as before`() = testApplication {
        install()
        assertEquals(400, google("localhost", slug = null).status.value)
    }
}

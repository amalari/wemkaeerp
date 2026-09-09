package com.eventverse.app

import com.eventverse.app.domain.auth.*
import com.eventverse.app.domain.tenant.*
import com.eventverse.app.infrastructure.InMemoryTenantRepository
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.*

class GoogleAuthIntegrationTest {

    private class TestUserRepository : UserRepository {
        private val users = mutableMapOf<UserId, User>()

        override suspend fun findById(id: UserId): User? = users[id]
        override suspend fun findByUsername(tenantId: TenantId?, username: Username): User? =
            users.values.find { it.tenantId == tenantId && it.username == username }
        override suspend fun findByEmail(email: EmailAddress): User? =
            users.values.find { it.email == email }
        override suspend fun save(user: User): Result<User> {
            users[user.id] = user
            return Result.success(user)
        }
        override suspend fun findAllByTenant(tenantId: TenantId): List<User> =
            users.values.filter { it.tenantId == tenantId }
    }

    @Test
    fun google_auth_url_endpoint_should_return_valid_oauth_url() = testApplication {
        val tenantRepo = InMemoryTenantRepository()
        val userRepo = TestUserRepository()

        application {
            module(tenantRepository = tenantRepo, userRepository = userRepo)
        }

        val response = client.get("/api/public/auth/google/url?redirect_uri=http://localhost:8080/callback")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("accounts.google.com/o/oauth2/v2/auth"))
        assertTrue(body.contains("http%3A%2F%2Flocalhost%3A8080%2Fcallback"))
    }

    @Test
    fun google_login_with_registered_user_should_return_jwt_session() = testApplication {
        val tenantRepo = InMemoryTenantRepository()
        val userRepo = TestUserRepository()

        // Seed demo tenant and user
        val tenant = Tenant(
            id = TenantId("ten-demo"),
            slug = TenantSlug("berkah-konveksi"),
            name = TenantName("Konveksi Berkah"),
            status = TenantStatus.ACTIVE,
            tier = SubscriptionTier.PRO
        )
        tenantRepo.save(tenant)

        val user = User(
            id = UserId("usr-owner-01"),
            tenantId = TenantId("ten-demo"),
            username = Username("owner_berkah"),
            email = EmailAddress("owner@berkah.com"),
            role = Role.TENANT_ADMIN,
            isActive = true
        )
        userRepo.save(user)

        application {
            module(tenantRepository = tenantRepo, userRepository = userRepo)
        }

        val response = client.post("/api/public/auth/google") {
            header(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
            setBody("idToken=mock-google-token:owner@berkah.com&tenantSlug=berkah-konveksi")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("\"token\":"), "Expected JWT token in response: $body")
        assertTrue(body.contains("\"username\":\"owner_berkah\""), "Expected username in response: $body")
        assertTrue(body.contains("\"role\":\"TENANT_ADMIN\""), "Expected role in response: $body")
    }

    @Test
    fun google_login_with_unregistered_email_should_return_forbidden() = testApplication {
        val tenantRepo = InMemoryTenantRepository()
        val userRepo = TestUserRepository()

        val tenant = Tenant(
            id = TenantId("ten-demo"),
            slug = TenantSlug("berkah-konveksi"),
            name = TenantName("Konveksi Berkah"),
            status = TenantStatus.ACTIVE,
            tier = SubscriptionTier.PRO
        )
        tenantRepo.save(tenant)

        application {
            module(tenantRepository = tenantRepo, userRepository = userRepo)
        }

        val response = client.post("/api/public/auth/google") {
            header(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
            setBody("idToken=mock-google-token:unregistered@gmail.com&tenantSlug=berkah-konveksi")
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("belum terdaftar"), "Expected unregistered message: $body")
    }
}

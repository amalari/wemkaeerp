package com.eventverse.app

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
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
import com.eventverse.app.infrastructure.DatabaseFactory
import com.eventverse.app.infrastructure.InMemoryAuditLogRepository
import com.eventverse.app.infrastructure.InMemoryRoleRepository
import com.eventverse.app.infrastructure.InMemoryTenantRepository
import com.eventverse.app.routes.DemoLoginPolicy
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Gerbang login demo (`POST /api/public/auth/demo`): bawaan mati, dan saat menyala hanya tenant demo.
 * Fixture non-default: tenant non-demo `pabrik-bukan-demo` dan tenant demo tambahan `bordir-uji-gate`.
 */
class DemoAuthGateApiTest {

    private val demoSlug = "wemade-demo"
    private val demoTenant = Tenant(TenantId("ten-gate-demo"), TenantSlug(demoSlug), TenantName("Demo"), TenantStatus.ACTIVE, SubscriptionTier.PRO)
    private val otherTenant = Tenant(TenantId("ten-gate-other"), TenantSlug("pabrik-bukan-demo"), TenantName("Bukan Demo"), TenantStatus.ACTIVE, SubscriptionTier.PRO)
    private val extraDemo = Tenant(TenantId("ten-gate-extra"), TenantSlug("bordir-uji-gate"), TenantName("Bordir Uji"), TenantStatus.ACTIVE, SubscriptionTier.PRO)

    /** Repo user minimal; `leaky` meniru repo buggy yang mengembalikan user lintas tenant dari findAllByTenant. */
    private class FakeUsers(private val leaky: Boolean = false) : UserRepository {
        val all = mutableListOf<User>()
        override suspend fun findById(id: UserId) = all.firstOrNull { it.id == id }
        override suspend fun findByUsername(tenantId: TenantId?, username: Username) = all.firstOrNull { it.username == username }
        override suspend fun findByEmail(email: EmailAddress) = all.firstOrNull { it.email == email }
        override suspend fun save(user: User): Result<User> {
            all.removeAll { it.id == user.id }
            all += user
            return Result.success(user)
        }
        override suspend fun findAllByTenant(tenantId: TenantId) = if (leaky) all.toList() else all.filter { it.tenantId == tenantId }
    }

    private fun ApplicationTestBuilder.boot(users: UserRepository, policy: DemoLoginPolicy) {
        DatabaseFactory.init()
        val tenants = InMemoryTenantRepository()
        runBlocking { listOf(demoTenant, otherTenant, extraDemo).forEach { tenants.save(it) } }
        application {
            module(
                tenantRepository = tenants, userRepository = users, roleRepository = InMemoryRoleRepository(),
                auditLogRepository = InMemoryAuditLogRepository(), demoLoginPolicy = policy
            )
        }
    }

    private suspend fun ApplicationTestBuilder.demo(slug: String, vararg form: Pair<String, String>) =
        client.post("/api/public/auth/demo") {
            contentType(ContentType.Application.FormUrlEncoded)
            setBody((listOf("tenantSlug" to slug) + form).joinToString("&") { "${it.first}=${it.second}" })
        }

    private val on = DemoLoginPolicy(enabled = true)

    @Test
    fun demoLogin_gateOff_everyPersonaIs404AndNoUserCreated() = testApplication {
        val users = FakeUsers()
        boot(users, DemoLoginPolicy())
        assertEquals(HttpStatusCode.NotFound, demo(demoSlug, "role" to "PLATFORM_SUPERADMIN").status)
        assertEquals(HttpStatusCode.NotFound, demo(demoSlug, "role" to "TENANT_ADMIN").status)
        assertEquals(HttpStatusCode.NotFound, demo(demoSlug, "username" to "Budi", "role" to "role-sales").status)
        assertEquals(HttpStatusCode.NotFound, demo("pabrik-bukan-demo").status)
        assertTrue(users.all.isEmpty(), "gerbang mati tidak boleh membuat user")
    }

    @Test
    fun demoLogin_defaultPolicyIsOff() {
        assertEquals(false, DemoLoginPolicy().enabled)
        assertEquals(false, DemoLoginPolicy.fromEnv { null }.enabled)
        assertEquals(true, DemoLoginPolicy.fromEnv { if (it == "WEMADE_DEMO_LOGIN") "on" else null }.enabled)
    }

    @Test
    fun demoLogin_gateOnDemoTenant_ownerGets200WithTenantBoundToken() = testApplication {
        boot(FakeUsers(), on)
        val response = demo(demoSlug, "role" to "TENANT_ADMIN")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue("\"tenantSlug\":\"$demoSlug\"" in body)
        assertTrue("\"tenantId\":\"ten-gate-demo\"" in body)
    }

    @Test
    fun demoLogin_gateOnDemoTenant_superadminGets200() = testApplication {
        boot(FakeUsers(), on)
        val response = demo(demoSlug, "role" to "PLATFORM_SUPERADMIN")
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue("\"role\":\"PLATFORM_SUPERADMIN\"" in response.bodyAsText())
    }

    @Test
    fun demoLogin_gateOn_nonDemoTenantRejectedForOwnerSuperadminAndPersona() = testApplication {
        val users = FakeUsers()
        boot(users, on)
        assertEquals(HttpStatusCode.Forbidden, demo("pabrik-bukan-demo", "role" to "TENANT_ADMIN").status)
        assertEquals(HttpStatusCode.Forbidden, demo("pabrik-bukan-demo", "role" to "PLATFORM_SUPERADMIN").status)
        assertEquals(HttpStatusCode.Forbidden, demo("pabrik-bukan-demo", "username" to "Budi").status)
        assertTrue(users.all.isEmpty())
    }

    @Test
    fun demoLogin_extraDemoTenantFromConfig_isAllowed() = testApplication {
        boot(FakeUsers(), DemoLoginPolicy(enabled = true, demoTenantSlugs = DemoLoginPolicy.DEFAULT_DEMO_TENANTS + "bordir-uji-gate"))
        assertEquals(HttpStatusCode.OK, demo("bordir-uji-gate", "role" to "TENANT_ADMIN").status)
    }

    @Test
    fun demoLogin_gateOn_personaOnDemoTenantGets200() = testApplication {
        boot(FakeUsers(), on)
        val response = demo(demoSlug, "username" to "Budi")
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue("\"tenantSlug\":\"$demoSlug\"" in response.bodyAsText())
    }

    @Test
    fun demoLogin_noGlobalEmailFallback_userOfOtherTenantIsNeverBorrowed() = testApplication {
        val users = FakeUsers()
        users.all += User(UserId("usr-student"), otherTenant.id, Username("student"), EmailAddress("student.achmad@gmail.com"), Role.TENANT_ADMIN)
        boot(users, on)
        val body = demo(demoSlug, "role" to "TENANT_ADMIN").bodyAsText()
        assertTrue("ten-gate-other" !in body, "user tenant lain tidak boleh dipinjam: $body")
        assertNotEquals("usr-student", users.all.first { it.tenantId == demoTenant.id }.id.value)
    }

    @Test
    fun demoLogin_userNotOwnedByRequestedTenant_isRejected() = testApplication {
        val users = FakeUsers(leaky = true)
        users.all += User(UserId("usr-leak"), otherTenant.id, Username("leak"), EmailAddress("leak@x.test"), Role.TENANT_ADMIN)
        boot(users, on)
        assertEquals(HttpStatusCode.Forbidden, demo(demoSlug, "role" to "TENANT_ADMIN").status)
    }

    @Test
    fun demoLogin_superadmin_isCreatedPlatformLevelWithoutTenantId() = testApplication {
        val users = FakeUsers()
        boot(users, on)
        val body = demo(demoSlug, "role" to "PLATFORM_SUPERADMIN").bodyAsText()
        assertTrue("\"tenantId\":\"\"" in body, "superadmin tidak boleh terikat tenant: $body")
        assertEquals(null, users.all.single().tenantId)
    }

    @Test
    fun demoLogin_superadmin_legacyTenantBoundRowIsUnbound() = testApplication {
        val users = FakeUsers()
        users.all += User(UserId("usr-superadmin-001"), otherTenant.id, Username("superadmin_apps"), EmailAddress("superadmin@wemade.id"), Role.PLATFORM_SUPERADMIN)
        boot(users, on)
        val body = demo(demoSlug, "role" to "PLATFORM_SUPERADMIN").bodyAsText()
        assertTrue("ten-gate-other" !in body, body)
        assertEquals(null, users.all.single().tenantId)
    }

    @Test
    fun demoLogin_superadminEmailHeldByNonSuperadmin_isRejectedNotBorrowed() = testApplication {
        val users = FakeUsers()
        users.all += User(UserId("usr-imposter"), otherTenant.id, Username("imposter"), EmailAddress("superadmin@wemade.id"), Role.TENANT_ADMIN)
        boot(users, on)
        assertEquals(HttpStatusCode.Forbidden, demo(demoSlug, "role" to "PLATFORM_SUPERADMIN").status)
    }

    @Test
    fun demoLogin_personaWithVeryLongName_gets200AndDistinctUsernames() = testApplication {
        val users = FakeUsers()
        boot(users, on)
        val prefix = "Bapak%20Haji%20Muhammad%20Abdurrahman%20Wahid%20Suryadiningrat%20"
        assertEquals(HttpStatusCode.OK, demo(demoSlug, "username" to prefix + "Pertama").status)
        assertEquals(HttpStatusCode.OK, demo(demoSlug, "username" to prefix + "Kedua").status)
        assertEquals(2, users.all.map { it.username.value }.distinct().size)
        assertTrue(users.all.all { it.username.value.length <= 30 })
    }

    private fun jwt(role: Role, tenantSlug: String?, tenantId: String?): String {
        val secret = System.getenv("JWT_SECRET") ?: "wemade-erp-default-development-secret-key-32-chars-long!"
        val now = Date()
        return JWT.create().withIssuer("wemade-erp").withSubject("usr-claim-test")
            .withClaim("tenant_id", tenantId).withClaim("tenant_slug", tenantSlug)
            .withClaim("username", "tester").withClaim("email", "t@x.test").withClaim("role", role.name)
            .withIssuedAt(now).withExpiresAt(Date(now.time + 3_600_000L)).sign(Algorithm.HMAC256(secret))
    }

    @Test
    fun me_blankTenantSlugClaimOnTenantToken_isRejected() = testApplication {
        boot(FakeUsers(), on)
        for (slug in listOf(null, "")) {
            val status = client.get("/api/public/auth/me") {
                header(HttpHeaders.Authorization, "Bearer ${jwt(Role.TENANT_ADMIN, slug, "ten-gate-demo")}")
            }.status
            assertEquals(HttpStatusCode.Unauthorized, status, "slug=$slug")
        }
    }

    @Test
    fun tenantRoutes_blankTenantSlugClaimOnTenantToken_isRejected() = testApplication {
        boot(FakeUsers(), on)
        for (slug in listOf(null, "")) {
            val status = client.get("/api/tenant/info") {
                header(HttpHeaders.Authorization, "Bearer ${jwt(Role.TENANT_ADMIN, slug, "ten-gate-demo")}")
            }.status
            assertEquals(HttpStatusCode.Forbidden, status, "slug=$slug")
        }
    }

    private suspend fun ApplicationTestBuilder.demoWithoutSlug(vararg form: Pair<String, String>) =
        client.post("/api/public/auth/demo") {
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(form.joinToString("&") { "${it.first}=${it.second}" })
        }

    @Test
    fun demoLogin_gateOnWithoutTenantSlug_is400AndNoUserCreated() = testApplication {
        val users = FakeUsers()
        boot(users, DemoLoginPolicy(enabled = true, demoTenantSlugs = setOf("wemade-demo", "bordir-uji-gate")))
        val response = demoWithoutSlug("role" to "TENANT_ADMIN")
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue("tenantSlug" in response.bodyAsText())
        assertEquals(HttpStatusCode.BadRequest, demoWithoutSlug("username" to "Budi", "role" to "role-sales").status)
        assertEquals(HttpStatusCode.BadRequest, demoWithoutSlug("tenantSlug" to "", "role" to "TENANT_ADMIN").status)
        assertTrue(users.all.isEmpty(), "tanpa slug tidak boleh membuat user atau menebak tenant demo")
    }

    @Test
    fun demoLogin_gateOffWithoutTenantSlug_stays404() = testApplication {
        boot(FakeUsers(), DemoLoginPolicy())
        assertEquals(HttpStatusCode.NotFound, demoWithoutSlug("role" to "TENANT_ADMIN").status)
    }

    @Test
    fun demoLogin_extraDemoTenantWithSlug_stillWorks() = testApplication {
        boot(FakeUsers(), DemoLoginPolicy(enabled = true, demoTenantSlugs = setOf("wemade-demo", "bordir-uji-gate")))
        val response = demo("bordir-uji-gate", "role" to "TENANT_ADMIN")
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue("\"tenantSlug\":\"bordir-uji-gate\"" in response.bodyAsText())
    }

    @Test
    fun me_validTenantToken_stillWorks() = testApplication {
        boot(FakeUsers(), on)
        val status = client.get("/api/public/auth/me") {
            header(HttpHeaders.Authorization, "Bearer ${jwt(Role.TENANT_ADMIN, demoSlug, "ten-gate-demo")}")
        }.status
        assertEquals(HttpStatusCode.OK, status)
    }
}

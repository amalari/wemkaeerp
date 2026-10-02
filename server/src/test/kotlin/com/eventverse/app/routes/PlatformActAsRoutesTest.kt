package com.eventverse.app.routes

import com.eventverse.app.domain.audit.AuditAction
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
import com.eventverse.app.infrastructure.InMemoryAuditLogRepository
import com.eventverse.app.infrastructure.InMemoryTenantRepository
import com.eventverse.app.infrastructure.auth.JwtTokenService
import com.eventverse.app.plugins.CallerPrincipal
import com.eventverse.app.plugins.CallerPrincipalAttributeKey
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.request.header
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Act-as Builder di `app.` (tanpa pindah origin): hanya superadmin, wajib ber-audit, sesi ditambatkan ke
 * tenant tujuan. Tenant pemilik **tidak boleh** memakainya (harus 403) — termasuk untuk tenantnya sendiri.
 *
 * `TenantResolutionPlugin` tidak dipasang; interceptor kecil meniru tugasnya (mengisi `CallerPrincipal`
 * dari JWT) supaya yang diuji adalah pemeriksaan di route itu sendiri.
 */
class PlatformActAsRoutesTest {

    private val jwt = JwtTokenService()
    private val audit = InMemoryAuditLogRepository()

    private class Users : UserRepository {
        val byId = mutableMapOf<UserId, User>()
        override suspend fun findById(id: UserId): User? = byId[id]
        override suspend fun findByUsername(tenantId: TenantId?, username: Username): User? =
            byId.values.find { it.tenantId == tenantId && it.username == username }
        override suspend fun findByEmail(email: EmailAddress): User? = byId.values.find { it.email == email }
        override suspend fun save(user: User): Result<User> { byId[user.id] = user; return Result.success(user) }
        override suspend fun findAllByTenant(tenantId: TenantId): List<User> = byId.values.filter { it.tenantId == tenantId }
    }

    private val owner = User(UserId("usr-owner-bordir"), TenantId("ten-bordir"), Username("owner_bordir"), EmailAddress("owner@bordir.id"), Role.TENANT_ADMIN, isActive = true)
    private val superadmin = User(UserId("usr-superadmin-001"), null, Username("superadmin_apps"), EmailAddress("superadmin@wemade.id"), Role.PLATFORM_SUPERADMIN, isActive = true)

    private fun ApplicationTestBuilder.install() {
        val tenants = InMemoryTenantRepository()
        val users = Users()
        runBlocking {
            tenants.save(Tenant(TenantId("ten-bordir"), TenantSlug("bordir-uji"), TenantName("Bordir Uji"), TenantStatus.TRIAL, SubscriptionTier.PRO))
            users.save(owner)
            users.save(superadmin)
        }
        application {
            intercept(ApplicationCallPipeline.Plugins) {
                val decoded = call.request.header(HttpHeaders.Authorization)?.removePrefix("Bearer ")
                    ?.let { jwt.verifyToken(it).getOrNull() }
                if (decoded != null) {
                    call.attributes.put(
                        CallerPrincipalAttributeKey,
                        CallerPrincipal(
                            userId = decoded.subject.orEmpty(),
                            role = Role.valueOf(decoded.getClaim("role").asString()),
                            tenantId = decoded.getClaim("tenant_id").asString()?.takeIf { it.isNotBlank() }?.let { TenantId(it) },
                            tenantSlug = decoded.getClaim("tenant_slug").asString()?.takeIf { it.isNotBlank() }
                        )
                    )
                }
            }
            routing { platformActAsRoutes(tenants, users, jwt, audit) }
        }
    }

    private suspend fun ApplicationTestBuilder.actAs(user: User?, slug: String): HttpResponse =
        client.post("/api/admin/tenants/$slug/act-as") {
            user?.let { header(HttpHeaders.Authorization, "Bearer ${jwt.generateToken(it).value}") }
        }

    @Test
    fun `superadmin act-as should audit and return session anchored to target tenant`() = testApplication {
        install()
        val response = actAs(superadmin, "bordir-uji")
        val body = response.bodyAsText()

        assertEquals(200, response.status.value, body)
        assertTrue(body.contains("\"tenantSlug\":\"bordir-uji\""), body)
        assertTrue(body.contains("\"tenantId\":\"ten-bordir\""), body)
        assertTrue(body.contains("\"role\":\"PLATFORM_SUPERADMIN\""), body)
        assertEquals(listOf(AuditAction.PLATFORM_ACT_AS_STARTED), audit.findByTenant(TenantId("ten-bordir")).map { it.action })
    }

    @Test
    fun `tenant owner act-as should be 403 even for own tenant and leave no audit`() = testApplication {
        install()
        assertEquals(403, actAs(owner, "bordir-uji").status.value)
        assertTrue(audit.findByTenant(TenantId("ten-bordir")).isEmpty())
    }

    @Test
    fun `act-as without session should be 403`() = testApplication {
        install()
        assertEquals(403, actAs(null, "bordir-uji").status.value)
    }

    @Test
    fun `superadmin act-as to unknown tenant should be 404`() = testApplication {
        install()
        assertEquals(404, actAs(superadmin, "tidak-ada").status.value)
    }
}

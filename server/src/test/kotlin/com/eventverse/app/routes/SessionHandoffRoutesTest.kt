package com.eventverse.app.routes

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
import com.eventverse.app.domain.audit.AuditAction
import com.eventverse.app.infrastructure.InMemoryAuditLogRepository
import com.eventverse.app.infrastructure.InMemoryTenantRepository
import com.eventverse.app.infrastructure.auth.JwtTokenService
import com.eventverse.app.infrastructure.auth.SessionHandoffTicketService
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.header
import io.ktor.client.request.post
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
 * Serah-terima sesi `app.` → `<slug>.` (discovery-M3-login-split). Fixture tenant non-garmen
 * (`bordir-uji`, TRIAL) supaya tidak ada yang lolos karena kebetulan data rajut.
 *
 * Yang dikunci adalah arah gagalnya: tiket tidak boleh dipakai dua kali, tidak boleh dibuka di
 * subdomain tenant lain, tidak boleh ditukar di `app.`, dan superadmin tidak mendapat tiket.
 */
class SessionHandoffRoutesTest {

    private val base = "wemakeerp.com"
    private val jwt = JwtTokenService()
    private val audit = InMemoryAuditLogRepository()

    private class TestUserRepository : UserRepository {
        val users = mutableMapOf<UserId, User>()
        override suspend fun findById(id: UserId): User? = users[id]
        override suspend fun findByUsername(tenantId: TenantId?, username: Username): User? =
            users.values.find { it.tenantId == tenantId && it.username == username }
        override suspend fun findByEmail(email: EmailAddress): User? = users.values.find { it.email == email }
        override suspend fun save(user: User): Result<User> { users[user.id] = user; return Result.success(user) }
        override suspend fun findAllByTenant(tenantId: TenantId): List<User> = users.values.filter { it.tenantId == tenantId }
    }

    private val owner = User(
        id = UserId("usr-bordir-owner"),
        tenantId = TenantId("ten-bordir"),
        username = Username("owner_bordir"),
        email = EmailAddress("owner@bordir.id"),
        role = Role.TENANT_ADMIN,
        isActive = true
    )
    private val superadmin = User(
        id = UserId("usr-superadmin-001"),
        tenantId = null,
        username = Username("superadmin_apps"),
        email = EmailAddress("superadmin@wemade.id"),
        role = Role.PLATFORM_SUPERADMIN,
        isActive = true
    )

    private fun ApplicationTestBuilder.install(): TestUserRepository {
        val tenants = InMemoryTenantRepository()
        val users = TestUserRepository()
        runBlocking {
            tenants.save(
                Tenant(TenantId("ten-bordir"), TenantSlug("bordir-uji"), TenantName("Bordir Uji"), TenantStatus.TRIAL, SubscriptionTier.PRO)
            )
            users.save(owner)
            users.save(superadmin)
        }
        application {
            routing { sessionHandoffRoutes(SessionHandoffTicketService(), jwt, tenants, users, base, audit) }
        }
        return users
    }

    private suspend fun ApplicationTestBuilder.issue(user: User, actAs: String? = null): HttpResponse =
        client.submitForm("/api/public/auth/handoff/issue", parameters { actAs?.let { append("actAs", it) } }) {
            header(HttpHeaders.Host, "app.$base")
            header(HttpHeaders.Authorization, "Bearer ${jwt.generateToken(user, user.tenantId?.let { "bordir-uji" }).value}")
        }

    private suspend fun ApplicationTestBuilder.ticketFor(user: User, actAs: String? = null): String =
        requireNotNull(Regex("\"ticket\":\"([^\"]+)\"").find(issue(user, actAs).bodyAsText())) { "tiket tidak terbit" }.groupValues[1]

    private suspend fun ApplicationTestBuilder.redeem(ticket: String, host: String): HttpResponse =
        client.submitForm("/api/public/auth/handoff", parameters { append("ticket", ticket) }) {
            header(HttpHeaders.Host, host)
        }

    @Test
    fun `issue for tenant owner should return ticket and tenant origin`() = testApplication {
        install()
        val body = issue(owner).bodyAsText()

        assertTrue(body.contains("\"tenantSlug\":\"bordir-uji\""), body)
        assertTrue(body.contains("\"origin\":\"https://bordir-uji.wemakeerp.com\""), body)
    }

    @Test
    fun `issue without valid session should be 401`() = testApplication {
        install()
        val response = client.post("/api/public/auth/handoff/issue") {
            header(HttpHeaders.Authorization, "Bearer bukan-token")
        }
        assertEquals(401, response.status.value)
    }

    @Test
    fun `issue for superadmin without actAs target should be 400`() = testApplication {
        install()
        assertEquals(400, issue(superadmin).status.value)
    }

    // --- Act-as superadmin (discovery-M3b): boleh masuk tenant mana pun, wajib ber-audit ---

    @Test
    fun `superadmin act-as should record audit on target tenant and anchor session to it`() = testApplication {
        install()
        val ticket = ticketFor(superadmin, actAs = "bordir-uji")

        val entries = audit.findByTenant(TenantId("ten-bordir"))
        assertEquals(listOf(AuditAction.PLATFORM_ACT_AS_STARTED), entries.map { it.action })
        assertEquals("usr-superadmin-001", entries.single().actorUserId)

        val session = redeem(ticket, "bordir-uji.$base")
        assertEquals(200, session.status.value)
        val body = session.bodyAsText()
        assertTrue(body.contains("\"tenantId\":\"ten-bordir\""), body)
        assertTrue(body.contains("\"role\":\"PLATFORM_SUPERADMIN\""), body)
    }

    @Test
    fun `tenant owner using actAs should be 403 and leave no audit`() = testApplication {
        install()
        assertEquals(403, issue(owner, actAs = "bordir-uji").status.value)
        assertTrue(audit.findByTenant(TenantId("ten-bordir")).isEmpty())
    }

    @Test
    fun `superadmin act-as to unknown tenant should be 403`() = testApplication {
        install()
        assertEquals(403, issue(superadmin, actAs = "tidak-ada").status.value)
    }

    @Test
    fun `superadmin act-as ticket on another tenant subdomain should be 401`() = testApplication {
        install()
        assertEquals(401, redeem(ticketFor(superadmin, actAs = "bordir-uji"), "wemade-demo.$base").status.value)
    }

    @Test
    fun `redeem on own subdomain should return session once then 401 on replay`() = testApplication {
        install()
        val ticket = ticketFor(owner)

        val first = redeem(ticket, "bordir-uji.$base")
        assertEquals(200, first.status.value)
        assertTrue(first.bodyAsText().contains("\"tenantSlug\":\"bordir-uji\""))

        assertEquals(401, redeem(ticket, "bordir-uji.$base").status.value)
    }

    @Test
    fun `redeem on another tenant subdomain should be 401`() = testApplication {
        install()
        assertEquals(401, redeem(ticketFor(owner), "wemade-demo.$base").status.value)
    }

    @Test
    fun `redeem on platform host should be 403`() = testApplication {
        install()
        assertEquals(403, redeem(ticketFor(owner), "app.$base").status.value)
    }

    @Test
    fun `redeem after account deactivated should be 403`() = testApplication {
        val users = install()
        val ticket = ticketFor(owner)
        users.users[owner.id] = owner.copy(isActive = false)

        assertEquals(403, redeem(ticket, "bordir-uji.$base").status.value)
    }

    @Test
    fun `redeem session token should not be accepted as ticket`() = testApplication {
        install()
        assertEquals(401, redeem(jwt.generateToken(owner, "bordir-uji").value, "bordir-uji.$base").status.value)
    }
}

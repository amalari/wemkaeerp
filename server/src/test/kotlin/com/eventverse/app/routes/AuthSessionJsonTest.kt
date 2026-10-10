package com.eventverse.app.routes

import com.eventverse.app.domain.auth.EmailAddress
import com.eventverse.app.domain.auth.Role
import com.eventverse.app.domain.auth.User
import com.eventverse.app.domain.auth.UserId
import com.eventverse.app.domain.auth.Username
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class AuthSessionJsonTest {
    @Test
    fun authSessionJson_withQuotesAndBackslashes_shouldStayValidJsonAndRoundTrip() {
        val nasty = "div\"si\\ \"},\"role\":\"PLATFORM_SUPERADMIN"
        val user = User(
            UserId("usr-1"), TenantId("ten-1"), Username("tester_x"), EmailAddress("x@bordir.id"),
            Role.OPERATOR, isActive = true, departmentId = nasty, customRoleId = null
        )
        val parsed = Json.parseToJsonElement(authSessionJson(user, "tok\"en", "bordir-uji")).jsonObject
        val u = parsed.getValue("user").jsonObject
        assertEquals(nasty, u.getValue("departmentId").jsonPrimitive.content)
        assertEquals("OPERATOR", u.getValue("role").jsonPrimitive.content)
        assertEquals("tok\"en", parsed.getValue("token").jsonPrimitive.content)
        assertEquals("bordir-uji", parsed.getValue("tenantSlug").jsonPrimitive.content)
    }

    @Test
    fun personaUserId_sameSlugDifferentTenants_shouldDiffer_andFitLimit() {
        val a = personaUserId(TenantId("ten-a"), "budi")
        val b = personaUserId(TenantId("ten-b"), "budi")
        assertNotEquals(a, b)
        val longSlug = "x".repeat(200)
        val la = personaUserId(TenantId("ten-a"), longSlug)
        val lb = personaUserId(TenantId("ten-b"), longSlug)
        assertTrue(la.length <= 64 && lb.length <= 64)
        assertNotEquals(la, lb)
        assertEquals(a, personaUserId(TenantId("ten-a"), "budi"))
    }
}

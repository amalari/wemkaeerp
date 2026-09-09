package com.eventverse.app.infrastructure.api

import com.eventverse.app.domain.auth.*
import com.eventverse.app.domain.tenant.TenantId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class AuthApiClientTest {

    @Test
    fun testSerializeAndDeserializeSession() {
        val user = User(
            id = UserId("usr-owner-001"),
            tenantId = TenantId("ten-demo-001"),
            username = Username("achmad_owner"),
            email = EmailAddress("student.achmad@gmail.com"),
            role = Role.TENANT_ADMIN,
            isActive = true
        )
        val originalSession = UserSession(
            user = user,
            token = AuthToken("jwt-token-xyz-123"),
            tenantSlug = "wemade-demo"
        )

        val serialized = AuthApiClient.serializeSession(originalSession)
        assertNotNull(serialized)

        val deserialized = AuthApiClient.deserializeSession(serialized)
        assertNotNull(deserialized)
        assertEquals("usr-owner-001", deserialized.user.id.value)
        assertEquals("ten-demo-001", deserialized.user.tenantId?.value)
        assertEquals("achmad_owner", deserialized.user.username.value)
        assertEquals("student.achmad@gmail.com", deserialized.user.email.value)
        assertEquals(Role.TENANT_ADMIN, deserialized.user.role)
        assertEquals("jwt-token-xyz-123", deserialized.token.value)
        assertEquals("wemade-demo", deserialized.tenantSlug)
    }

    @Test
    fun testDeserializeEmptyOrInvalidReturnsNull() {
        assertNull(AuthApiClient.deserializeSession(null))
        assertNull(AuthApiClient.deserializeSession(""))
        assertNull(AuthApiClient.deserializeSession("invalid json"))
    }
}

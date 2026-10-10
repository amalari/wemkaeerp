package com.eventverse.app.infrastructure.auth

import com.eventverse.app.domain.auth.EmailAddress
import com.eventverse.app.domain.auth.Role
import com.eventverse.app.domain.auth.User
import com.eventverse.app.domain.auth.UserId
import com.eventverse.app.domain.auth.Username
import com.eventverse.app.domain.tenant.TenantId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Token tenant-bound wajib membawa slug; gagal tegas saat penerbitan, bukan token rusak. */
class JwtTokenServiceTenantSlugTest {
    private val jwt = JwtTokenService()
    private fun user(role: Role, tenant: String?) =
        User(UserId("usr-x"), tenant?.let { TenantId(it) }, Username("tester_x"), EmailAddress("x@bordir.id"), role, isActive = true)

    @Test
    fun generateToken_tenantUserWithoutSlug_shouldFail() {
        assertFailsWith<IllegalArgumentException> { jwt.generateToken(user(Role.TENANT_ADMIN, "ten-bordir"), null) }
        assertFailsWith<IllegalArgumentException> { jwt.generateToken(user(Role.OPERATOR, "ten-bordir"), "  ") }
    }

    @Test
    fun generateToken_tenantUserWithSlug_shouldCarrySlug() {
        val token = jwt.generateToken(user(Role.TENANT_ADMIN, "ten-bordir"), "bordir-uji")
        assertEquals("bordir-uji", jwt.verifyToken(token.value).getOrThrow().getClaim("tenant_slug").asString())
    }

    @Test
    fun generateToken_superadminWithoutSlug_shouldStillWork() {
        jwt.generateToken(user(Role.PLATFORM_SUPERADMIN, null), null)
    }
}

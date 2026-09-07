package com.eventverse.app.domain.auth

import com.eventverse.app.domain.tenant.TenantId
import kotlin.test.*

class AuthTest {

    @Test
    fun valid_email_and_username_should_succeed() {
        val email = EmailAddress("admin@berkahkonveksi.com")
        val username = Username("admin_berkah")
        assertEquals("admin@berkahkonveksi.com", email.value)
        assertEquals("admin_berkah", username.value)
    }

    @Test
    fun invalid_email_format_should_throw_exception() {
        assertFailsWith<IllegalArgumentException> {
            EmailAddress("invalid-email-format")
        }
    }

    @Test
    fun tenant_user_without_tenant_id_should_throw_exception() {
        assertFailsWith<IllegalArgumentException> {
            User(
                id = UserId("usr-1"),
                tenantId = null, // Tenant users MUST have tenantId
                username = Username("operator_1"),
                email = EmailAddress("op1@factory.com"),
                role = Role.OPERATOR
            )
        }
    }

    @Test
    fun platform_superadmin_can_have_null_tenant_id() {
        val superAdmin = User(
            id = UserId("usr-admin"),
            tenantId = null,
            username = Username("superadmin"),
            email = EmailAddress("admin@wemade.id"),
            role = Role.PLATFORM_SUPERADMIN
        )

        assertNull(superAdmin.tenantId)
        assertTrue(superAdmin.hasPermission(Permission.MANAGE_PLATFORM))
        assertTrue(superAdmin.hasPermission(Permission.MANAGE_TENANT))
    }

    @Test
    fun operator_has_only_shopfloor_permission_by_default() {
        val operator = User(
            id = UserId("usr-op1"),
            tenantId = TenantId("ten-101"),
            username = Username("operator_budi"),
            email = EmailAddress("budi@factory.com"),
            role = Role.OPERATOR
        )

        assertTrue(operator.hasPermission(Permission.INPUT_SHOPFLOOR_OUTPUT))
        assertFalse(operator.hasPermission(Permission.APPROVE_SPK))
        assertFalse(operator.hasPermission(Permission.CALCULATE_COSTING))
    }

    @Test
    fun deactivated_user_cannot_have_any_permission() {
        val ppicUser = User(
            id = UserId("usr-ppic"),
            tenantId = TenantId("ten-101"),
            username = Username("supervisor_agus"),
            email = EmailAddress("agus@factory.com"),
            role = Role.PPIC_SUPERVISOR
        ).deactivate()

        assertFalse(ppicUser.isActive)
        assertFalse(ppicUser.hasPermission(Permission.MANAGE_PRODUCTION_SCHEDULE))
    }
}

package com.eventverse.app.domain.auth

import com.eventverse.app.domain.tenant.TenantId
import kotlin.test.*

class UserTest {

    @Test
    fun valid_tenant_user_creation_should_succeed() {
        val user = User(
            id = UserId("usr-1"),
            tenantId = TenantId("ten-1"),
            username = Username("operator_budi"),
            email = EmailAddress("budi@konveksi.id"),
            role = Role.OPERATOR
        )

        assertEquals("usr-1", user.id.value)
        assertEquals("operator_budi", user.username.value)
        assertTrue(user.isActive)
        assertTrue(user.hasPermission(Permission.INPUT_SHOPFLOOR_OUTPUT))
        assertFalse(user.hasPermission(Permission.MANAGE_TENANT))
    }

    @Test
    fun non_superadmin_user_without_tenant_should_throw_exception() {
        assertFailsWith<IllegalArgumentException> {
            User(
                id = UserId("usr-bad"),
                tenantId = null,
                username = Username("sales_andi"),
                email = EmailAddress("andi@konveksi.id"),
                role = Role.SALES
            )
        }
    }

    @Test
    fun platform_superadmin_can_have_null_tenant() {
        val superadmin = User(
            id = UserId("usr-super"),
            tenantId = null,
            username = Username("superadmin"),
            email = EmailAddress("super@wemade.id"),
            role = Role.PLATFORM_SUPERADMIN
        )

        assertNull(superadmin.tenantId)
        assertTrue(superadmin.hasPermission(Permission.MANAGE_PLATFORM))
        assertTrue(superadmin.hasPermission(Permission.IMPERSONATE_TENANT))
    }

    @Test
    fun deactivated_user_has_no_permissions() {
        val user = User(
            id = UserId("usr-1"),
            tenantId = TenantId("ten-1"),
            username = Username("operator_budi"),
            email = EmailAddress("budi@konveksi.id"),
            role = Role.OPERATOR
        ).deactivate()

        assertFalse(user.isActive)
        assertFalse(user.hasPermission(Permission.INPUT_SHOPFLOOR_OUTPUT))
    }

    @Test
    fun adding_custom_permissions_augments_effective_permissions() {
        val operator = User(
            id = UserId("usr-1"),
            tenantId = TenantId("ten-1"),
            username = Username("lead_operator"),
            email = EmailAddress("lead@konveksi.id"),
            role = Role.OPERATOR
        ).addCustomPermission(Permission.PERFORM_QC)

        assertTrue(operator.hasPermission(Permission.INPUT_SHOPFLOOR_OUTPUT))
        assertTrue(operator.hasPermission(Permission.PERFORM_QC))
    }
}

package com.eventverse.app.routes

import com.eventverse.app.domain.auth.Role
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Regresi keamanan (TRD-FLOW-001 3b): operator tanpa jabatan kustom sempat bisa menulis kerangka
 * tahap karena gerbang bersama fail-open saat keputusan RBAC tak bisa dihitung.
 */
class StageFlowEditGuardTest {

    @Test
    fun withoutDecision_onlyRolesCarryingManageTenantMayEdit() {
        assertTrue(mayEditWithoutDecision(null, Role.PLATFORM_SUPERADMIN))
        assertTrue(mayEditWithoutDecision(null, Role.TENANT_ADMIN))
        Role.entries
            .filterNot { it.defaultPermissions.contains(com.eventverse.app.domain.auth.Permission.MANAGE_TENANT) }
            .forEach { assertFalse(mayEditWithoutDecision(null, it), "$it tidak boleh menyunting tanpa keputusan RBAC") }
        assertFalse(mayEditWithoutDecision(null, null))
    }
}

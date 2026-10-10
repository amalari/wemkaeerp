package com.eventverse.app.presentation.rbac

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RbacHeaderChipsTest {
    private fun labels(state: RbacLoadState, users: Int? = 7) =
        RbacHeaderChips.forState(state, totalRoles = 3, totalModules = 15, totalUsers = users).map { it.label }

    @Test
    fun failed_hasNoChips_becauseCountsAreUnknown() {
        assertTrue(labels(RbacLoadState.Failed("x")).isEmpty())
    }

    @Test
    fun loading_hasNoChips() {
        assertTrue(labels(RbacLoadState.Loading).isEmpty())
    }

    @Test
    fun loaded_showsRolesModulesAndUsers() {
        assertEquals(listOf("Jabatan", "Modul SaaS", "Total Karyawan"), labels(RbacLoadState.Loaded))
    }

    @Test
    fun loaded_hidesUsersWhenUnknown() {
        assertEquals(listOf("Jabatan", "Modul SaaS"), labels(RbacLoadState.Loaded, users = null))
    }

    @Test
    fun empty_showsZeroRolesAsRealCount() {
        val chips = RbacHeaderChips.forState(RbacLoadState.Empty, 0, 15, null)
        assertEquals(0, chips.first { it.label == "Jabatan" }.value)
    }
}

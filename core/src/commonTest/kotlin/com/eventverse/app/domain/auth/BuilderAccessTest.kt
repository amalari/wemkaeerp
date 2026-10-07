package com.eventverse.app.domain.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BuilderAccessTest {
    @Test
    fun `peran tak dikenal ditolak, superadmin masuk, dan hanya peran pembawa MANAGE_BUILDER yang lain boleh`() {
        assertFalse((null as Role?).canOpenBuilder(), "fail-closed")
        assertTrue(Role.PLATFORM_SUPERADMIN.canOpenBuilder())
        Role.entries.forEach { r ->
            val expected = r == Role.PLATFORM_SUPERADMIN || r.defaultPermissions.contains(Permission.MANAGE_BUILDER)
            assertEquals(expected, r.canOpenBuilder(), "peran $r")
        }
        assertFalse(Role.SALES.canOpenBuilder())
    }
}

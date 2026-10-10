package com.eventverse.app.presentation.workspace

import com.eventverse.app.domain.rbac.AccessLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class AccessDeniedCardLabelTest {
    @Test
    fun badge_usesUserLanguageNotRawEnumName() {
        assertEquals("Tanpa Akses", ACCESS_DENIED_BADGE)
        assertNotEquals(AccessLevel.NONE.name, ACCESS_DENIED_BADGE)
    }
}

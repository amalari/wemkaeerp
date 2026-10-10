package com.eventverse.app.presentation.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Tidak ada tenant bawaan yang ditebak: slug kosong/null ditolak, slug non-default (`bordir-uji`) lolos apa adanya. */
class TenantBoundTest {

    @Test
    fun explicitTenantSlug_nullOrBlank_isNull() {
        assertNull(explicitTenantSlug(null))
        assertNull(explicitTenantSlug(""))
        assertNull(explicitTenantSlug("   "))
    }

    @Test
    fun explicitTenantSlug_nonDefaultSlug_isKept() {
        assertEquals("bordir-uji", explicitTenantSlug(" bordir-uji "))
    }

    @Test
    fun companyProfile_unknownSlug_isNullNotFirstDemoProfile() {
        assertNull(CompanyTenantProfile.findBySlug("bordir-uji"))
        assertNull(CompanyTenantProfile.findBySlug(null))
        assertEquals("wemade-demo", CompanyTenantProfile.findBySlug("WeMade-Demo")?.slug)
    }
}

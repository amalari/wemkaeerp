package com.eventverse.app.domain.tenant

import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Jam trial (V88): `TRIAL` sudah ada sejak V1 tapi tanpa tenggat — tenant baru dapat PRO penuh
 * selamanya. Test ini mengunci semantik jamnya: expired, perpanjangan yang menumpuk (bukan
 * memotong sisa), dan penolakan di luar TRIAL.
 */
class TenantTrialTest {

    private fun trialTenant(endsAt: Instant?) = Tenant(
        id = TenantId("ten-trial-1"),
        slug = TenantSlug("pabrik-uji"),
        name = TenantName("Pabrik Uji"),
        status = TenantStatus.TRIAL,
        tier = SubscriptionTier.PRO,
        trialEndsAt = endsAt
    )

    private val oct1 = Instant.parse("2026-10-01T00:00:00Z")
    private val oct15 = Instant.parse("2026-10-15T00:00:00Z")
    private val oct20 = Instant.parse("2026-10-20T00:00:00Z")

    @Test
    fun `trial with future deadline is not expired`() {
        assertFalse(trialTenant(oct15).trialExpired(oct1))
    }

    @Test
    fun `trial past deadline is expired`() {
        assertTrue(trialTenant(oct1).trialExpired(oct15))
    }

    @Test
    fun `legacy tenant without deadline is never expired`() {
        assertFalse(trialTenant(null).trialExpired(Instant.parse("2030-01-01T00:00:00Z")))
    }

    @Test
    fun `non trial tenant is never expired`() {
        val active = trialTenant(oct1).activate()
        assertFalse(active.trialExpired(oct15), "ACTIVE di luar jam trial")
    }

    @Test
    fun `extending before deadline stacks onto the deadline`() {
        val extended = trialTenant(oct15).extendTrial(5, now = oct1)
        assertEquals(oct20, extended.trialEndsAt, "basis = tenggat lama, bukan sekarang")
    }

    @Test
    fun `extending after deadline starts from now`() {
        val extended = trialTenant(oct1).extendTrial(7, now = oct15)
        assertEquals(Instant.parse("2026-10-22T00:00:00Z"), extended.trialEndsAt)
    }

    @Test
    fun `extending legacy tenant without deadline starts from now`() {
        val extended = trialTenant(null).extendTrial(14, now = oct1)
        assertEquals(oct15, extended.trialEndsAt)
    }

    @Test
    fun `extending non trial tenant is rejected`() {
        val active = trialTenant(oct15).activate()
        val error = runCatching { active.extendTrial(7, oct1) }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException && error.message!!.contains("TRIAL"))
    }

    @Test
    fun `extending with zero or negative days is rejected`() {
        val error = runCatching { trialTenant(oct15).extendTrial(0, oct1) }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
    }
}

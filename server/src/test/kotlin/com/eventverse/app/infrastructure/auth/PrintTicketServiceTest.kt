package com.eventverse.app.infrastructure.auth

import com.eventverse.app.domain.tenant.TenantId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PrintTicketServiceTest {
    private val service = PrintTicketService(secret = SECRET)
    private val tenant = TenantId("tenant-a")
    private val ticket = service.issue("user-1", tenant, SCOPE)

    @Test
    fun `verify pdf inside scope should return ticket tenant`() {
        assertEquals(tenant, service.verify(ticket, "$SCOPE/spk-card.pdf"))
        assertEquals(tenant, service.verify(ticket, "$SCOPE/labels.pdf"))
    }

    @Test
    fun `verify other work order should be rejected`() {
        assertNull(service.verify(ticket, "/api/tenant/traceability/work-orders/SAMPLING/smp-other/spk-card.pdf"))
        assertNull(service.verify(ticket, "${SCOPE}x/spk-card.pdf"))
    }

    @Test
    fun `verify non pdf route should be rejected`() {
        assertNull(service.verify(ticket, "$SCOPE/allocation"))
    }

    @Test
    fun `verify expired ticket should be rejected`() {
        val expired = PrintTicketService(secret = SECRET, validityMillis = -1_000L).issue("user-1", tenant, SCOPE)
        assertNull(service.verify(expired, "$SCOPE/spk-card.pdf"))
    }

    @Test
    fun `ticket used as session token should be rejected`() {
        assertTrue(JwtTokenService(secret = SECRET).verifyToken(ticket).isFailure)
    }

    private companion object {
        const val SECRET = "test-secret-test-secret-test-secret-32"
        const val SCOPE = "/api/tenant/traceability/work-orders/SAMPLING/smp-seed-0050-s"
    }
}

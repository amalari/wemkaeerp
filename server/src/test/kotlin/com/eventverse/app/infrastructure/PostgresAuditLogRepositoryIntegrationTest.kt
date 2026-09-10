package com.eventverse.app.infrastructure

import com.eventverse.app.domain.audit.AuditAction
import com.eventverse.app.domain.audit.AuditLogEntry
import com.eventverse.app.domain.auth.Role
import com.eventverse.app.domain.tenant.*
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Integration coverage for the audit trail against the local docker-compose PostgreSQL —
 * including the real `TIMESTAMP WITH TIME ZONE` column and RLS-scoped tenant isolation,
 * which an in-memory fake cannot exercise.
 */
class PostgresAuditLogRepositoryIntegrationTest {

    private lateinit var tenantRepo: PostgresTenantRepository
    private lateinit var auditRepo: PostgresAuditLogRepository

    @BeforeTest
    fun setup() {
        DatabaseFactory.init()
        tenantRepo = PostgresTenantRepository()
        auditRepo = PostgresAuditLogRepository()
    }

    private fun createTenant(): Tenant {
        val suffix = kotlin.math.abs(System.nanoTime() % 1_000_000).toString()
        val tenant = Tenant(
            id = TenantId("ten-audit-$suffix"),
            slug = TenantSlug("audit-$suffix"),
            name = TenantName("PT Uji Audit $suffix"),
            status = TenantStatus.ACTIVE,
            tier = SubscriptionTier.ENTERPRISE
        )
        runBlocking { tenantRepo.save(tenant).getOrThrow() }
        return tenant
    }

    private fun entryFor(tenant: Tenant, action: AuditAction, summary: String) = AuditLogEntry(
        id = "audit-${tenant.id.value}-${Clock.System.now().toEpochMilliseconds()}-${(0..9999).random()}",
        actorUserId = "usr-superadmin-test",
        actorRole = Role.PLATFORM_SUPERADMIN,
        targetTenantId = tenant.id,
        action = action,
        summary = summary,
        occurredAt = Clock.System.now()
    )

    @Test
    fun record_thenFindByTenant_shouldRoundTripEveryField() = runBlocking<Unit> {
        val tenant = createTenant()
        val entry = entryFor(tenant, AuditAction.TENANT_TIER_UPDATED, "Mengubah paket dari PRO ke ENTERPRISE")

        auditRepo.record(entry).getOrThrow()
        val fetched = auditRepo.findByTenant(tenant.id).single()

        assertEquals(entry.id, fetched.id)
        assertEquals(entry.actorUserId, fetched.actorUserId)
        assertEquals(entry.actorRole, fetched.actorRole)
        assertEquals(entry.targetTenantId, fetched.targetTenantId)
        assertEquals(entry.action, fetched.action)
        assertEquals(entry.summary, fetched.summary)
        // Real TIMESTAMPTZ storage: compare at second precision rather than requiring
        // bit-for-bit equality with sub-millisecond fractions the column may round.
        assertTrue(
            kotlin.math.abs(entry.occurredAt.epochSeconds - fetched.occurredAt.epochSeconds) <= 1
        )
    }

    @Test
    fun findByTenant_shouldReturnNewestFirst() = runBlocking<Unit> {
        val tenant = createTenant()
        val first = entryFor(tenant, AuditAction.TENANT_TIER_UPDATED, "Perubahan pertama")
        auditRepo.record(first).getOrThrow()
        val second = entryFor(tenant, AuditAction.TENANT_ENTITLEMENT_UPDATED, "Perubahan kedua")
        auditRepo.record(second).getOrThrow()

        val entries = auditRepo.findByTenant(tenant.id)

        assertEquals(listOf(second.id, first.id), entries.map { it.id })
    }

    @Test
    fun findByTenant_shouldRespectTheLimit() = runBlocking<Unit> {
        val tenant = createTenant()
        repeat(5) { i ->
            auditRepo.record(entryFor(tenant, AuditAction.TENANT_TIER_UPDATED, "Perubahan #$i")).getOrThrow()
        }

        val entries = auditRepo.findByTenant(tenant.id, limit = 2)

        assertEquals(2, entries.size)
    }

    @Test
    fun findByTenant_shouldNeverReturnAnotherTenantsEntries() = runBlocking<Unit> {
        val tenantA = createTenant()
        val tenantB = createTenant()
        auditRepo.record(
            entryFor(tenantA, AuditAction.TENANT_TIER_UPDATED, "Rahasia tenant A")
        ).getOrThrow()

        val entriesForB = auditRepo.findByTenant(tenantB.id)

        assertTrue(entriesForB.isEmpty(), "Tenant B tidak boleh melihat entri milik tenant A")
    }

    @Test
    fun findByTenant_forTenantWithNoHistory_shouldReturnEmptyList() = runBlocking<Unit> {
        val tenant = createTenant()

        assertEquals(emptyList(), auditRepo.findByTenant(tenant.id))
    }
}

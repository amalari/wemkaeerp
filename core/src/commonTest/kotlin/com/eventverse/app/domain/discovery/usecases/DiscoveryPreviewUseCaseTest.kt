package com.eventverse.app.domain.discovery.usecases

import com.eventverse.app.domain.auth.UserId
import com.eventverse.app.domain.discovery.DiscoveryDraftId
import com.eventverse.app.domain.discovery.DiscoveryDraftRepository
import com.eventverse.app.domain.discovery.DiscoveryPreviewRegistry
import com.eventverse.app.domain.discovery.DiscoveryRequest
import com.eventverse.app.domain.discovery.DeterministicDiscoveryAgent
import com.eventverse.app.domain.discovery.StoredDiscoveryDraft
import com.eventverse.app.domain.pack.DomainPackCode
import com.eventverse.app.domain.tenant.SubscriptionTier
import com.eventverse.app.domain.tenant.Tenant
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.domain.tenant.TenantName
import com.eventverse.app.domain.tenant.TenantRepository
import com.eventverse.app.domain.tenant.TenantSlug
import com.eventverse.app.domain.tenant.TenantStatus
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Plan §2 A7: sandbox tenant per prospek, idempoten, dan konflik slug ditolak. */
class DiscoveryPreviewUseCaseTest {

    private class FakeTenants : TenantRepository {
        val rows = linkedMapOf<TenantId, Tenant>()
        override suspend fun findById(id: TenantId) = rows[id]
        override suspend fun findBySlug(slug: TenantSlug) = rows.values.firstOrNull { it.slug == slug }
        override suspend fun save(tenant: Tenant): Result<Tenant> { rows[tenant.id] = tenant; return Result.success(tenant) }
        override suspend fun existsBySlug(slug: TenantSlug) = rows.values.any { it.slug == slug }
        override suspend fun findAll() = rows.values.toList()
    }

    private class FakeDrafts : DiscoveryDraftRepository {
        val rows = linkedMapOf<DiscoveryDraftId, StoredDiscoveryDraft>()
        override suspend fun findById(id: DiscoveryDraftId) = rows[id]
        override suspend fun findByOwner(owner: UserId) = rows.values.filter { it.ownerUserId == owner }
        override suspend fun findByTenant(tenantId: com.eventverse.app.domain.tenant.TenantId) = null
        override suspend fun findAll() = rows.values.toList()
        override suspend fun save(stored: StoredDiscoveryDraft): StoredDiscoveryDraft { rows[stored.id] = stored; return stored }
    }

    private val tenants = FakeTenants()
    private val drafts = FakeDrafts()
    private val start = StartDiscoveryPreviewUseCase(drafts, tenants)
    private val end = EndDiscoveryPreviewUseCase(drafts)
    private val owner = UserId("usr-pemilik")
    private val now = Instant.fromEpochMilliseconds(1_700_000_000_000)

    @AfterTest
    fun cleanup() {
        (DiscoveryPreviewRegistry.activePackCodes + DomainPackCode("klinik")).forEach { DiscoveryPreviewRegistry.end(it) }
        drafts.rows.clear(); tenants.rows.clear()
    }

    private suspend fun seed(): DiscoveryDraftId {
        val id = DiscoveryDraftId("draft-1")
        CreateDiscoveryDraftUseCase(
            DeterministicDiscoveryAgent(), drafts
        )(DiscoveryRequest("Kami klinik dengan antrean pasien dan tagihan kasir.", industryHint = "klinik"), owner, id).getOrThrow()
        return id
    }

    @Test
    fun `start membuat tenant sandbox menunjuk pack draf`() = runTest {
        val id = seed()
        val preview = start(id, owner, isPlatformSuperadmin = false, now = now).getOrThrow()

        assertEquals("sandbox-klinik", preview.sandboxSlug)
        val sandbox = tenants.findBySlug(TenantSlug("sandbox-klinik"))!!
        assertEquals(DomainPackCode("klinik"), sandbox.domainPack)
        assertEquals(DomainPackCode("klinik"), preview.packCode)
    }

    @Test
    fun `start ulang setelah sesi berakhir memakai sandbox yang sama`() = runTest {
        val id = seed()
        start(id, owner, isPlatformSuperadmin = false, now = now).getOrThrow()
        end(id, owner, isPlatformSuperadmin = false).getOrThrow()
        assertNull(tenants.findBySlug(TenantSlug("sandbox-klinik"))?.let { DiscoveryPreviewRegistry.activeOf(it.domainPack, now) })

        start(id, owner, isPlatformSuperadmin = false, now = now).getOrThrow()
        assertEquals(1, tenants.rows.size)
    }

    @Test
    fun `slug sandbox yang dipakai pack lain ditolak`() = runTest {
        tenants.save(
            Tenant(TenantId("ten-lain"), TenantSlug("sandbox-klinik"), TenantName("Pemilik lain"),
                TenantStatus.ACTIVE, SubscriptionTier.PRO, domainPack = DomainPackCode("bisnis_kustom"))
        ).getOrThrow()
        val id = seed()
        val ex = assertFailsWith<IllegalStateException> { start(id, owner, isPlatformSuperadmin = false, now = now).getOrThrow() }
        assertTrue(ex.message!!.contains("sudah dipakai"))
    }

    @Test
    fun `bukan pemilik draf ditolak`() = runTest {
        val id = seed()
        val ex = assertFailsWith<UpdateDiscoveryDraftUseCase.NotOwnerException> {
            start(id, UserId("usr-orang"), isPlatformSuperadmin = false, now = now).getOrThrow()
        }
        assertTrue(ex.message!!.contains("bukan milik"))
    }
}

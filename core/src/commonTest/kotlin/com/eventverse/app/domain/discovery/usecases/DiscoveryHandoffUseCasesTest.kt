package com.eventverse.app.domain.discovery.usecases

import com.eventverse.app.domain.auth.UserId
import com.eventverse.app.domain.discovery.DiscoveryDraftId
import com.eventverse.app.domain.discovery.DiscoveryDraftRepository
import com.eventverse.app.domain.discovery.DiscoveryRequest
import com.eventverse.app.domain.discovery.DeterministicDiscoveryAgent
import com.eventverse.app.domain.discovery.StoredDiscoveryDraft
import com.eventverse.app.domain.pack.DomainPackCode
import com.eventverse.app.domain.pack.DomainPackRegistry
import com.eventverse.app.domain.pack.DomainPackRepository
import com.eventverse.app.domain.pack.DomainPackStatus
import com.eventverse.app.domain.pack.StoredDomainPack
import com.eventverse.app.domain.prospect.LeadStatus
import com.eventverse.app.domain.prospect.ProspectLead
import com.eventverse.app.domain.prospect.ProspectLeadId
import com.eventverse.app.domain.prospect.ProspectLeadRepository
import com.eventverse.app.domain.prospect.usecases.SubmitProspectLeadUseCase
import com.eventverse.app.domain.tenant.SubscriptionTier
import com.eventverse.app.domain.tenant.Tenant
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.domain.tenant.TenantName
import com.eventverse.app.domain.tenant.TenantRepository
import com.eventverse.app.domain.tenant.TenantSlug
import com.eventverse.app.domain.tenant.TenantStatus
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Plan §3 B2/B3: CTA "Bangun Sistem Ini" → funnel, dan handoff otomatis draf → tenant produksi. */
class DiscoveryHandoffUseCasesTest {

    private class FakeDrafts : DiscoveryDraftRepository {
        val rows = linkedMapOf<DiscoveryDraftId, StoredDiscoveryDraft>()
        override suspend fun findById(id: DiscoveryDraftId) = rows[id]
        override suspend fun findByOwner(owner: UserId) = rows.values.filter { it.ownerUserId == owner }
        override suspend fun findByTenant(tenantId: com.eventverse.app.domain.tenant.TenantId) = null
        override suspend fun findAll() = rows.values.toList()
        override suspend fun save(stored: StoredDiscoveryDraft): StoredDiscoveryDraft { rows[stored.id] = stored; return stored }
    }

    private class FakeTenants : TenantRepository {
        val rows = linkedMapOf<TenantId, Tenant>()
        override suspend fun findById(id: TenantId) = rows[id]
        override suspend fun findBySlug(slug: TenantSlug) = rows.values.firstOrNull { it.slug == slug }
        override suspend fun save(tenant: Tenant): Result<Tenant> { rows[tenant.id] = tenant; return Result.success(tenant) }
        override suspend fun existsBySlug(slug: TenantSlug) = rows.values.any { it.slug == slug }
        override suspend fun findAll() = rows.values.toList()
    }

    private class FakePacks : DomainPackRepository {
        val rows = mutableListOf<StoredDomainPack>()
        override suspend fun findEffective(code: DomainPackCode) =
            rows.filter { it.pack.code == code }.filter { it.status == DomainPackStatus.LOCKED }.maxByOrNull { it.version }
        override suspend fun findAllEffective() = rows.groupBy { it.pack.code }.map { (_, v) ->
            v.filter { it.status == DomainPackStatus.LOCKED }.maxByOrNull { it.version }
                ?: v.maxBy { it.version }
        }
        override suspend fun findLatest(code: DomainPackCode) =
            rows.filter { it.pack.code == code }.maxByOrNull { it.version }
        override suspend fun findVersion(code: DomainPackCode, version: Int) =
            rows.find { it.pack.code == code && it.version == version }
        override suspend fun save(stored: StoredDomainPack): StoredDomainPack {
            rows.removeAll { it.pack.code == stored.pack.code && it.version == stored.version }
            rows.add(stored); return stored
        }
    }

    private class FakeLeads : ProspectLeadRepository {
        val rows = linkedMapOf<ProspectLeadId, ProspectLead>()
        override suspend fun findById(id: ProspectLeadId) = rows[id]
        override suspend fun findByStatus(status: LeadStatus, limit: Int) =
            rows.values.filter { it.status == status }.take(limit)
        override suspend fun findRecent(limit: Int) = rows.values.toList().takeLast(limit).reversed()
        override suspend fun save(lead: ProspectLead) { rows[lead.id] = lead }
    }

    private val drafts = FakeDrafts()
    private val tenants = FakeTenants()
    private val packs = FakePacks()
    private val leads = FakeLeads()
    private val owner = UserId("usr-pemilik")
    private val submit = SubmitDiscoveryDraftUseCase(drafts, leads, SubmitProspectLeadUseCase(leads))
    private val handoff = HandoffDiscoveryDraftUseCase(drafts, tenants, packs) { false }

    @AfterTest
    fun cleanup() {
        (drafts.rows.values.map { it.draft.pack.code }).forEach { if (!DomainPackRegistry.isShipped(it)) DomainPackRegistry.unregister(it) }
        drafts.rows.clear(); tenants.rows.clear(); packs.rows.clear(); leads.rows.clear()
    }

    private suspend fun lockedKlinik(id: DiscoveryDraftId = DiscoveryDraftId("draft-1")): DiscoveryDraftId {
        CreateDiscoveryDraftUseCase(
            DeterministicDiscoveryAgent(), drafts
        )(DiscoveryRequest("Kami klinik dengan antrean pasien dan tagihan kasir.", industryHint = "klinik"), owner, id).getOrThrow()
        LockDiscoveryDraftUseCase(drafts)(id, owner, isPlatformSuperadmin = false).getOrThrow()
        return id
    }

    @Test
    fun `submit menolak draf yang belum dikunci`() = runTest {
        val id = DiscoveryDraftId("draft-1")
        CreateDiscoveryDraftUseCase(
            DeterministicDiscoveryAgent(), drafts
        )(DiscoveryRequest("Kami klinik gigi dengan tagihan kasir.", industryHint = "klinik"), owner, id).getOrThrow()

        val ex = assertFailsWith<IllegalArgumentException> {
            submit(id, owner, isPlatformSuperadmin = false, companyName = "Klinik Sehat").getOrThrow()
        }
        assertTrue(ex.message!!.contains("kunci"))
        assertTrue(leads.rows.isEmpty())
    }

    @Test
    fun `submit setelah kunci membuat lead dan menautkan id pada draf`() = runTest {
        val id = lockedKlinik()
        val result = submit(id, owner, isPlatformSuperadmin = false, companyName = "Klinik Sehat").getOrThrow()

        val lead = leads.findById(result.leadId)!!
        assertEquals("Klinik Sehat", lead.companyName)
        assertEquals(LeadStatus.TRANSLATED, lead.status)
        // Dokumen LOCKED tidak berubah; hanya metadata penautan yang ditulis.
        assertEquals(result.leadId.value, drafts.findById(id)!!.prospectLeadId)
    }

    @Test
    fun `handoff hanya boleh superadmin platform`() = runTest {
        val id = lockedKlinik()
        assertFailsWith<HandoffDiscoveryDraftUseCase.ForbiddenException> {
            handoff(id, isPlatformSuperadmin = false, tenantSlug = "klinik-sehat", companyName = "Klinik Sehat").getOrThrow()
        }
        assertTrue(tenants.rows.isEmpty())
    }

    @Test
    fun `handoff klinik membuat tenant, mengunci pack, dan menyalin blueprint`() = runTest {
        val id = lockedKlinik()
        val draft = drafts.findById(id)!!.draft
        val result = handoff(id, isPlatformSuperadmin = true, tenantSlug = "Klinik-Sehat", companyName = "Klinik Sehat").getOrThrow()

        assertEquals("klinik-sehat", result.tenant.slug.value)
        assertEquals(draft.pack.code, result.tenant.domainPack)
        assertEquals(1, result.packVersion)
        assertEquals(draft.blueprint, result.tenant.businessPreset)
        assertEquals(DomainPackStatus.LOCKED, packs.findLatest(draft.pack.code)!!.status)
        // Pack data baru langsung dikenal registry — layar modulnya aktif lewat jalur B7.
        assertEquals(draft.pack, DomainPackRegistry.find(draft.pack.code))
    }

    @Test
    fun `handoff kedua dengan pack identik dipakai ulang, versi berbeda ditolak`() = runTest {
        val first = lockedKlinik()
        val pertama = handoff(first, isPlatformSuperadmin = true, tenantSlug = "klinik-a", companyName = "Klinik A").getOrThrow()
        assertFalse(pertama.packBecameShared)
        assertEquals(pertama.tenant.id, packs.findLatest(pertama.packCode)?.ownerTenantId, "pack baru dimiliki tenant pertama")

        // Prospek kedua, draf identik → reuse versi terkunci.
        val second = DiscoveryDraftId("draft-2")
        CreateDiscoveryDraftUseCase(
            DeterministicDiscoveryAgent(), drafts
        )(DiscoveryRequest("Kami klinik dengan antrean pasien dan tagihan kasir.", industryHint = "klinik"), owner, second).getOrThrow()
        LockDiscoveryDraftUseCase(drafts)(second, owner, isPlatformSuperadmin = false).getOrThrow()
        val reuse = handoff(second, isPlatformSuperadmin = true, tenantSlug = "klinik-b", companyName = "Klinik B").getOrThrow()
        assertEquals(1, reuse.packVersion)
        // TRD-PLAT-004 P1: reuse identik oleh tenant lain sah, tetapi pack dilepas menjadi bersama — eksplisit, bukan lolos diam-diam.
        assertTrue(reuse.packBecameShared)
        assertNull(packs.findLatest(reuse.packCode)?.ownerTenantId)
        assertEquals(reuse.packCode, tenants.findBySlug(TenantSlug("klinik-b"))?.domainPack)

        // Prospek ketiga dengan pack yang menyimpang dari versi terkunci → 409, butuh review manual (plan §7).
        val stored = drafts.findById(second)!!
        drafts.save(stored.copy(draft = stored.draft.copy(pack = stored.draft.pack.copy(displayName = "Klinik Gigi V2"))))
        val ex = assertFailsWith<IllegalStateException> {
            handoff(second, isPlatformSuperadmin = true, tenantSlug = "klinik-c", companyName = "Klinik C").getOrThrow()
        }
        assertTrue(ex.message!!.contains("review manual"))
        assertNull(tenants.findBySlug(TenantSlug("klinik-c")))
    }

    // ── TRD-PLAT-005 (A2/A3): reuse pack identik dan kepemilikan ────────────────────────────────────────

    @Test
    fun `handoff ulang oleh pemilik sendiri tidak melepas pack menjadi bersama`() = runTest {
        val a = handoff(lockedKlinik(), isPlatformSuperadmin = true, tenantSlug = "klinik-a", companyName = "Klinik A").getOrThrow()
        val ulang = handoff(lockedKlinik(DiscoveryDraftId("draft-2")), isPlatformSuperadmin = true, tenantSlug = "klinik-a", companyName = "Klinik A").getOrThrow()

        assertFalse(ulang.packBecameShared)
        assertEquals(a.tenant.id, packs.findLatest(a.packCode)?.ownerTenantId, "pemilik tidak berubah")
    }

    @Test
    fun `setelah dilepas, tenant ketiga memakai pack bersama tanpa pelepasan baru`() = runTest {
        handoff(lockedKlinik(), isPlatformSuperadmin = true, tenantSlug = "klinik-a", companyName = "Klinik A").getOrThrow()
        val b = handoff(lockedKlinik(DiscoveryDraftId("draft-2")), isPlatformSuperadmin = true, tenantSlug = "klinik-b", companyName = "Klinik B").getOrThrow()
        val c = handoff(lockedKlinik(DiscoveryDraftId("draft-3")), isPlatformSuperadmin = true, tenantSlug = "klinik-c", companyName = "Klinik C").getOrThrow()

        assertTrue(b.packBecameShared)
        assertFalse(c.packBecameShared, "sudah bersama, tidak ada yang dilepas lagi")
        assertEquals(b.packCode, c.tenant.domainPack)
        assertNull(packs.findLatest(c.packCode)?.ownerTenantId)
    }

    @Test
    fun `handoff yang gagal saat assign mengembalikan kepemilikan pack`() = runTest {
        val a = handoff(lockedKlinik(), isPlatformSuperadmin = true, tenantSlug = "klinik-a", companyName = "Klinik A").getOrThrow()
        val punyaData = HandoffDiscoveryDraftUseCase(drafts, tenants, packs) { true }

        val result = punyaData(lockedKlinik(DiscoveryDraftId("draft-2")), isPlatformSuperadmin = true, tenantSlug = "klinik-b", companyName = "Klinik B")

        assertTrue(result.isFailure)
        assertEquals(a.tenant.id, packs.findLatest(a.packCode)?.ownerTenantId, "pack A tidak boleh tertinggal bersama akibat handoff yang gagal")
    }

    @Test
    fun `handoff garment tidak menyimpan ulang pack bawaan`() = runTest {
        val id = DiscoveryDraftId("draft-g")
        CreateDiscoveryDraftUseCase(
            DeterministicDiscoveryAgent(), drafts
        )(DiscoveryRequest("Konveksi brand sendiri untuk distro retail."), owner, id).getOrThrow()
        LockDiscoveryDraftUseCase(drafts)(id, owner, isPlatformSuperadmin = false).getOrThrow()

        val result = handoff(id, isPlatformSuperadmin = true, tenantSlug = "distro-jaya", companyName = "Distro Jaya").getOrThrow()

        assertNull(result.packVersion) // Pack bawaan dikirim sebagai kode; tidak ada baris versi baru.
        assertTrue(packs.rows.isEmpty())
        assertEquals(result.tenant.businessPreset.code, drafts.findById(id)!!.draft.blueprint.code)
    }

    private fun runTest(block: suspend () -> Unit) = kotlinx.coroutines.test.runTest { block() }
}

// PART2

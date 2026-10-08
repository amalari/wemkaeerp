package com.eventverse.app.domain.pack

import com.eventverse.app.domain.pack.usecases.AssignTenantDomainPackUseCase
import com.eventverse.app.domain.pack.usecases.TenantOperationalDataProbe
import com.eventverse.app.domain.tenant.SubscriptionTier
import com.eventverse.app.domain.tenant.Tenant
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.domain.tenant.TenantName
import com.eventverse.app.domain.tenant.TenantRepository
import com.eventverse.app.domain.tenant.TenantSlug
import com.eventverse.app.domain.tenant.TenantStatus
import com.eventverse.app.shared.pack.DomainPackCodec
import com.eventverse.app.shared.pack.DomainPackCodecTest
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** TRD-PLAT-004 P1: `owner_tenant_id` ditegakkan saat tenant dipasangi pack — dua arah, plus pack bersama dan bawaan. */
class AssignTenantDomainPackOwnerTest {

    private val klinik = DomainPackCodec.decode(DomainPackCodecTest.KLINIK_JSON)
    private val pemilik = TenantId("ten-pemilik")
    private val lain = TenantId("ten-lain")

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
        override suspend fun findEffective(code: DomainPackCode) = rows.filter { it.pack.code == code && it.status == DomainPackStatus.LOCKED }.maxByOrNull { it.version }
        override suspend fun findAllEffective() = rows.toList()
        override suspend fun findLatest(code: DomainPackCode) = rows.filter { it.pack.code == code }.maxByOrNull { it.version }
        override suspend fun findVersion(code: DomainPackCode, version: Int) = rows.find { it.pack.code == code && it.version == version }
        override suspend fun save(stored: StoredDomainPack): StoredDomainPack { rows += stored; return stored }
    }

    private val tenants = FakeTenants()
    private val packs = FakePacks()
    private val noData = TenantOperationalDataProbe { false }
    private val assign = AssignTenantDomainPackUseCase(tenants, noData, packs)

    @BeforeTest
    fun setUp() {
        DomainPackRegistry.register(klinik)
        listOf(pemilik, lain).forEach {
            tenants.rows[it] = Tenant(it, TenantSlug(it.value.removePrefix("ten-")), TenantName(it.value), TenantStatus.ACTIVE, SubscriptionTier.PRO)
        }
    }

    @AfterTest
    fun cleanup() = DomainPackRegistry.unregister(klinik.code)

    @Test
    fun `tenant pemilik boleh dipasangi pack miliknya`() = runTest {
        packs.save(StoredDomainPack(klinik, 1, DomainPackStatus.LOCKED, ownerTenantId = pemilik))
        assertEquals(klinik.code, assign(pemilik, klinik.code).getOrThrow().domainPack)
    }

    @Test
    fun `tenant lain ditolak, tenantnya tidak berubah, dan pesan tidak menyebut pemilik`() = runTest {
        packs.save(StoredDomainPack(klinik, 1, DomainPackStatus.LOCKED, ownerTenantId = pemilik))
        val result = assign(lain, klinik.code)
        assertTrue(result.isFailure)
        assertFalse(result.exceptionOrNull()!!.message!!.contains(pemilik.value), "pesan tidak boleh membocorkan pemilik")
        assertEquals(GarmentDomainPackCodeForTest, tenants.rows.getValue(lain).domainPack)
    }

    @Test
    fun `pack bersama tanpa pemilik boleh dipasang pada tenant mana pun`() = runTest {
        packs.save(StoredDomainPack(klinik, 1, DomainPackStatus.LOCKED, ownerTenantId = null))
        assertTrue(assign(lain, klinik.code).isSuccess)
        assertTrue(assign(pemilik, klinik.code).isSuccess)
    }

    @Test
    fun `pemilik dibaca dari versi tertinggi, jadi revisi draf tidak melepaskan kepemilikan`() = runTest {
        packs.save(StoredDomainPack(klinik, 1, DomainPackStatus.LOCKED, ownerTenantId = pemilik))
        packs.save(StoredDomainPack(klinik, 2, DomainPackStatus.DRAFT, ownerTenantId = pemilik))
        assertTrue(assign(lain, klinik.code).isFailure)
    }

    @Test
    fun `pasang ulang pack yang sama oleh bukan pemilik gagal keras, tidak lolos lewat jalan pintas sudah-sama`() = runTest {
        // Data lama yang melanggar: tenant lain sudah berada di pack itu sebelum aturan ini ada.
        tenants.rows[lain] = tenants.rows.getValue(lain).copy(domainPack = klinik.code)
        packs.save(StoredDomainPack(klinik, 1, DomainPackStatus.LOCKED, ownerTenantId = pemilik))
        assertTrue(assign(lain, klinik.code).isFailure)
    }

    @Test
    fun `pack bawaan garment tidak terpengaruh`() = runTest {
        assertTrue(assign(lain, GarmentDomainPack.CODE).isSuccess)
    }

    private companion object {
        val GarmentDomainPackCodeForTest = GarmentDomainPack.CODE
    }
}

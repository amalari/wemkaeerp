package com.eventverse.app.domain.pack

import com.eventverse.app.domain.pack.usecases.ResolveDomainPackUseCase
import com.eventverse.app.domain.pack.usecases.LockDomainPackUseCase
import com.eventverse.app.domain.pack.usecases.SaveDomainPackDraftUseCase
import com.eventverse.app.shared.pack.DomainPackCodec
import com.eventverse.app.shared.pack.DomainPackCodecTest
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** B7 FR-4/5: draf diganti di tempat, versi terkunci membeku, revisi = versi baru; resolver memuat pack data sekali. */
class DomainPackVersioningTest {

    private val repo = FakeRepo()
    private val klinik = DomainPackCodec.decode(DomainPackCodecTest.KLINIK_JSON)

    @AfterTest
    fun cleanup() = DomainPackRegistry.unregister(klinik.code)

    @Test
    fun draftIsReplaced_lockFreezes_andRevisionBecomesNewVersion_whileTenantsKeepLockedVersion() = runTest {
        SaveDomainPackDraftUseCase(repo)(klinik, null).getOrThrow()
        val renamed = klinik.copy(displayName = "Klinik v1 final")
        assertEquals(1, SaveDomainPackDraftUseCase(repo)(renamed, null).getOrThrow().version, "draf yang sama diganti di tempat")

        assertEquals(DomainPackStatus.LOCKED, LockDomainPackUseCase(repo)(klinik.code).getOrThrow().status)

        val revision = klinik.copy(displayName = "Klinik v2 draf")
        assertEquals(2, SaveDomainPackDraftUseCase(repo)(revision, null).getOrThrow().version)
        // Tenant tetap di versi terkunci: draf v2 tidak mengubah registry.
        assertEquals("Klinik v1 final", DomainPackRegistry.find(klinik.code)?.displayName)
        assertEquals(1, repo.findEffective(klinik.code)?.version)
    }

    @Test
    fun hijackingPack_isRejectedBeforeSaving() = runTest {
        val hijack = klinik.copy(code = DomainPackCode("garment"))
        assertTrue(SaveDomainPackDraftUseCase(repo)(hijack, null).isFailure)
        assertEquals(null, repo.findLatest(DomainPackCode("garment")))
    }

    @Test
    fun resolver_loadsDataPackOnce_andReturnsNullForInvalidOrUnknown() = runTest {
        repo.save(StoredDomainPack(klinik, 1, DomainPackStatus.LOCKED, null))
        val unprefixed = DomainPackCodec.decode(DomainPackCodecTest.KLINIK_JSON.replace("klinik_antrean", "antrean").replace("\"code\":\"klinik\"", "\"code\":\"klinik2\""))
        repo.save(StoredDomainPack(unprefixed, 1, DomainPackStatus.LOCKED, null))
        val resolve = ResolveDomainPackUseCase(repo)

        assertEquals(null, DomainPackRegistry.find(klinik.code), "belum dimuat sebelum tenant pertama datang")
        assertEquals(klinik, resolve(klinik.code))
        assertEquals(klinik, DomainPackRegistry.find(klinik.code), "dimuat sekali, lalu dari registry")
        assertEquals(null, resolve(DomainPackCode("klinik2")), "pack tak sah tidak didaftarkan")
        assertEquals(null, resolve(DomainPackCode("hilang")))
        assertEquals(GarmentDomainPack.pack, resolve(GarmentDomainPack.CODE))
    }

    private class FakeRepo : DomainPackRepository {
        private val rows = mutableMapOf<Pair<DomainPackCode, Int>, StoredDomainPack>()
        override suspend fun findEffective(code: DomainPackCode) = effectiveOf(rows.values.filter { it.pack.code == code })
        override suspend fun findAllEffective() = rows.values.groupBy { it.pack.code }.values.mapNotNull(::effectiveOf)
        override suspend fun findLatest(code: DomainPackCode) = rows.values.filter { it.pack.code == code }.maxByOrNull { it.version }
        override suspend fun save(stored: StoredDomainPack): StoredDomainPack { rows[stored.pack.code to stored.version] = stored; return stored }
    }
}

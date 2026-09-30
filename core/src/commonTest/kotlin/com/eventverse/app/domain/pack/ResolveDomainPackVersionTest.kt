package com.eventverse.app.domain.pack

import com.eventverse.app.domain.pack.usecases.LockDomainPackUseCase
import com.eventverse.app.domain.pack.usecases.ResolveDomainPackVersionUseCase
import com.eventverse.app.domain.pack.usecases.SaveDomainPackDraftUseCase
import com.eventverse.app.shared.pack.DomainPackCodec
import com.eventverse.app.shared.pack.DomainPackCodecTest
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pin versi pack per tenant (PLAN-builder-console M0): versi yang diminta wajib persis, terkunci, dan
 * tak dikenal **ditolak** — tanpa fallback ke versi lain (Kontrak 4). Fixture non-default: pack klinik.
 */
class ResolveDomainPackVersionTest {

    private val repo = FakeRepo()
    private val klinik = DomainPackCodec.decode(DomainPackCodecTest.KLINIK_JSON)
    private val resolve = ResolveDomainPackVersionUseCase(repo)

    @AfterTest
    fun cleanup() = DomainPackRegistry.unregister(klinik.code)

    @Test
    fun lockedVersion_resolvesExactly_whileOtherVersionsDoNotSubstitute() = runTest {
        SaveDomainPackDraftUseCase(repo)(klinik, null).getOrThrow()
        LockDomainPackUseCase(repo)(klinik.code).getOrThrow()
        SaveDomainPackDraftUseCase(repo)(klinik.copy(displayName = "Klinik v2 draf"), null).getOrThrow()

        assertEquals("Klinik & Layanan Kesehatan", resolve(klinik.code, 1)?.displayName, "versi 1 terkunci dijawab persis")
        assertFailsWith<IllegalArgumentException>("draf v2 belum terkunci — ditolak keras, bukan dijawab") {
            resolve(klinik.code, 2)
        }
        assertNull(resolve(klinik.code, 99), "versi tak dikenal = null, bukan versi effective")
        assertNull(resolve(DomainPackCode("hilang"), 1))
    }

    @Test
    fun nonPositiveVersion_isRejectedLoudly() = runTest {
        assertFailsWith<IllegalArgumentException> { resolve(klinik.code, 0) }
        assertFailsWith<IllegalArgumentException> { resolve(klinik.code, -1) }
    }

    @Test
    fun draftVersion_cannotBePinned_evenIfItIsTheEffectiveOne() = runTest {
        SaveDomainPackDraftUseCase(repo)(klinik, null).getOrThrow()
        // Pack effective saat ini justru draf v1 — tetap tidak boleh dipin tenant.
        assertTrue(repo.findEffective(klinik.code) != null)
        assertFailsWith<IllegalArgumentException> { resolve(klinik.code, 1) }
    }

    private class FakeRepo : DomainPackRepository {
        private val rows = mutableMapOf<Pair<DomainPackCode, Int>, StoredDomainPack>()
        override suspend fun findEffective(code: DomainPackCode) = effectiveOf(rows.values.filter { it.pack.code == code })
        override suspend fun findAllEffective() = rows.values.groupBy { it.pack.code }.values.mapNotNull(::effectiveOf)
        override suspend fun findLatest(code: DomainPackCode) = rows.values.filter { it.pack.code == code }.maxByOrNull { it.version }
        override suspend fun findVersion(code: DomainPackCode, version: Int) = rows[code to version]
        override suspend fun save(stored: StoredDomainPack): StoredDomainPack { rows[stored.pack.code to stored.version] = stored; return stored }
    }
}

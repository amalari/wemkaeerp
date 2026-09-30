package com.eventverse.app.domain.discovery.usecases

import com.eventverse.app.domain.auth.UserId
import com.eventverse.app.domain.discovery.DiscoveryDraftId
import com.eventverse.app.domain.discovery.DiscoveryDraftStatus
import com.eventverse.app.domain.discovery.DiscoveryRequest
import com.eventverse.app.domain.discovery.DeterministicDiscoveryAgent
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Plan §2 A5: siklus hidup draf — create/update/lock — dengan aturan kepemilikan (T12) dan LOCKED immutable
 * (Kontrak 5). Repository in-memory di sini; jalur HTTP-nya dites di server (`DiscoveryApiTest`).
 */
class DiscoveryDraftUseCaseTest {

    private open class FakeRepository : com.eventverse.app.domain.discovery.DiscoveryDraftRepository {
        val rows = linkedMapOf<DiscoveryDraftId, com.eventverse.app.domain.discovery.StoredDiscoveryDraft>()
        override suspend fun findById(id: DiscoveryDraftId) = rows[id]
        override suspend fun findByOwner(owner: UserId) = rows.values.filter { it.ownerUserId == owner }.reversed()
        override suspend fun findByTenant(tenantId: com.eventverse.app.domain.tenant.TenantId) = null
        override suspend fun findAll() = rows.values.toList()
        override suspend fun save(stored: com.eventverse.app.domain.discovery.StoredDiscoveryDraft): com.eventverse.app.domain.discovery.StoredDiscoveryDraft {
            rows[stored.id] = stored
            return stored
        }
    }

    private val repo = FakeRepository()
    private val create = CreateDiscoveryDraftUseCase(DeterministicDiscoveryAgent(), repo)
    private val update = UpdateDiscoveryDraftUseCase(repo)
    private val lock = LockDiscoveryDraftUseCase(repo)
    private val owner = UserId("usr-pemilik")
    private val orangLain = UserId("usr-orang-lain")

    @AfterTest
    fun cleanup() = repo.rows.clear()

    private suspend fun seed(): DiscoveryDraftId {
        val id = DiscoveryDraftId("draft-1")
        create(DiscoveryRequest("Kami klinik dengan antrean pasien dan tagihan kasir.", industryHint = "klinik"), owner, id).getOrThrow()
        return id
    }

    @Test
    fun `create menyimpan draf milik pemilik sebagai DRAFT`() = runTest {
        val id = seed()
        val stored = repo.findById(id)!!
        assertEquals(owner, stored.ownerUserId)
        assertEquals(DiscoveryDraftStatus.DRAFT, stored.status)
    }

    @Test
    fun `update oleh pemilik mengganti dokumen, oleh orang lain ditolak`() = runTest {
        val id = seed()
        val revised = repo.findById(id)!!.draft.copy(screens = emptyList())

        val failure = assertFailsWith<UpdateDiscoveryDraftUseCase.NotOwnerException> {
            update(id, orangLain, isPlatformSuperadmin = false, draft = revised).getOrThrow()
        }
        assertTrue(failure.message!!.contains("bukan milik"))
        update(id, owner, isPlatformSuperadmin = false, draft = revised).getOrThrow()
    }

    @Test
    fun `update ke LOCKED ditolak`() = runTest {
        val id = seed()
        lock(id, owner, isPlatformSuperadmin = false).getOrThrow()
        assertEquals(DiscoveryDraftStatus.LOCKED, repo.findById(id)!!.status)

        val revised = repo.findById(id)!!.draft
        assertFailsWith<UpdateDiscoveryDraftUseCase.LockedException> {
            update(id, owner, isPlatformSuperadmin = false, draft = revised).getOrThrow()
        }
        assertFailsWith<LockDiscoveryDraftUseCase.LockedException> {
            lock(id, owner, isPlatformSuperadmin = false).getOrThrow()
        }
    }

    @Test
    fun `superadmin boleh merevisi draf pemilik`() = runTest {
        val id = seed()
        val revised = repo.findById(id)!!.draft
        update(id, orangLain, isPlatformSuperadmin = true, draft = revised).getOrThrow()
        lock(id, orangLain, isPlatformSuperadmin = true).getOrThrow()
        assertEquals(DiscoveryDraftStatus.LOCKED, repo.findById(id)!!.status)
    }

    private fun runTest(block: suspend () -> Unit) = kotlinx.coroutines.test.runTest { block() }
}

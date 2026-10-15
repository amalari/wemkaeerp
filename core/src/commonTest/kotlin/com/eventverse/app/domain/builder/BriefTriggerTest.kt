package com.eventverse.app.domain.builder

import com.eventverse.app.domain.auth.UserId
import com.eventverse.app.domain.discovery.DiscoveryDraft
import com.eventverse.app.domain.discovery.DiscoveryDraftId
import com.eventverse.app.domain.discovery.DiscoveryDraftRepository
import com.eventverse.app.domain.discovery.StoredDiscoveryDraft
import com.eventverse.app.domain.discovery.interview.InterviewFixtures
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.shared.discovery.DiscoveryDraftCodec
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pemicu brief (TRD-BLDR-001 FR-1): dipicu SETELAH patch diterapkan, bukan setelah rencana wawancara dibuat.
 * Regresi nyata (cek visual 2026-10-16): wawancara hasil perencana baru ada di patch usulan; pemicu lama berjalan sebelum
 * Terapkan, membaca draf tersimpan tanpa wawancara, dan semua modul `skipped`.
 */
class BriefTriggerTest {
    private val tenant = TenantId("ten-uji")
    private val owner = UserId("usr-uji")
    private val klinikDraft = InterviewFixtures.draftOf(InterviewFixtures.klinikPack, null)

    private class Drafts(draft: DiscoveryDraft, tenantId: TenantId) : DiscoveryDraftRepository {
        var row = StoredDiscoveryDraft(DiscoveryDraftId("d1"), UserId("usr-uji"), draft, tenantId = tenantId)
        override suspend fun findById(id: DiscoveryDraftId) = row.takeIf { it.id == id }
        override suspend fun findByOwner(ownerUserId: UserId) = listOf(row).filter { it.ownerUserId == ownerUserId }
        override suspend fun findByTenant(tenantId: TenantId) = row.takeIf { it.tenantId == tenantId }
        override suspend fun findAll() = listOf(row)
        override suspend fun save(stored: StoredDiscoveryDraft) = stored.also { row = it }
    }

    /** Merekam keadaan draf TERSIMPAN pada saat dipanggil — bukti urutan: wawancara sudah ada atau belum. */
    private class RecordingService(private val drafts: Drafts, private val fail: Boolean = false) : ModuleBriefService {
        val calls = mutableListOf<TenantId>()
        val interviewAtCall = mutableListOf<Boolean>()
        override suspend fun generate(tenantId: TenantId, moduleId: String?): Result<List<ModuleBriefOutcome>> {
            calls += tenantId
            interviewAtCall += (drafts.findByTenant(tenantId)?.draft?.interview != null)
            if (fail) error("brief mati")
            return Result.success(emptyList())
        }
    }

    /** Pesan AGENT berisi patch usulan = draf klinik BERWAWANCARA; draf tersimpan awal belum punya wawancara. */
    private suspend fun proposePatch(chats: InMemoryBuilderChatRepository): ChatMessageId {
        val patched = klinikDraft.copy(interview = InterviewFixtures.klinikSession)
        val conv = chats.conversationFor(tenant)
        return chats.append(
            ChatMessage(
                ChatMessageId("msg-patch"), conv.id, tenant, ChatRole.AGENT, "Saya mengusulkan revisi draf.",
                proposedDraftJson = DiscoveryDraftCodec.encodeToString(patched)
            )
        ).id
    }

    @Test
    fun `terapkan patch should memicu brief SETELAH wawancara tersimpan di draf`() = runTest {
        val drafts = Drafts(klinikDraft, tenant)
        assertNull(drafts.row.draft.interview, "prasyarat: draf tersimpan awal belum punya wawancara")
        val service = RecordingService(drafts)
        val chats = InMemoryBuilderChatRepository()
        val id = proposePatch(chats)

        ApplyDraftPatchUseCase(chats, drafts, briefService = service)(tenant, id, owner).getOrThrow()

        assertEquals(listOf(tenant), service.calls)
        assertEquals(listOf(true), service.interviewAtCall, "saat brief dipicu, wawancara harus sudah tersimpan")
        assertNotNull(drafts.row.draft.interview)
    }

    @Test
    fun `service brief melempar galat should tidak menggagalkan penerapan patch`() = runTest {
        val drafts = Drafts(klinikDraft, tenant)
        val service = RecordingService(drafts, fail = true)
        val chats = InMemoryBuilderChatRepository()
        val id = proposePatch(chats)

        val result = ApplyDraftPatchUseCase(chats, drafts, briefService = service)(tenant, id, owner)

        assertTrue(result.isSuccess)
        assertEquals(1, service.calls.size)
        assertNotNull(drafts.row.draft.interview, "patch tetap tersimpan")
    }

    @Test
    fun `patch ditolak should tidak memicu brief`() = runTest {
        val drafts = Drafts(klinikDraft, tenant)
        val service = RecordingService(drafts)
        val chats = InMemoryBuilderChatRepository()
        val conv = chats.conversationFor(tenant)
        val noPatch = chats.append(ChatMessage(ChatMessageId("m"), conv.id, tenant, ChatRole.AGENT, "tanpa patch")).id

        val result = ApplyDraftPatchUseCase(chats, drafts, briefService = service)(tenant, noPatch, owner)

        assertTrue(result.isFailure)
        assertTrue(service.calls.isEmpty())
    }

    @Test
    fun `dengan briefScope should dibuat di latar dan tetap dipanggil`() = runTest {
        val drafts = Drafts(klinikDraft, tenant)
        val service = RecordingService(drafts)
        val chats = InMemoryBuilderChatRepository()
        val id = proposePatch(chats)

        ApplyDraftPatchUseCase(chats, drafts, briefService = service, briefScope = this)(tenant, id, owner).getOrThrow()
        advanceUntilIdle()

        assertEquals(listOf(true), service.interviewAtCall)
    }

    @Test
    fun `tanpa briefService should berperilaku seperti sebelumnya`() = runTest {
        val drafts = Drafts(klinikDraft, tenant)
        val chats = InMemoryBuilderChatRepository()
        val id = proposePatch(chats)
        assertTrue(ApplyDraftPatchUseCase(chats, drafts)(tenant, id, owner).isSuccess)
    }
}

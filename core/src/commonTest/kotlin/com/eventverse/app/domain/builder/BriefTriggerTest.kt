package com.eventverse.app.domain.builder

import com.eventverse.app.domain.auth.UserId
import com.eventverse.app.domain.discovery.DiscoveryDraft
import com.eventverse.app.domain.discovery.DiscoveryDraftId
import com.eventverse.app.domain.discovery.DiscoveryDraftRepository
import com.eventverse.app.domain.discovery.StoredDiscoveryDraft
import com.eventverse.app.domain.discovery.interview.InterviewFixtures
import com.eventverse.app.domain.discovery.interview.InterviewPlanner
import com.eventverse.app.domain.discovery.interview.InterviewSession
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Pemicu brief di [SendBuilderMessageUseCase] (TRD-BLDR-001 FR-1): hanya setelah rencana terbentuk, tak pernah menggagalkan chat. */
class BriefTriggerTest {
    private val tenant = TenantId("ten-uji")
    private val baseDraft = InterviewFixtures.draftOf(InterviewFixtures.klinikPack, null)

    private class Drafts(draft: DiscoveryDraft) : DiscoveryDraftRepository {
        private var row = StoredDiscoveryDraft(DiscoveryDraftId("d1"), UserId("usr-uji"), draft, tenantId = TenantId("ten-uji"))
        override suspend fun findById(id: DiscoveryDraftId) = row.takeIf { it.id == id }
        override suspend fun findByOwner(ownerUserId: UserId) = listOf(row).filter { it.ownerUserId == ownerUserId }
        override suspend fun findByTenant(tenantId: TenantId) = row.takeIf { it.tenantId == tenantId }
        override suspend fun findAll() = listOf(row)
        override suspend fun save(stored: StoredDiscoveryDraft) = stored.also { row = it }
    }

    private object Agent : BuilderAgent {
        override val agentRef = "uji"
        override suspend fun proposePatch(currentDraft: DiscoveryDraft?, history: List<ChatMessage>, userMessage: String) =
            Result.success(BuilderAgentReply("draf"))
    }

    private class RecordingService(private val fail: Boolean = false) : ModuleBriefService {
        val calls = mutableListOf<Pair<TenantId, String?>>()
        override suspend fun generate(tenantId: TenantId, moduleId: String?): Result<List<ModuleBriefOutcome>> {
            calls += tenantId to moduleId
            if (fail) error("brief mati")
            return Result.success(emptyList())
        }
    }

    private val planned = InterviewPlanner { _, _, narrative -> InterviewFixtures.klinikSession.copy(narrative = narrative) }
    private val failingPlanner = InterviewPlanner { _, _, _ -> error("LLM mati") }

    @Test
    fun `rencana berhasil dibuat should memicu pembuatan brief satu kali untuk semua modul`() = runTest {
        val service = RecordingService()
        val uc = SendBuilderMessageUseCase(InMemoryBuilderChatRepository(), Agent, Drafts(baseDraft), interviewPlanner = planned, briefService = service)
        uc(tenant, "Kami klinik dengan dua poli").getOrThrow()
        assertEquals(listOf<Pair<TenantId, String?>>(tenant to null), service.calls)
    }

    @Test
    fun `perencana gagal should tidak memicu brief dan chat tetap jalan`() = runTest {
        val service = RecordingService()
        val uc = SendBuilderMessageUseCase(InMemoryBuilderChatRepository(), Agent, Drafts(baseDraft), interviewPlanner = failingPlanner, briefService = service)
        uc(tenant, "Kami klinik").getOrThrow()
        assertTrue(service.calls.isEmpty())
    }

    @Test
    fun `service brief melempar galat should tidak menggagalkan balasan chat`() = runTest {
        val service = RecordingService(fail = true)
        val chats = InMemoryBuilderChatRepository()
        val uc = SendBuilderMessageUseCase(chats, Agent, Drafts(baseDraft), interviewPlanner = planned, briefService = service)
        val result = uc(tenant, "Kami klinik")
        assertTrue(result.isSuccess)
        assertEquals(1, service.calls.size)
    }

    @Test
    fun `tanpa briefService should berperilaku seperti sebelumnya`() = runTest {
        val uc = SendBuilderMessageUseCase(InMemoryBuilderChatRepository(), Agent, Drafts(baseDraft), interviewPlanner = planned)
        assertTrue(uc(tenant, "Kami klinik").isSuccess)
    }
}

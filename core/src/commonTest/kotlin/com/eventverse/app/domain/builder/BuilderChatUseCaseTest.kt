package com.eventverse.app.domain.builder

import com.eventverse.app.domain.discovery.DiscoveryDraft
import com.eventverse.app.domain.discovery.DiscoveryDraftId
import com.eventverse.app.domain.discovery.DiscoveryDraftRepository
import com.eventverse.app.domain.discovery.DiscoveryDraftStatus
import com.eventverse.app.domain.discovery.StoredDiscoveryDraft
import com.eventverse.app.domain.pack.GarmentBlueprints
import com.eventverse.app.domain.pack.GarmentDomainPack
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.domain.auth.UserId
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Chat Builder M1 (FR-M1-1/2/3): pesan tersimpan, agent hanya mengusulkan, "Terapkan" = aksi manusia
 * yang melewati validator. Fixture dua tenant — garment (`wemade-demo`) dan non-garment (`bordir-uji`).
 */
class BuilderChatUseCaseTest {

    private val demo = TenantId("ten-wemade-demo")
    private val bordir = TenantId("ten-bordir-uji")
    private val owner = com.eventverse.app.domain.auth.UserId("usr-pemilik")

    private val chats = InMemoryBuilderChatRepository()
    private val drafts = FakeTenantDrafts()
    private val agent = StubAgent()
    private val send = SendBuilderMessageUseCase(chats, agent, drafts)
    private val apply = ApplyDraftPatchUseCase(chats, drafts)

    private fun garmentDraft() = DiscoveryDraft(
        pack = GarmentDomainPack.pack,
        blueprint = GarmentBlueprints.DEFAULT
    )

    @Test
    fun send_appendsUserThenAgent_andPatchStaysPending() = runTest {
        agent.nextReply = BuilderAgentReply("Saya usulkan penyesuaian modul.", proposedDraft = garmentDraft())
        val messages = send(demo, "Tambahkan tahap sablon di alur saya").getOrThrow()

        assertEquals(ChatRole.USER, messages.first().role)
        assertEquals(ChatRole.AGENT, messages.last().role)
        assertTrue(messages.last().hasPendingPatch, "patch usulan menunggu keputusan manusia")
        assertEquals(0, drafts.savedCount, "agent tidak pernah menulis draf")
    }

    @Test
    fun apply_existingDraftKeepsItsOwner_whenAnotherUserApplies() = runTest {
        agent.nextReply = BuilderAgentReply("Usulan pertama.", proposedDraft = garmentDraft())
        send(demo, "Alur awal").getOrThrow()
        apply(demo, chats.messages(chats.conversationFor(demo).id).last().id, owner).getOrThrow()
        agent.nextReply = BuilderAgentReply("Usulan kedua.", proposedDraft = garmentDraft())
        send(demo, "Revisi").getOrThrow()
        val other = com.eventverse.app.domain.auth.UserId("usr-lain")

        val stored = apply(demo, chats.messages(chats.conversationFor(demo).id).last().id, other).getOrThrow()

        assertEquals(owner, stored.ownerUserId, "pemilik draf yang sudah ada tidak berpindah ke penerap berikutnya")
    }

    @Test
    fun apply_savesTenantDraft_andMarksMessageApplied() = runTest {
        agent.nextReply = BuilderAgentReply("Usulan draf pertama.", proposedDraft = garmentDraft())
        send(demo, "Buatkan alur awal").getOrThrow()
        val pending = chats.messages(chats.conversationFor(demo).id).last()

        val stored = apply(demo, pending.id, owner).getOrThrow()
        assertEquals(demo, stored.tenantId, "draf kerja milik tenant, bukan pribadi pemanggil")
        assertEquals(owner, stored.ownerUserId, "draf baru dimiliki pemanggil yang menekan Terapkan (pengguna nyata), bukan pemilik karangan")
        assertTrue(chats.messages(chats.conversationFor(demo).id).last().appliedDraftId != null)

        val reapplied = apply(demo, pending.id, owner)
        assertTrue(reapplied.isFailure, "patch yang sudah diterapkan tidak bisa diterapkan dua kali")
    }

    @Test
    fun agentFailure_stillPersistsUserMessage() = runTest {
        agent.failNext = true
        assertTrue(send(bordir, "Buat alur bordir").isFailure)
        val messages = chats.messages(chats.conversationFor(bordir).id)
        assertEquals(1, messages.size)
        assertEquals(ChatRole.USER, messages.single().role, "pesan user tidak boleh hilang saat agent gagal")
    }

    @Test
    fun messages_neverLeakAcrossTenants() = runTest {
        send(demo, "Pesan garment").getOrThrow()
        send(bordir, "Pesan bordir").getOrThrow()

        assertEquals(2, chats.messages(chats.conversationFor(demo).id).size)
        assertEquals(2, chats.messages(chats.conversationFor(bordir).id).size)
        assertTrue(
            chats.messages(chats.conversationFor(demo).id).none { it.tenantId == bordir },
            "pesan tenant lain tidak bocor"
        )
    }

    @Test
    fun apply_onLockedDraft_isRejected() = runTest {
        agent.nextReply = BuilderAgentReply("Usulan.", proposedDraft = garmentDraft())
        send(demo, "Usulkan").getOrThrow()
        val first = chats.messages(chats.conversationFor(demo).id).last()
        apply(demo, first.id, owner).getOrThrow()

        send(demo, "Revisi lagi").getOrThrow()
        val second = chats.messages(chats.conversationFor(demo).id).last()

        drafts.findByTenant(demo)?.let {
            drafts.save(it.copy(status = DiscoveryDraftStatus.LOCKED))
        }
        val result = apply(demo, second.id, owner)
        assertFailsWith<ApplyDraftPatchUseCase.DraftLockedException> { result.getOrThrow() }
    }

    private class StubAgent : BuilderAgent {
        var nextReply: BuilderAgentReply = BuilderAgentReply("Baik, dicatat.")
        var failNext = false

        override val agentRef = "stub/builder-v1"
        override suspend fun proposePatch(
            currentDraft: DiscoveryDraft?,
            history: List<ChatMessage>,
            userMessage: String
        ): Result<BuilderAgentReply> =
            if (failNext) Result.failure(IllegalStateException("agent down"))
            else Result.success(nextReply)
    }

    private class FakeTenantDrafts : DiscoveryDraftRepository {
        val rows = mutableMapOf<DiscoveryDraftId, StoredDiscoveryDraft>()
        var savedCount = 0

        override suspend fun findById(id: DiscoveryDraftId) = rows[id]
        override suspend fun findByOwner(owner: UserId) = rows.values.filter { it.ownerUserId == owner }
        override suspend fun findByTenant(tenantId: TenantId) = rows.values.lastOrNull { it.tenantId == tenantId }
        override suspend fun findAll() = rows.values.toList()
        override suspend fun save(stored: StoredDiscoveryDraft): StoredDiscoveryDraft {
            savedCount++
            rows[stored.id] = stored
            return stored
        }
    }
}

/** Fake repository chat untuk test domain: percakapan satu-per-tenant, pesan terurut. */
class InMemoryBuilderChatRepository : BuilderChatRepository {
    private val conversations = mutableMapOf<TenantId, BuilderConversation>()
    private val messages = mutableListOf<ChatMessage>()
    private var seq = 0

    override suspend fun conversationFor(tenantId: TenantId): BuilderConversation =
        conversations.getOrPut(tenantId) {
            BuilderConversation(BuilderConversationId("conv-${tenantId.value}"), tenantId)
        }

    override suspend fun messages(conversationId: BuilderConversationId): List<ChatMessage> =
        messages.filter { it.conversationId == conversationId }

    override suspend fun append(message: ChatMessage): ChatMessage {
        seq++
        val stamped = message.copy(id = ChatMessageId("${message.id.value}-$seq"))
        messages.add(stamped)
        return stamped
    }

    override suspend fun markAnswered(messageId: ChatMessageId, answers: Map<String, String>): ChatMessage? {
        val index = messages.indexOfFirst { it.id == messageId }
        if (index < 0) return null
        val updated = messages[index].copy(
            questions = messages[index].questions.map { q -> if (q.answer.isNullOrBlank()) q.copy(answer = answers[q.id] ?: q.answer) else q }
        )
        messages[index] = updated
        return updated
    }

    override suspend fun markApplied(messageId: ChatMessageId, draftId: DiscoveryDraftId): ChatMessage? {
        val index = messages.indexOfFirst { it.id == messageId }
        if (index < 0) return null
        val updated = messages[index].copy(appliedDraftId = draftId.value)
        messages[index] = updated
        return updated
    }
}


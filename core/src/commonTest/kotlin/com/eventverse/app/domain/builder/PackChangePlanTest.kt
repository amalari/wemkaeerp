package com.eventverse.app.domain.builder

import com.eventverse.app.domain.auth.UserId
import com.eventverse.app.domain.discovery.DiscoveryDraft
import com.eventverse.app.domain.discovery.DiscoveryDraftId
import com.eventverse.app.domain.discovery.DiscoveryDraftRepository
import com.eventverse.app.domain.discovery.StoredDiscoveryDraft
import com.eventverse.app.domain.discovery.interview.InterviewFixtures
import com.eventverse.app.domain.discovery.interview.InterviewPlanner
import com.eventverse.app.domain.discovery.interview.isPlanned
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.shared.discovery.DiscoveryDraftCodec
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Temuan 8 (cek visual 2026-10-16): cerita pengguna mengganti industri. Draf tersimpan ber-pack lama (`bengkel`), agent menyusun
 * draf ber-pack baru (`klinik`) TANPA wawancara. Perencana sebelumnya hanya berjalan terhadap pack lama, jadi usulan pertama tak
 * membawa wawancara dan tak ada log. Perencana kini dijalankan sekali lagi terhadap pack baru.
 */
class PackChangePlanTest {
    private val tenant = TenantId("klinik-uji")
    private val oldDraft = InterviewFixtures.draftOf(InterviewFixtures.bengkelPack, null)
    private val klinikDraft = InterviewFixtures.draftOf(InterviewFixtures.klinikPack, null)

    private class Drafts(draft: DiscoveryDraft) : DiscoveryDraftRepository {
        private var row = StoredDiscoveryDraft(DiscoveryDraftId("d1"), UserId("usr-uji"), draft, tenantId = TenantId("klinik-uji"))
        override suspend fun findById(id: DiscoveryDraftId) = row
        override suspend fun findByOwner(ownerUserId: UserId) = listOf(row)
        override suspend fun findByTenant(tenantId: TenantId) = row
        override suspend fun findAll() = listOf(row)
        override suspend fun save(stored: StoredDiscoveryDraft) = stored.also { row = it }
    }

    /** Meniru adapter produksi: draf agent apa adanya, tanpa wawancara (pack berbeda dari draf tersimpan). */
    private class FixedDraftAgent(private val to: DiscoveryDraft) : BuilderAgent {
        override val agentRef = "uji"
        override suspend fun proposePatch(currentDraft: DiscoveryDraft?, history: List<ChatMessage>, userMessage: String) =
            Result.success(BuilderAgentReply("draf", proposedDraft = to))
    }

    private val packsSeen = mutableListOf<String>()
    private val rejections = mutableListOf<PlanRejection>()

    /** Perencana yang hanya sah untuk pack klinik (katalog modul klinik); untuk pack lain ia gagal. */
    private val clinicOnlyPlanner = InterviewPlanner { draft, _, narrative ->
        packsSeen += draft.pack.code.value
        check(draft.pack.code.value == "klinik") { "modul tak ada di katalog ${draft.pack.code.value}" }
        InterviewFixtures.klinikSession.copy(narrative = narrative)
    }

    private fun useCase(agent: BuilderAgent, planner: InterviewPlanner) = SendBuilderMessageUseCase(
        InMemoryBuilderChatRepository(), agent, Drafts(oldDraft),
        interviewPlanner = planner, onPlanRejected = { _, r -> rejections += r }
    )

    @Test
    fun `cerita mengganti industri should menyertakan wawancara rencana pack baru di patch usulan`() = runTest {
        val messages = useCase(FixedDraftAgent(klinikDraft), clinicOnlyPlanner)(tenant, "Kami klinik gigi").getOrThrow()
        val json = assertNotNull(messages.last { it.role == ChatRole.AGENT }.proposedDraftJson)
        val proposed = DiscoveryDraftCodec.decode(json)
        assertEquals("klinik", proposed.pack.code.value)
        assertTrue(proposed.interview?.isPlanned == true, "wawancara harus ada di usulan")
        assertEquals(InterviewFixtures.klinikSession.links.size, proposed.interview?.links?.size)
        assertEquals(listOf("bengkel", "klinik"), packsSeen)
    }

    @Test
    fun `pack tidak berubah should tidak menjalankan perencana kedua kali`() = runTest {
        useCase(FixedDraftAgent(oldDraft), clinicOnlyPlanner)(tenant, "Kami bengkel").getOrThrow()
        assertEquals(listOf("bengkel"), packsSeen)
    }

    @Test
    fun `perencana pack baru gagal should melaporkan penolakan bukan senyap`() = runTest {
        val failing = InterviewPlanner { draft, _, _ -> error("gagal ${draft.pack.code.value}") }
        useCase(FixedDraftAgent(klinikDraft), failing)(tenant, "Kami klinik gigi").getOrThrow()
        assertEquals(2, rejections.size, "satu per percobaan (pack lama dan pack baru)")
        assertTrue(rejections.last().detail.contains("gagal klinik"))
    }
}

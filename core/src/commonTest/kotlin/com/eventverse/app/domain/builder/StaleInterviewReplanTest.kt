package com.eventverse.app.domain.builder

import com.eventverse.app.domain.auth.UserId
import com.eventverse.app.domain.discovery.DiscoveryDraft
import com.eventverse.app.domain.discovery.DiscoveryDraftId
import com.eventverse.app.domain.discovery.DiscoveryDraftRepository
import com.eventverse.app.domain.discovery.DiscoveryDraftValidator
import com.eventverse.app.domain.discovery.StoredDiscoveryDraft
import com.eventverse.app.domain.discovery.interview.InterviewFixtures
import com.eventverse.app.domain.discovery.interview.InterviewPlanner
import com.eventverse.app.domain.discovery.interview.InterviewValidator
import com.eventverse.app.domain.pack.PortType
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.shared.discovery.DiscoveryDraftCodec
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Temuan 11 (model sungguhan, 2026-10-17; direproduksi dengan log `patch_apply_rejected`): `POST /chat/apply` membalas 409
 * `Patch ditolak validator: $.interview.handoffs[0].portType: Port 'ANTREAN' tidak cocok: ... mengeluarkan 'PASIEN' ...`.
 *
 * Alurnya: draf tersimpan sudah berwawancara -> perencana tak dijalankan lagi -> adapter agent menyalin wawancara LAMA ke draf agent
 * (pack.code sama) -> agent menyusun ulang slot/port modul -> handoff lama tak cocok dengan port baru -> patch ditawarkan ke
 * pengguna padahal pasti ditolak saat Terapkan. Perbaikan: wawancara di usulan yang tak sah untuk pack usulan direncanakan ulang;
 * bila gagal, dibuang (dan dilaporkan) alih-alih menawarkan patch yang tak bisa diterapkan.
 */
class StaleInterviewReplanTest {
    private val tenant = TenantId("klinik-uji")
    private val valid = InterviewFixtures.klinikSession
    private val stored = InterviewFixtures.draftOf(InterviewFixtures.klinikPack, valid)

    /** Wawancara basi: handoff memakai port yang bukan port tersambung pack (meniru nama port lama yang tak cocok slot baru). */
    private val stale = valid.copy(handoffs = valid.handoffs.map { it.copy(portType = PortType("ANTREAN")) })

    private class Drafts(draft: DiscoveryDraft) : DiscoveryDraftRepository {
        private var row = StoredDiscoveryDraft(DiscoveryDraftId("d1"), UserId("usr-uji"), draft, tenantId = TenantId("klinik-uji"))
        override suspend fun findById(id: DiscoveryDraftId) = row
        override suspend fun findByOwner(ownerUserId: UserId) = listOf(row)
        override suspend fun findByTenant(tenantId: TenantId) = row
        override suspend fun findAll() = listOf(row)
        override suspend fun save(stored: StoredDiscoveryDraft) = stored.also { row = it }
    }

    /** Meniru adapter produksi: draf agent apa adanya, membawa wawancara yang diberikan (hasil salinan wawancara lama). */
    private class FixedDraftAgent(private val to: DiscoveryDraft) : BuilderAgent {
        override val agentRef = "uji"
        override suspend fun proposePatch(currentDraft: DiscoveryDraft?, history: List<ChatMessage>, userMessage: String) =
            Result.success(BuilderAgentReply("draf", proposedDraft = to))
    }

    private val rejections = mutableListOf<PlanRejection>()
    private var plannerCalls = 0

    private val goodPlanner = InterviewPlanner { _, _, narrative -> plannerCalls++; valid.copy(narrative = narrative) }
    private val failingPlanner = InterviewPlanner { _, _, _ -> plannerCalls++; error("model tidak menjawab") }

    private suspend fun proposedBy(agentDraft: DiscoveryDraft, planner: InterviewPlanner): DiscoveryDraft {
        val uc = SendBuilderMessageUseCase(
            InMemoryBuilderChatRepository(), FixedDraftAgent(agentDraft), Drafts(stored),
            interviewPlanner = planner, onPlanRejected = { _, r -> rejections += r }
        )
        val messages = uc(tenant, "Tambahkan stok obat").getOrThrow()
        return DiscoveryDraftCodec.decode(assertNotNull(messages.last { it.role == ChatRole.AGENT }.proposedDraftJson))
    }

    @Test
    fun `prasyarat - wawancara basi memang ditolak validator dan wawancara asli sah`() {
        assertTrue(InterviewValidator.validate(valid, InterviewFixtures.klinikPack).isEmpty())
        assertTrue(InterviewValidator.validate(stale, InterviewFixtures.klinikPack).isNotEmpty())
        assertTrue(DiscoveryDraftValidator.validate(stored.copy(interview = stale)).isNotEmpty(), "patch basi pasti ditolak saat Terapkan")
    }

    @Test
    fun `wawancara basi di usulan should direncanakan ulang dan patch lolos validator`() = runTest {
        val proposed = proposedBy(stored.copy(interview = stale), goodPlanner)
        assertEquals(valid.links.size, proposed.interview?.links?.size, "wawancara diganti hasil rencana ulang")
        assertTrue(DiscoveryDraftValidator.validate(proposed).isEmpty(), "patch yang ditawarkan harus bisa diterapkan")
        assertEquals(1, plannerCalls)
        assertTrue(rejections.isEmpty())
    }

    @Test
    fun `rencana ulang gagal should membuang wawancara basi dan melaporkan alasannya`() = runTest {
        val proposed = proposedBy(stored.copy(interview = stale), failingPlanner)
        assertNull(proposed.interview, "jangan menawarkan patch yang pasti ditolak")
        assertTrue(DiscoveryDraftValidator.validate(proposed).isEmpty(), "tanpa wawancara basi patch lolos validator")
        assertEquals(1, rejections.size)
        assertEquals(PlanRejectionKind.PLANNER_ERROR, rejections.single().kind)
    }

    @Test
    fun `wawancara di usulan yang sah should dibiarkan dan perencana tidak dijalankan lagi`() = runTest {
        val proposed = proposedBy(stored.copy(interview = valid), goodPlanner)
        assertEquals(valid.links.size, proposed.interview?.links?.size)
        assertEquals(0, plannerCalls)
        assertTrue(rejections.isEmpty())
    }
}

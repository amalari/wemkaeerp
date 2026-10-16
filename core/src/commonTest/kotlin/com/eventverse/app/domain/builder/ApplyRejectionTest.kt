package com.eventverse.app.domain.builder

import com.eventverse.app.domain.auth.UserId
import com.eventverse.app.domain.discovery.DiscoveryDraftId
import com.eventverse.app.domain.discovery.DiscoveryDraftRepository
import com.eventverse.app.domain.discovery.DiscoveryDraftValidator
import com.eventverse.app.domain.discovery.StoredDiscoveryDraft
import com.eventverse.app.domain.discovery.interview.InterviewFixtures
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.shared.discovery.DiscoveryDraftCodec
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Temuan 11 (409 pada Terapkan, penyebab belum diketahui). Kandidat yang dibuktikan di sini: draf ber-pack `klinik` yang
 * modulnya lebih sedikit dari wawancara bertaut (pack disusun ulang agent, wawancara lama terbawa karena kode pack sama).
 * Layar yang lebih sedikit dari modul BUKAN pelanggaran; tautan wawancara ke modul yang tak ada di pack ADALAH.
 */
class ApplyRejectionTest {
    private val tenant = TenantId("klinik-uji")
    private val fullPack = InterviewFixtures.klinikPack
    private val shrunkPack = InterviewFixtures.pack(
        "klinik", "Klinik",
        InterviewFixtures.operational("klinik_pendaftaran", "Pendaftaran"),
        InterviewFixtures.operational("klinik_poli", "Poli")
    )

    private class Drafts : DiscoveryDraftRepository {
        var row: StoredDiscoveryDraft? = null
        override suspend fun findById(id: DiscoveryDraftId) = row
        override suspend fun findByOwner(ownerUserId: UserId) = listOfNotNull(row)
        override suspend fun findByTenant(tenantId: TenantId) = row
        override suspend fun findAll() = listOfNotNull(row)
        override suspend fun save(stored: StoredDiscoveryDraft) = stored.also { row = it }
    }

    @Test
    fun `layar lebih sedikit dari modul dengan wawancara selaras should lolos validator`() {
        val draft = InterviewFixtures.draftOf(fullPack, InterviewFixtures.klinikSession)
        assertTrue(draft.screens.isEmpty())
        assertEquals(emptyList(), DiscoveryDraftValidator.validate(draft))
    }

    @Test
    fun `wawancara bertaut ke modul yang tak ada di pack should ditolak dengan path interview`() {
        val draft = InterviewFixtures.draftOf(shrunkPack, InterviewFixtures.klinikSession)
        val issues = DiscoveryDraftValidator.validate(draft)
        assertTrue(issues.isNotEmpty())
        assertTrue(issues.all { it.path.startsWith("$.interview") }, issues.toString())
    }

    @Test
    fun `apply patch yang ditolak validator should gagal dengan alasan berpath dan tidak menyimpan`() = runTest {
        val chats = InMemoryBuilderChatRepository()
        val drafts = Drafts()
        val conversation = chats.conversationFor(tenant)
        val bad = InterviewFixtures.draftOf(shrunkPack, InterviewFixtures.klinikSession)
        chats.append(
            ChatMessage(
                id = ChatMessageId("m1"), conversationId = conversation.id, tenantId = tenant, role = ChatRole.AGENT,
                text = "usulan", proposedDraftJson = DiscoveryDraftCodec.encodeToString(bad),
                createdAt = kotlinx.datetime.Clock.System.now()
            )
        )
        val storedId = chats.messages(conversation.id).last().id
        val result = ApplyDraftPatchUseCase(chats, drafts)(tenant, storedId, UserId("usr-uji"))
        val message = assertNotNull(result.exceptionOrNull()?.message)
        assertTrue(message.startsWith("Patch ditolak validator:") && "$.interview" in message, message)
        assertFalse(drafts.row != null, "draf tidak boleh tersimpan bila ditolak")
    }
}

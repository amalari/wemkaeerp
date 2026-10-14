package com.eventverse.app.domain.builder

import com.eventverse.app.domain.auth.UserId
import com.eventverse.app.domain.discovery.DiscoveryDraftId
import com.eventverse.app.domain.discovery.DiscoveryDraftRepository
import com.eventverse.app.domain.discovery.StoredDiscoveryDraft
import com.eventverse.app.domain.discovery.interview.InterviewFixtures
import com.eventverse.app.domain.discovery.interview.InterviewFixtures.draftOf
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Regresi: klien menyiapkan follow-up semua modul beruntun saat halaman dimuat. Id pesan dulu hanya memakai
 * `epochSeconds`, jadi dua modul dalam detik yang sama bertabrakan di primary key (500 di server nyata).
 */
class FollowUpMessageIdTest {
    private val tenant = TenantId("ten-uji")
    private val frozen = object : Clock { override fun now(): Instant = Instant.fromEpochSeconds(1_791_987_347) }

    private class Drafts(private val row: StoredDiscoveryDraft) : DiscoveryDraftRepository {
        override suspend fun findById(id: DiscoveryDraftId) = row
        override suspend fun findByOwner(ownerUserId: UserId) = listOf(row)
        override suspend fun findByTenant(tenantId: TenantId) = row
        override suspend fun findAll() = listOf(row)
        override suspend fun save(stored: StoredDiscoveryDraft) = stored
    }

    /**
     * Meniru primary key Postgres: id pesan yang MASUK ke `append` tidak boleh ganda. `InMemoryBuilderChatRepository`
     * menempelkan nomor urut ke id sehingga selalu unik dan menyembunyikan tabrakan semacam ini — jadi periksa
     * id sebelum di-stamp.
     */
    private class StrictChats(private val inner: InMemoryBuilderChatRepository) : BuilderChatRepository by inner {
        private val seen = mutableSetOf<String>()
        override suspend fun append(message: ChatMessage): ChatMessage {
            check(seen.add(message.id.value)) { "duplicate key: ${message.id.value}" }
            return inner.append(message)
        }
    }

    @Test
    fun `follow-up dua modul dalam detik yang sama should membuat id pesan berbeda`() = runTest {
        val chats = StrictChats(InMemoryBuilderChatRepository())
        val draft = draftOf(InterviewFixtures.klinikPack, null)
        val ask = AskModuleFollowUpsUseCase(chats, Drafts(StoredDiscoveryDraft(DiscoveryDraftId("d1"), UserId("u-1"), draft, tenantId = tenant)), frozen)

        assertTrue(ask(tenant, "klinik_poli").getOrThrow())
        assertTrue(ask(tenant, "klinik_kasir").getOrThrow())

        assertEquals(2, chats.messages(chats.conversationFor(tenant).id).size)
    }
}

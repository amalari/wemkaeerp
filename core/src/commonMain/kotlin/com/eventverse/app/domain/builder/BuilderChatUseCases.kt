package com.eventverse.app.domain.builder

import com.eventverse.app.domain.auth.UserId
import com.eventverse.app.shared.discovery.DiscoveryDraftCodec
import com.eventverse.app.domain.discovery.DiscoveryDraftId
import com.eventverse.app.domain.discovery.DiscoveryDraftRepository
import com.eventverse.app.domain.discovery.DiscoveryDraftStatus
import com.eventverse.app.domain.discovery.DiscoveryDraftValidator
import com.eventverse.app.domain.discovery.StoredDiscoveryDraft
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Instant
import kotlinx.datetime.Clock

/**
 * Mengirim pesan user dan merekam balasan agent (FR-M1-1/2). Urutannya penting: pesan USER dicatat
 * **dulu**, baru agent menjawab — putaran koneksi agent yang gagal tidak boleh menghilangkan
 * apa yang user ketik.
 *
 * Patch usulan **belum disentuh draf**: ia disimpan sebagai JSON di pesan AGENT dan diterapkan
 * lewat [ApplyDraftPatchUseCase] oleh keputusan manusia.
 */
class SendBuilderMessageUseCase(
    private val chats: BuilderChatRepository,
    private val agent: BuilderAgent,
    private val drafts: DiscoveryDraftRepository,
    private val clock: Clock = Clock.System
) {
    suspend operator fun invoke(tenantId: TenantId, userText: String): Result<List<ChatMessage>> = runCatching {
        require(userText.isNotBlank()) { "Pesan tidak boleh kosong" }
        val conversation = chats.conversationFor(tenantId)

        chats.append(
            ChatMessage(
                id = ChatMessageId("msg-${conversation.id.value}-u-${clock.now().epochSeconds}"),
                conversationId = conversation.id,
                tenantId = tenantId,
                role = ChatRole.USER,
                text = userText.trim(),
                createdAt = clock.now()
            )
        )

        val current = drafts.findByTenant(tenantId)?.draft
        val reply = agent.proposePatch(current, chats.messages(conversation.id), userText.trim()).getOrThrow()

        chats.append(
            ChatMessage(
                id = ChatMessageId("msg-${conversation.id.value}-a-${clock.now().epochSeconds}"),
                conversationId = conversation.id,
                tenantId = tenantId,
                role = ChatRole.AGENT,
                text = reply.text,
                proposedDraftJson = reply.proposedDraft?.let(DiscoveryDraftCodec::encodeToString),
                proposedSummary = reply.summary,
                createdAt = clock.now()
            )
        )
        chats.messages(conversation.id)
    }
}

/**
 * Menerapkan patch usulan dari satu pesan AGENT (FR-M1-3): validator menilai dokumen, draf kerja
 * tenant dibuat/diganti di tempat (DRAFT), pesan ditandai `appliedDraftId`. Draf terkunci ditolak
 * (Kontrak 5 — revisi pasca-lock adalah versi baru lewat deploy, bukan edit di tempat).
 */
class ApplyDraftPatchUseCase(
    private val chats: BuilderChatRepository,
    private val drafts: DiscoveryDraftRepository,
    private val clock: Clock = Clock.System
) {
    class DraftLockedException(message: String) : IllegalStateException(message)

    suspend operator fun invoke(tenantId: TenantId, messageId: ChatMessageId): Result<StoredDiscoveryDraft> =
        runCatching {
            val conversation = chats.conversationFor(tenantId)
            val message = chats.messages(conversation.id).firstOrNull { it.id == messageId }
                ?: throw ChatMessageNotFoundException("Pesan ${messageId.value} tidak ada di percakapan tenant ini")
            val json = requireNotNull(message.proposedDraftJson) { "Pesan ini tidak membawa patch usulan" }
            require(message.appliedDraftId == null) { "Patch pesan ${messageId.value} sudah diterapkan" }

            val draft = DiscoveryDraftCodec.decode(json)
            val issues = DiscoveryDraftValidator.validate(draft)
            require(issues.isEmpty()) {
                "Patch ditolak validator: " + issues.joinToString("; ") { "${it.path}: ${it.message}" }
            }

            val existing = drafts.findByTenant(tenantId)
            if (existing?.status == DiscoveryDraftStatus.LOCKED) {
                throw DraftLockedException("Draf kerja tenant sudah terkunci — buat deployment untuk revisi berikutnya")
            }
            val stored = drafts.save(
                StoredDiscoveryDraft(
                    id = existing?.id ?: DiscoveryDraftId("draft-${tenantId.value}"),
                    ownerUserId = existing?.ownerUserId
                        ?: UserId("usr-builder-${tenantId.value.takeLast(8)}"),
                    draft = draft,
                    tenantId = tenantId,
                    createdAt = existing?.createdAt ?: clock.now(),
                    updatedAt = clock.now()
                )
            )
            chats.markApplied(messageId, stored.id)
            stored
        }
}

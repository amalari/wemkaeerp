package com.eventverse.app.domain.builder

import com.eventverse.app.domain.auth.UserId
import com.eventverse.app.shared.discovery.DiscoveryDraftCodec
import com.eventverse.app.domain.discovery.DiscoveryDraftId
import com.eventverse.app.domain.discovery.DiscoveryDraftRepository
import com.eventverse.app.domain.discovery.DiscoveryDraftStatus
import com.eventverse.app.domain.discovery.DiscoveryDraftValidator
import com.eventverse.app.domain.discovery.StoredDiscoveryDraft
import com.eventverse.app.domain.discovery.interview.InterviewLimits
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
    private val clock: Clock = Clock.System,
    /** Penanya klarifikasi sebelum draf (Fase B); null = langsung menyusun draf. Kegagalannya = tidak bertanya. */
    private val clarifier: NarrativeClarifier? = null,
    /** Penyunting isian per modul (Fase C); null = utas modul memakai agent penyusun draf. */
    private val moduleEditing: EditModuleFromChat? = null
) {
    /**
     * Satu giliran chat. [onProgress] menerima fase kerja (`planning` saat memeriksa kejelasan cerita, `drafting` saat
     * agent menyusun draf) untuk indikator memuat. Urutan:
     *
     * 1. pesan USER dicatat;
     * 2. follow-up yang menunggu **di utas ini** dijawab oleh pesan ini (tercatat di pertanyaannya);
     * 3. sekali per utas Semua, bila belum pernah bertanya, penanya boleh mengajukan pertanyaan → pesan QUESTION dan
     *    giliran berhenti (draf menunggu jawaban);
     * 4. selain itu agent menyusun patch dari **narasi gabungan** utas ([composeNarrative]).
     */
    suspend operator fun invoke(
        tenantId: TenantId,
        userText: String,
        moduleId: String? = null,
        onProgress: suspend (String) -> Unit = {}
    ): Result<List<ChatMessage>> = runCatching {
        require(userText.isNotBlank()) { "Pesan tidak boleh kosong" }
        val conversation = chats.conversationFor(tenantId)

        chats.append(
            ChatMessage(
                id = ChatMessageId("msg-${conversation.id.value}-u-${clock.now().epochSeconds}"),
                conversationId = conversation.id,
                tenantId = tenantId,
                role = ChatRole.USER,
                text = userText.trim(),
                createdAt = clock.now(),
                moduleId = moduleId
            )
        )

        // Follow-up yang menunggu di utas persis ini dijawab oleh pesan ini; pertanyaan utas lain tidak tersentuh.
        val waiting = chats.messages(conversation.id).pendingFollowUps(moduleId).filter { it.moduleId == moduleId }
        waiting.groupBy { it.messageId }.forEach { (messageId, qs) ->
            chats.markAnswered(messageId, qs.associate { it.question.id to userText.trim() })
        }

        // Utas modul: pesan (dan jawaban follow-up) ditafsirkan menjadi sunting isian modul itu, bukan menyusun ulang draf.
        val editing = moduleEditing
        if (editing != null && moduleId != null) {
            val answered = waiting.map { it.question.question to userText.trim() }
            if (editing.handle(tenantId, conversation, moduleId, userText.trim(), answered, onProgress)) {
                return@runCatching chats.messages(conversation.id)
            }
        }

        val current = drafts.findByTenant(tenantId)?.draft
        val thread = chats.messages(conversation.id).inThread(moduleId)

        val asker = clarifier
        if (asker != null && moduleId == null && waiting.isEmpty() && thread.none { it.kind == ChatMessageKind.QUESTION }) {
            onProgress("planning")
            val questions = runCatching {
                asker.clarify(composeNarrative(thread), current?.blueprint?.activeModuleCodes?.toList().orEmpty())
            }.getOrDefault(emptyList()).take(InterviewLimits.CLARIFICATIONS)
            if (questions.isNotEmpty()) {
                chats.append(
                    ChatMessage(
                        id = ChatMessageId("msg-${conversation.id.value}-a-${clock.now().epochSeconds}"),
                        conversationId = conversation.id,
                        tenantId = tenantId,
                        role = ChatRole.AGENT,
                        text = "Sebelum saya menyusun alur dan modulnya, ada yang ingin saya pastikan. Jawab singkat saja di chat:",
                        createdAt = clock.now(),
                        moduleId = moduleId,
                        kind = ChatMessageKind.QUESTION,
                        questions = questions
                    )
                )
                return@runCatching chats.messages(conversation.id)
            }
        }

        onProgress("drafting")
        val reply = agent.proposePatch(current, chats.messages(conversation.id), composeNarrative(thread)).getOrThrow()

        chats.append(
            ChatMessage(
                id = ChatMessageId("msg-${conversation.id.value}-a-${clock.now().epochSeconds}"),
                conversationId = conversation.id,
                tenantId = tenantId,
                role = ChatRole.AGENT,
                text = reply.text,
                proposedDraftJson = reply.proposedDraft?.let(DiscoveryDraftCodec::encodeToString),
                proposedSummary = reply.summary,
                createdAt = clock.now(),
                moduleId = moduleId
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

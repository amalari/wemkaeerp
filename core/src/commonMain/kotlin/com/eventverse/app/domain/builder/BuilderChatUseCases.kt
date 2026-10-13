package com.eventverse.app.domain.builder

import com.eventverse.app.domain.auth.UserId
import com.eventverse.app.shared.discovery.DiscoveryDraftCodec
import com.eventverse.app.domain.discovery.DiscoveryDraftId
import com.eventverse.app.domain.discovery.DiscoveryDraftRepository
import com.eventverse.app.domain.discovery.DiscoveryDraftStatus
import com.eventverse.app.domain.discovery.DiscoveryDraftValidator
import com.eventverse.app.domain.discovery.StoredDiscoveryDraft
import com.eventverse.app.domain.discovery.interview.InterviewLimits
import com.eventverse.app.domain.discovery.interview.InterviewPlanner
import com.eventverse.app.domain.discovery.interview.InterviewSession
import com.eventverse.app.domain.discovery.interview.InterviewStep
import com.eventverse.app.domain.discovery.interview.InterviewValidator
import com.eventverse.app.domain.discovery.interview.isPlanned
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Instant
import kotlinx.datetime.Clock
import kotlinx.coroutines.CancellationException

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
    /** Perencana wawancara penuh yang sudah dipakai Discovery; null = langsung menyusun draf. */
    private val interviewPlanner: InterviewPlanner? = null,
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
        /**
         * Jawaban per-pertanyaan dari modal follow-up (`question.id` → jawaban). Kosong = chat bebas:
         * seluruh pertanyaan menunggu di utas itu dijawab oleh [userText] (perilaku lama).
         */
        answers: Map<String, String> = emptyMap(),
        onModuleProgress: suspend (moduleId: String, title: String, ready: Boolean) -> Unit = { _, _, _ -> },
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
        // Jawaban spesifik per pertanyaan bila modal mengirimnya; chat bebas (tanpa `answers`) jatuh ke teks pesan.
        // Pertanyaan yang tidak hadir di `answers` tetap menunggu — tanpa fallback senyap.
        fun answerFor(questionId: String): String? =
            answers[questionId] ?: if (answers.isEmpty()) userText.trim() else null
        waiting.groupBy { it.messageId }.forEach { (messageId, qs) ->
            val marked = qs.mapNotNull { q -> answerFor(q.question.id)?.let { q.question.id to it } }.toMap()
            if (marked.isNotEmpty()) chats.markAnswered(messageId, marked)
        }

        // Utas modul: pesan (dan jawaban follow-up) ditafsirkan menjadi sunting isian modul itu, bukan menyusun ulang draf.
        val editing = moduleEditing
        if (editing != null && moduleId != null) {
            val answered = waiting.mapNotNull { f -> answerFor(f.question.id)?.let { f.question.question to it } }
            if (editing.handle(tenantId, conversation, moduleId, userText.trim(), answered, onProgress)) {
                return@runCatching chats.messages(conversation.id)
            }
        }

        val current = drafts.findByTenant(tenantId)?.draft
        val thread = chats.messages(conversation.id).inThread(moduleId)

        var plannedCurrent = current
        val planner = interviewPlanner
        val planningQuestions = thread.filter { it.moduleId == null && it.kind == ChatMessageKind.QUESTION }
        val shouldPlan = planner != null && current != null && moduleId == null &&
            (waiting.isNotEmpty() || (planningQuestions.isEmpty() && current.interview?.isPlanned != true))
        if (shouldPlan) {
            onProgress("planning")
            val narrative = composeNarrative(thread)
            val prior = current.interview ?: InterviewSession(
                step = InterviewStep.G1_DIVISI,
                version = InterviewSession.BASED_ON_STORY,
                narrative = narrative
            )
            val answered = planningQuestions.flatMap { it.questions }
            val input = prior.copy(narrative = narrative, clarifications = answered)
            val planned = try {
                planner.plan(current.copy(interview = input), input, narrative)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
                ?.takeIf { InterviewValidator.validate(it, current.pack).isEmpty() }
                // Setelah satu putaran tanya, perencana tidak boleh meminta putaran kedua.
                ?.takeIf { answered.isEmpty() || !it.awaitingClarification }
            val questions = planned?.clarifications?.filter { it.answer.isNullOrBlank() }
                .orEmpty().take(InterviewLimits.CLARIFICATIONS)
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
            if (planned != null) plannedCurrent = current.copy(interview = planned)
        }

        onProgress("drafting")
        val reply = agent.proposePatch(plannedCurrent, chats.messages(conversation.id), composeNarrative(thread)).getOrThrow()

        reply.proposedDraft?.blueprint?.activeModuleCodes.orEmpty().forEach { code ->
            val title = reply.proposedDraft?.pack?.modules?.firstOrNull { it.id.value == code }?.displayName ?: code
            onModuleProgress(code, title, false)
            onModuleProgress(code, title, true)
        }

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
 *
 * **Pemilik draf baru = pemanggil yang menekan Terapkan** (`ownerUserId`), sama dengan bootstrap `GET /draft`. Draf yang sudah ada
 * mempertahankan pemiliknya. Pemilik wajib pengguna nyata (FK `ops.discovery_drafts.owner_user_id` → `users`); dulu dikarang
 * (`usr-builder-<tenant>`) sehingga tenant tanpa draf kerja tak pernah bisa menerapkan patch pertamanya.
 */
class ApplyDraftPatchUseCase(
    private val chats: BuilderChatRepository,
    private val drafts: DiscoveryDraftRepository,
    private val clock: Clock = Clock.System
) {
    class DraftLockedException(message: String) : IllegalStateException(message)

    suspend operator fun invoke(tenantId: TenantId, messageId: ChatMessageId, ownerUserId: UserId): Result<StoredDiscoveryDraft> =
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
                throw DraftLockedException("Draf kerja tenant sudah terkunci - buat deployment untuk revisi berikutnya")
            }
            val stored = drafts.save(
                StoredDiscoveryDraft(
                    id = existing?.id ?: DiscoveryDraftId("draft-${tenantId.value}"),
                    ownerUserId = existing?.ownerUserId ?: ownerUserId,
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

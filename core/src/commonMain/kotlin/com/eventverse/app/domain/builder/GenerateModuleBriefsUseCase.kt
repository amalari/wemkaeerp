package com.eventverse.app.domain.builder

import com.eventverse.app.domain.discovery.DiscoveryDraft
import com.eventverse.app.domain.discovery.DiscoveryDraftRepository
import com.eventverse.app.domain.discovery.interview.Clarification
import com.eventverse.app.domain.evidence.EvidenceContext
import com.eventverse.app.domain.pack.ModuleDefinition
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.datetime.Clock

/**
 * Membuat brief per modul (TRD-BLDR-001 FR-1..FR-7, FR-10). SIGNATURE DIBEKUKAN (Track B bergantung).
 *
 * Per modul: di luar cakupan → SKIPPED; brief terbaru berbasis sama → FRESH (tanpa menulis); selain itu model
 * ([briefer]) dicoba, gagal/tak sah → satu ulang dengan umpan balik berpath → fallback [DeterministicModuleBriefer]
 * (`source = DETERMINISTIC`, terlihat di UI). Follow-up AI + celah [ModuleGapAnalyzer] total ≤ [ModuleBriefLimits.FOLLOW_UPS_MAX]
 * dan tidak dibuat selama masih ada pertanyaan menunggu jawaban di utas modul.
 *
 * Kegagalan **tak terduga** satu modul (mis. penyimpanan) tidak menghentikan modul lain; modul itu dilaporkan
 * [BriefStatus.SKIPPED] karena kontrak outcome tidak punya status galat — tidak ada pesan yang ditulis untuknya,
 * sehingga pemanggilan ulang mencobanya lagi (idempoten).
 */
class GenerateModuleBriefsUseCase(
    private val chats: BuilderChatRepository,
    private val drafts: DiscoveryDraftRepository,
    private val briefer: ModuleBriefer,
    private val validator: ModuleBriefValidator,
    private val agentRef: String,
    private val clock: Clock = Clock.System
) : ModuleBriefService {

    override suspend fun generate(tenantId: TenantId, moduleId: String?): Result<List<ModuleBriefOutcome>> = runCatching {
        val draft = drafts.findByTenant(tenantId)?.draft ?: return@runCatching emptyList()
        val conversation = chats.conversationFor(tenantId)
        val history = chats.messages(conversation.id)
        val narrative = composeNarrative(history.inThread(null))
        val targets = draft.pack.modules.filter { moduleId == null || it.id.value == moduleId }
        val gate = Semaphore(MAX_CONCURRENCY)
        val outcomes = coroutineScope {
            targets.map { module ->
                async {
                    gate.withPermit {
                        try {
                            one(tenantId, draft, conversation, history, narrative, module)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            ModuleBriefOutcome(module.id.value, BriefStatus.SKIPPED)
                        }
                    }
                }
            }.awaitAll()
        }
        if (moduleId != null && outcomes.isEmpty()) listOf(ModuleBriefOutcome(moduleId, BriefStatus.SKIPPED)) else outcomes
    }

    private suspend fun one(
        tenantId: TenantId, draft: DiscoveryDraft, conversation: BuilderConversation,
        history: List<ChatMessage>, narrative: String, module: ModuleDefinition
    ): ModuleBriefOutcome {
        val id = module.id.value
        if (!ModuleBriefInputs.inScope(draft, module)) return ModuleBriefOutcome(id, BriefStatus.SKIPPED)
        val thread = history.inThread(id)
        val fingerprint = BriefKeys.inputFingerprint(narrative, ModuleBriefInputs.roleFeatures(draft, module).map { it.label }, agentRef)
        val latest = thread.lastOrNull { it.kind == ChatMessageKind.BRIEF }
        if (latest?.inputFingerprint == fingerprint) return ModuleBriefOutcome(id, BriefStatus.FRESH)

        // "Sudah ditanyakan" = semua kecuali pertanyaan AI yang belum dijawab: yang itu digantikan generasi baru, jadi yang masih
        // relevan boleh ditanyakan ulang (id sama, lihat BriefKeys.followUpId) dan tidak hilang.
        val asked = thread.filter { it.kind == ChatMessageKind.QUESTION }.flatMap { it.questions }
            .filterNot { BriefKeys.isAiQuestionId(it.id) && it.answer.isNullOrBlank() }
        fun request(feedback: String?) = ModuleBriefInputs.request(tenantId, draft, module, narrative, asked, feedback)
        val brief = fromModel(::request, evidenceContext(draft, narrative, history, thread)) ?: DeterministicModuleBriefer.of(request(null))

        val now = clock.now()
        chats.append(
            ChatMessage(
                id = ChatMessageId("msg-${conversation.id.value}-b-$id-${fingerprint.takeLast(ID_SUFFIX)}"),
                conversationId = conversation.id, tenantId = tenantId, role = ChatRole.AGENT,
                text = brief.summary, createdAt = now, moduleId = id, kind = ChatMessageKind.BRIEF,
                features = brief.features, stages = brief.stages, inputFingerprint = fingerprint, briefSource = brief.source
            )
        )
        val questions = followUps(draft, id, brief, thread, asked)
        // Pertanyaan AI: pesan baru dibuat bila ada yang belum tampil (menggantikan generasi lama); himpunan yang sama dengan yang
        // sudah menunggu tidak digandakan. Cadangan template: hanya bila belum ada yang menunggu (perilaku semula).
        val pending = thread.pendingFollowUps(id)
        val aiPendingIds = pending.map { it.question.id }.filter(BriefKeys::isAiQuestionId).toSet()
        val create = questions.isNotEmpty() && (
            if (questions.all { BriefKeys.isAiQuestionId(it.id) }) questions.any { it.id !in aiPendingIds } else pending.isEmpty()
        )
        if (create) {
            val key = BriefKeys.fingerprint(questions.joinToString("|") { it.id })
            chats.append(
                ChatMessage(
                    id = ChatMessageId("msg-${conversation.id.value}-q-$id-$key"),
                    conversationId = conversation.id, tenantId = tenantId, role = ChatRole.AGENT,
                    text = "Beberapa hal tentang ${module.displayName} belum jelas dari ceritamu. Jawab seperlunya, atau langsung minta ubah isiannya:",
                    createdAt = now, moduleId = id, kind = ChatMessageKind.QUESTION, questions = questions
                )
            )
        }
        return ModuleBriefOutcome(id, BriefStatus.CREATED, brief.source)
    }

    /** Jejak sah tempat bukti butir brief divalidasi (FR-1.3): narasi wawancara + narasi chat, id jawaban/pesan tenant ini. */
    private fun evidenceContext(
        draft: DiscoveryDraft, narrative: String, history: List<ChatMessage>, thread: List<ChatMessage>
    ): EvidenceContext = EvidenceContext(
        narratives = buildSet {
            draft.interview?.narrative?.takeIf { it.isNotBlank() }?.let { add(it) }
            if (narrative.isNotBlank()) add(narrative)
        },
        interviewAnswerIds = draft.interview?.answers.orEmpty().map { it.questionId }.toSet(),
        builderAnswerIds = thread.flatMap { it.questions }.filterNot { it.answer.isNullOrBlank() }.map { it.id }.toSet(),
        userMessageIds = history.filter { it.role == ChatRole.USER }.map { it.id.value }.toSet(),
    )

    /** Dua percobaan: yang kedua membawa galat berpath. `null` = model gagal total atau keluaran tak sah → fallback. */
    private suspend fun fromModel(request: (String?) -> ModuleBriefRequest, evidence: EvidenceContext): ModuleBrief? {
        var feedback: String? = null
        repeat(MAX_ATTEMPTS) {
            val result = try {
                briefer.brief(request(feedback))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                Result.failure(e)
            }
            val brief = result.getOrNull()?.copy(source = BriefSource.AI)
            if (brief == null) {
                feedback = "model gagal: ${result.exceptionOrNull()?.message ?: "tanpa jawaban"}"
            } else {
                val issues = validator.validate(brief, evidence)
                if (issues.isEmpty()) return brief
                feedback = issues.joinToString("; ") { "${it.path}: ${it.message}" }
            }
        }
        return null
    }

    /**
     * Pertanyaan AI bila ada (maks [ModuleBriefLimits.FOLLOW_UPS_MAX]); hanya bila AI tidak punya pertanyaan, celah template
     * mengisi (fallback). Tidak dicampur: pertanyaan AI menggantikan template di `pendingFollowUps`, jadi campuran percuma.
     * Dedup terhadap yang sudah ada di utas (dijawab atau belum).
     */
    private fun followUps(
        draft: DiscoveryDraft, moduleId: String, brief: ModuleBrief, thread: List<ChatMessage>, asked: List<Clarification>
    ): List<Clarification> {
        val seen = asked.map { BriefKeys.normalize(it.question) }.toMutableSet()
        val out = mutableListOf<Clarification>()
        fun take(c: Clarification) {
            if (out.size < ModuleBriefLimits.FOLLOW_UPS_MAX && seen.add(BriefKeys.normalize(c.question))) out += c
        }
        brief.followUps.forEach { take(Clarification(BriefKeys.followUpId(moduleId, it), it.trim())) }
        if (out.isNotEmpty()) return out
        ModuleGapAnalyzer.unasked(ModuleGapAnalyzer.analyze(draft, moduleId), thread).forEach { take(Clarification(it.code, it.question)) }
        return out
    }

    companion object {
        const val MAX_CONCURRENCY = 3
        const val MAX_ATTEMPTS = 2
        private const val ID_SUFFIX = 12
    }
}

package com.eventverse.app.domain.builder

import com.eventverse.app.domain.discovery.DiscoveryDraft
import com.eventverse.app.domain.discovery.interview.Clarification
import com.eventverse.app.domain.discovery.DiscoveryDraftId
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Instant
import kotlin.jvm.JvmInline

/** Peran pesan chat Builder. Status sistem — milik platform, bukan kosakata vertikal (Uji Variabilitas). */
enum class ChatRole { USER, AGENT, SYSTEM }

/**
 * Jenis pesan. Mekanik chat milik platform (bukan kosakata industri): [TEXT] biasa, [QUESTION] = pesan agent yang
 * membawa pertanyaan follow-up ([ChatMessage.questions]) yang menunggu jawaban pengguna, [BRIEF] = ringkasan satu modul
 * (TRD-BLDR-001): teks ringkasan + [ChatMessage.features] + [ChatMessage.stages], selalu di utas modul.
 */
enum class ChatMessageKind { TEXT, QUESTION, BRIEF }

@JvmInline
value class BuilderConversationId(val value: String) {
    init { require(value.isNotBlank()) { "BuilderConversationId kosong" } }
}

@JvmInline
value class ChatMessageId(val value: String) {
    init { require(value.isNotBlank()) { "ChatMessageId kosong" } }
}

/** Satu sesi chat per tenant (PLAN-builder-console §4): satu tenant, satu percakapan berjalan. */
data class BuilderConversation(
    val id: BuilderConversationId,
    val tenantId: TenantId,
    val createdAt: Instant? = null
)

/**
 * Satu pesan chat. [proposedDraftJson] hanya ada pada pesan AGENT yang mengusulkan patch — dokumen
 * `DiscoveryDraft` penuh (JSON), **belum** diterapkan. [appliedDraftId] terisi bila user menekan
 * "Terapkan" (aksi manusia; agent tidak pernah menulis draf — plan §4).
 */
data class ChatMessage(
    val id: ChatMessageId,
    val conversationId: BuilderConversationId,
    val tenantId: TenantId,
    val role: ChatRole,
    val text: String,
    val proposedDraftJson: String? = null,
    val proposedSummary: List<String> = emptyList(),
    val appliedDraftId: String? = null,
    val createdAt: Instant? = null,
    /**
     * Utas pesan: `null` = utas **Semua** (alur penuh, tanpa filter modul); terisi = kode modul pack — pesan hanya
     * tampil di utas modul itu. Data (kode modul pack), bukan enum.
     */
    val moduleId: String? = null,
    val kind: ChatMessageKind = ChatMessageKind.TEXT,
    /** Pertanyaan follow-up (hanya [ChatMessageKind.QUESTION]); [Clarification.answer] null = belum dijawab. */
    val questions: List<Clarification> = emptyList(),
    /** Butir fitur pelengkap modul (hanya [ChatMessageKind.BRIEF]). Bukan kotak flow — lihat [stages]. */
    val features: List<String> = emptyList(),
    /** Sketsa Tahap berurutan satu dokumen di modul (hanya BRIEF); deskriptif, bukan `TenantStageFlow`. */
    val stages: List<StageSketch> = emptyList(),
    /** Jejak basis brief (hash narasi + fitur tautan + versi agent) untuk idempotensi; hanya BRIEF. */
    val basisRef: String? = null,
    /** Asal brief (AI atau fallback deterministik) supaya terlihat di UI; hanya BRIEF. */
    val briefSource: BriefSource? = null
) {
    init {
        require(text.isNotBlank()) { "Teks pesan kosong" }
        require(kind == ChatMessageKind.QUESTION || questions.isEmpty()) { "Pertanyaan follow-up hanya untuk pesan QUESTION" }
        require(kind != ChatMessageKind.QUESTION || (role == ChatRole.AGENT && questions.isNotEmpty())) {
            "Pesan QUESTION wajib dari AGENT dan membawa minimal satu pertanyaan"
        }
        if (kind == ChatMessageKind.BRIEF) {
            require(role == ChatRole.AGENT) { "Pesan BRIEF wajib dari AGENT" }
            require(moduleId != null) { "Pesan BRIEF wajib berutas modul" }
            require(briefSource != null) { "Pesan BRIEF wajib menyatakan briefSource" }
            require(!basisRef.isNullOrBlank()) { "Pesan BRIEF wajib membawa basisRef" }
        } else {
            require(features.isEmpty() && stages.isEmpty() && basisRef == null && briefSource == null) {
                "features/stages/basisRef/briefSource hanya untuk pesan BRIEF"
            }
        }
    }

    val hasPendingPatch: Boolean get() = proposedDraftJson != null && appliedDraftId == null
}

/** Balasan agent: teks + patch usulan (belum divalidasi — validator yang menilai, bukan agent). */
data class BuilderAgentReply(
    val text: String,
    val proposedDraft: DiscoveryDraft? = null,
    val summary: List<String> = emptyList()
)

/**
 * Port agent Builder (M1, FR-M1-2): mengusulkan patch atas draf kerja tenant. Di belakang interface
 * yang sama: implementasi Koog (LLM) dan fallback deterministik — pola `DiscoveryAgent`.
 * Keluaran **tidak tepercaya**: validator yang menilai, route yang menyimpan.
 */
interface BuilderAgent {
    /** `'<agent>/<versi>'` — jejak audit, pola `translatorRef`. */
    val agentRef: String

    suspend fun proposePatch(
        currentDraft: DiscoveryDraft?,
        history: List<ChatMessage>,
        userMessage: String
    ): Result<BuilderAgentReply>
}

/** Penyimpanan percakapan & pesan (schema `builder`, V82, RLS per tenant). Aturan status di use case. */
interface BuilderChatRepository {
    /** Percakapan tenant, dibuat bila belum ada. */
    suspend fun conversationFor(tenantId: TenantId): BuilderConversation

    /** Pesan percakapan, terlama dulu. Pesan tenant lain tidak pernah keluar lewat sini. */
    suspend fun messages(conversationId: BuilderConversationId): List<ChatMessage>

    suspend fun append(message: ChatMessage): ChatMessage

    /**
     * Mengisi jawaban pertanyaan follow-up pada pesan QUESTION ([answers]: id pertanyaan → jawaban). Pertanyaan yang
     * sudah berjawaban tidak ditimpa. `null` bila pesan tidak ada.
     */
    suspend fun markAnswered(messageId: ChatMessageId, answers: Map<String, String>): ChatMessage?

    /** Menandai pesan patch sudah diterapkan ke draf [draftId]. */
    suspend fun markApplied(messageId: ChatMessageId, draftId: DiscoveryDraftId): ChatMessage?
}

class ChatMessageNotFoundException(message: String) : IllegalStateException(message)

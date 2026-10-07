package com.eventverse.app.domain.builder

import com.eventverse.app.domain.auth.UserId
import com.eventverse.app.domain.discovery.DiscoveryDraftRepository
import com.eventverse.app.domain.discovery.DiscoveryDraftValidator
import com.eventverse.app.domain.discovery.interview.Clarification
import com.eventverse.app.domain.discovery.proposal.ProposalEdit
import com.eventverse.app.domain.discovery.proposal.ProposalSource
import com.eventverse.app.domain.discovery.proposal.ScreenProposal
import com.eventverse.app.domain.discovery.proposal.applyEdits
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Clock

/** Permintaan sunting isian satu modul untuk [ModuleEditor]. [feedback] terisi pada percobaan ulang (galat sunting sebelumnya). */
data class ModuleEditRequest(
    val moduleId: String,
    val moduleName: String,
    val proposal: ScreenProposal,
    val message: String,
    /** Pertanyaan follow-up yang dijawab oleh [message] (pertanyaan → jawaban), bila ada. */
    val answered: List<Pair<String, String>> = emptyList(),
    val feedback: String? = null
)

data class ModuleEditReply(val text: String, val edits: List<ProposalEdit>)

/**
 * Penafsir permintaan pengguna per modul menjadi [ProposalEdit] (model kecil di server). Hanya **mengusulkan**:
 * hasilnya diterapkan dan divalidasi oleh [EditModuleFromChat]; model tidak pernah menulis draf.
 */
fun interface ModuleEditor {
    suspend fun edit(request: ModuleEditRequest): Result<ModuleEditReply>
}

/**
 * Giliran chat di utas **modul** (Fase C): pesan pengguna (atau jawabannya atas follow-up) ditafsirkan menjadi sunting
 * isian layar modul itu, diterapkan ke salinan draf, divalidasi penuh, lalu diajukan sebagai **patch usulan** — manusia
 * yang menekan Terapkan. Gagal menerapkan = satu percobaan ulang dengan galat berpath; tetap gagal = balasan jujur tanpa patch.
 *
 * Mengembalikan `false` bila giliran ini bukan urusannya (tidak ada draf atau modul tanpa layar berproposal) sehingga
 * pemanggil memakai jalur agent penyusun draf.
 */
class EditModuleFromChat(
    private val chats: BuilderChatRepository,
    private val drafts: DiscoveryDraftRepository,
    private val editor: ModuleEditor,
    private val clock: Clock = Clock.System
) {
    suspend fun handle(
        tenantId: TenantId,
        conversation: BuilderConversation,
        moduleId: String,
        message: String,
        answered: List<Pair<String, String>>,
        onProgress: suspend (String) -> Unit
    ): Boolean {
        val draft = drafts.findByTenant(tenantId)?.draft ?: return false
        val index = draft.screens.indexOfFirst { it.moduleId.value == moduleId && it.proposal != null }
        if (index < 0) return false
        val screen = draft.screens[index]
        val proposal = requireNotNull(screen.proposal)
        val moduleName = draft.pack.modules.firstOrNull { it.id.value == moduleId }?.displayName ?: moduleId
        val moduleIds = draft.pack.modules.map { it.id.value }.toSet()
        onProgress("editing")

        var feedback: String? = null
        var lastError = "tidak ada usulan"
        repeat(MAX_ATTEMPTS) {
            val reply = editor.edit(ModuleEditRequest(moduleId, moduleName, proposal, message, answered, feedback)).getOrElse { e ->
                return reply(tenantId, conversation, moduleId, "Belum bisa memproses permintaan itu: ${e.message ?: "model tidak menjawab"}")
            }
            if (reply.edits.isEmpty()) return reply(tenantId, conversation, moduleId, reply.text)
            val edited = proposal.applyEdits(reply.edits, moduleIds).getOrElse { e ->
                lastError = e.message ?: "sunting tidak sah"; feedback = lastError; null
            }
            if (edited != null) {
                val screens = draft.screens.toMutableList().also {
                    it[index] = screen.copy(proposal = edited, source = ProposalSource.Agent(AGENT_REF))
                }
                val next = draft.copy(screens = screens)
                val issues = DiscoveryDraftValidator.validate(next)
                if (issues.isEmpty()) {
                    chats.append(
                        ChatMessage(
                            id = ChatMessageId("msg-${conversation.id.value}-a-${clock.now().epochSeconds}"),
                            conversationId = conversation.id, tenantId = tenantId, role = ChatRole.AGENT,
                            text = reply.text.ifBlank { "Saya mengusulkan ${reply.edits.size} perubahan isian $moduleName. Tekan Terapkan bila sesuai." },
                            proposedDraftJson = com.eventverse.app.shared.discovery.DiscoveryDraftCodec.encodeToString(next),
                            proposedSummary = reply.edits.map(::describe),
                            createdAt = clock.now(), moduleId = moduleId
                        )
                    )
                    return true
                }
                lastError = issues.joinToString("; ") { "${it.path}: ${it.message}" }; feedback = lastError
            }
        }
        return reply(tenantId, conversation, moduleId, "Belum bisa menerapkan perubahan itu ($lastError). Coba jelaskan dengan cara lain.")
    }

    private suspend fun reply(tenantId: TenantId, conversation: BuilderConversation, moduleId: String, text: String): Boolean {
        chats.append(
            ChatMessage(
                id = ChatMessageId("msg-${conversation.id.value}-a-${clock.now().epochSeconds}"),
                conversationId = conversation.id, tenantId = tenantId, role = ChatRole.AGENT, text = text.ifBlank { "Tidak ada yang perlu diubah." },
                createdAt = clock.now(), moduleId = moduleId
            )
        )
        return true
    }

    private fun describe(e: ProposalEdit): String = when (e) {
        is ProposalEdit.AddField -> "Tambah isian: ${e.field.label} (${e.field.type.name.lowercase()}${if (e.field.required) ", wajib" else ""})"
        is ProposalEdit.RemoveField -> "Hapus isian: ${e.key}"
        is ProposalEdit.ReplaceField -> "Ganti isian: ${e.key} → ${e.field.label} (${e.field.type.name.lowercase()}${if (e.field.required) ", wajib" else ""})"
    }

    companion object {
        const val MAX_ATTEMPTS = 2
        const val AGENT_REF = "builder-module-edit/v1"
    }
}

/**
 * Mengajukan follow-up per modul saat tab modul dibuka (Fase C): celah dihitung dari data ([ModuleGapAnalyzer]),
 * celah yang sudah pernah ditanyakan dilewati, dan tidak ada pertanyaan baru selama masih ada yang menunggu jawaban.
 * Idempoten — memanggilnya berulang tidak menggandakan pesan. Mengembalikan `true` bila pesan pertanyaan dibuat.
 */
class AskModuleFollowUpsUseCase(
    private val chats: BuilderChatRepository,
    private val drafts: DiscoveryDraftRepository,
    private val clock: Clock = Clock.System
) {
    suspend operator fun invoke(tenantId: TenantId, moduleId: String): Result<Boolean> = runCatching {
        val draft = drafts.findByTenant(tenantId)?.draft ?: return@runCatching false
        val conversation = chats.conversationFor(tenantId)
        val thread = chats.messages(conversation.id).inThread(moduleId)
        if (thread.pendingFollowUps(moduleId).isNotEmpty()) return@runCatching false
        val gaps = ModuleGapAnalyzer.unasked(ModuleGapAnalyzer.analyze(draft, moduleId), thread)
        if (gaps.isEmpty()) return@runCatching false
        val name = draft.pack.modules.firstOrNull { it.id.value == moduleId }?.displayName ?: moduleId
        chats.append(
            ChatMessage(
                id = ChatMessageId("msg-${conversation.id.value}-a-${clock.now().epochSeconds}"),
                conversationId = conversation.id, tenantId = tenantId, role = ChatRole.AGENT,
                text = "Beberapa hal tentang $name belum jelas dari ceritamu. Jawab seperlunya, atau langsung minta ubah isiannya:",
                createdAt = clock.now(), moduleId = moduleId, kind = ChatMessageKind.QUESTION,
                questions = gaps.map { Clarification(it.code, it.question) }
            )
        )
        true
    }
}

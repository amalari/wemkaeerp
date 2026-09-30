package com.eventverse.app.infrastructure.builder

import com.eventverse.app.domain.builder.BuilderAgentReply
import com.eventverse.app.domain.builder.BuilderChatRepository
import com.eventverse.app.domain.builder.ChatMessage
import com.eventverse.app.domain.discovery.DiscoveryAgent
import com.eventverse.app.domain.discovery.DiscoveryRequest
import kotlinx.datetime.Clock

/**
 * Adapter BuilderAgent (M1): di belakang [DiscoveryAgent] yang sudah teruji — LLM Koog
 * (`DiscoveryAgents.from(...)`, loop koreksi diri + fallback deterministik) atau deterministik murni.
 *
 * Cara mengusulkan patch: pesan user diperlakukan sebagai narasi discovery versi-N (draf dikerjakan
 * ulang dari nol oleh agent, lalu dicatat sebagai usulan). Validator yang menilai saat "Terapkan" —
 * kalau draf versi-N identik platform (garment), validator menolaknya dan itu *memang perilaku yang
 * benar*: draf shipped wajib lewat `useShipped`, bukan ditulis ulang.
 */
class DiscoveryBackedBuilderAgent(
    private val discovery: DiscoveryAgent,
    private val clock: Clock = Clock.System
) : com.eventverse.app.domain.builder.BuilderAgent {

    override val agentRef: String = "builder/${discovery.agentRef}"

    override suspend fun proposePatch(
        currentDraft: com.eventverse.app.domain.discovery.DiscoveryDraft?,
        history: List<ChatMessage>,
        userMessage: String
    ): Result<BuilderAgentReply> = discovery.draft(DiscoveryRequest(narrative = userMessage)).map { proposed ->
        BuilderAgentReply(
            text = replyText(currentDraft, proposed),
            proposedDraft = proposed,
            summary = diffSummary(currentDraft, proposed)
        )
    }

    private fun replyText(current: com.eventverse.app.domain.discovery.DiscoveryDraft?, proposed: com.eventverse.app.domain.discovery.DiscoveryDraft?): String {
        if (proposed == null) return "Saya belum bisa menyusun usulan draf. Coba jelaskan lagi."
        val changes = diffSummary(current, proposed)
        return if (changes.isEmpty()) "Draf tidak berubah — susunan modul sudah sesuai."
        else "Saya mengusulkan revisi draf (" + changes.size + " perubahan). Tekan Terapkan bila sesuai."
    }

    private fun diffSummary(current: com.eventverse.app.domain.discovery.DiscoveryDraft?, proposed: com.eventverse.app.domain.discovery.DiscoveryDraft?): List<String> {
        if (proposed == null) return emptyList()
        val currentModules = current?.blueprint?.activeModuleCodes ?: emptySet()
        val proposedModules = proposed.blueprint.activeModuleCodes
        val summary = mutableListOf<String>()
        (proposedModules - currentModules).forEach { summary += "Modul aktif baru: $it" }
        (currentModules - proposedModules).forEach { summary += "Modul dinonaktifkan: $it" }
        return summary
    }
}

package com.eventverse.app.infrastructure.help

import ai.koog.prompt.executor.clients.deepseek.DeepSeekLLMClient
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import com.eventverse.app.domain.help.DeterministicHelpAgent
import com.eventverse.app.domain.help.HelpAgent
import com.eventverse.app.infrastructure.EnvLoader
import com.eventverse.app.infrastructure.discovery.DiscoveryAgents
import org.slf4j.LoggerFactory

/**
 * Pemilihan AI helper — kill-switch, pola yang sama dengan [DiscoveryAgents]:
 *
 * | `HELP_AGENT` | `DEEPSEEK_API_KEY` | Hasil |
 * |---|---|---|
 * | `koog` | terisi | [KoogHelpAgent] (LLM); gagal → deterministik lewat `AskHelpUseCase` |
 * | `koog` | kosong | deterministik + peringatan |
 * | lain / kosong | — | deterministik (default) |
 *
 * `HELP_AGENT_MODEL` memilih id model (default sama dengan discovery). Kunci API dipakai bersama discovery.
 */
object HelpAgents {
    const val KOOG = "koog"

    fun fromEnv(): HelpAgent = from(
        configured = EnvLoader.get("HELP_AGENT").takeIf { it.isNotBlank() },
        apiKey = EnvLoader.get("DEEPSEEK_API_KEY").takeIf { it.isNotBlank() },
        modelId = EnvLoader.get("HELP_AGENT_MODEL").takeIf { it.isNotBlank() },
    )

    fun from(configured: String?, apiKey: String?, modelId: String? = null): HelpAgent {
        if (configured?.lowercase() != KOOG) return DeterministicHelpAgent()
        if (apiKey.isNullOrBlank()) {
            logger.warn("HELP_AGENT=koog tetapi DEEPSEEK_API_KEY kosong — memakai AI helper deterministik")
            return DeterministicHelpAgent()
        }
        val model = DiscoveryAgents.resolveModel(modelId)
        logger.info("AI helper aktif: {} (model {})", KOOG, model.id)
        return KoogHelpAgent(MultiLLMPromptExecutor(DeepSeekLLMClient(apiKey)), model)
    }

    private val logger = LoggerFactory.getLogger(HelpAgents::class.java)
}

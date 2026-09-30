package com.eventverse.app.infrastructure.crm.prefill

import ai.koog.prompt.executor.clients.deepseek.DeepSeekLLMClient
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import com.eventverse.app.domain.crm.prefill.DeterministicLeadDraftExtractor
import com.eventverse.app.domain.crm.prefill.LeadDraftExtractor
import com.eventverse.app.infrastructure.EnvLoader
import com.eventverse.app.infrastructure.discovery.DiscoveryAgents
import org.slf4j.LoggerFactory

/**
 * Kill-switch ekstraktor draf lead (pola `HelpAgents`): `LEAD_DRAFT_AGENT=koog` + `DEEPSEEK_API_KEY` → LLM;
 * selain itu deterministik. Ini saklar **platform**; setiap tenant tetap harus opt-in (`crm_ai_settings`).
 */
object LeadDraftAgents {
    fun fromEnv(): LeadDraftExtractor = from(
        configured = EnvLoader.get("LEAD_DRAFT_AGENT").takeIf { it.isNotBlank() },
        apiKey = EnvLoader.get("DEEPSEEK_API_KEY").takeIf { it.isNotBlank() },
        modelId = EnvLoader.get("LEAD_DRAFT_AGENT_MODEL").takeIf { it.isNotBlank() },
    )

    fun from(configured: String?, apiKey: String?, modelId: String? = null): LeadDraftExtractor {
        if (configured?.lowercase() != "koog") return DeterministicLeadDraftExtractor()
        if (apiKey.isNullOrBlank()) {
            logger.warn("LEAD_DRAFT_AGENT=koog tetapi DEEPSEEK_API_KEY kosong — memakai ekstraksi deterministik")
            return DeterministicLeadDraftExtractor()
        }
        val model = DiscoveryAgents.resolveModel(modelId)
        logger.info("Draf lead AI aktif: koog (model {})", model.id)
        return KoogLeadDraftExtractor(MultiLLMPromptExecutor(DeepSeekLLMClient(apiKey)), model)
    }

    private val logger = LoggerFactory.getLogger(LeadDraftAgents::class.java)
}

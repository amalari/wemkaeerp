package com.eventverse.app.infrastructure.discovery

import ai.koog.prompt.executor.clients.deepseek.DeepSeekLLMClient
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import com.eventverse.app.domain.discovery.DiscoveryDraft
import com.eventverse.app.domain.discovery.interview.InterviewSession
import com.eventverse.app.domain.discovery.interview.InterviewStep
import com.eventverse.app.domain.discovery.interview.InterviewStepFiller
import com.eventverse.app.domain.discovery.interview.mergingGuessesOf
import com.eventverse.app.infrastructure.EnvLoader
import kotlinx.coroutines.withTimeout
import org.slf4j.LoggerFactory

/**
 * Pengisi langkah wawancara berbasis agent Koog ([AgentInterviewGuesser]). Dipanggil **hanya saat langkah G1–G4
 * mulai ditanyakan**; usulan agent **digabung** dengan tebakan deterministik langkah itu (hybrid: kamus tetap, agent mengisi celah; kunci sama → agent menang; butir milik pengguna tetap),
 * butir yang menggantung dipangkas, dan pemanggil memvalidasi hasilnya. Galat atau timeout → [fill] melempar dan
 * use case mempertahankan tebakan deterministik: wawancara tidak pernah gagal karena AI.
 */
class AgentStepFiller(
    private val guesser: AgentInterviewGuesser,
    private val timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS
) : InterviewStepFiller {

    override suspend fun fill(draft: DiscoveryDraft, session: InterviewSession, step: InterviewStep, narrative: String): InterviewSession {
        val guesses = withTimeout(timeoutMillis) { guesser.guess(step, draft.pack, draft, narrative).getOrThrow() }
        return session.mergingGuessesOf(step, guesses.divisions, guesses.roles, guesses.links, guesses.handoffs)
    }

    companion object {
        /** Target awal per giliran (plan C6: < ~15–20 detik); dikalibrasi dari eval live. */
        const val DEFAULT_TIMEOUT_MILLIS = 20_000L
    }
}

/**
 * Pemilihan agent wawancara — **kill-switch terpisah** dari agent discovery supaya biaya wawancara dikendalikan
 * sendiri: `INTERVIEW_AGENT=koog` **dan** `DEEPSEEK_API_KEY` terisi → [AgentStepFiller]; selain itu `null`
 * (hanya tebakan deterministik). Kunci kosong tidak menggagalkan start server. `INTERVIEW_AGENT_TIMEOUT_MS`
 * mengatur batas waktu per giliran; `DISCOVERY_AGENT_MODEL` dipakai bersama agar satu pengaturan model.
 */
object InterviewAgents {

    fun fromEnv(): InterviewStepFiller? = from(
        configured = EnvLoader.get("INTERVIEW_AGENT").takeIf { it.isNotBlank() },
        apiKey = EnvLoader.get("DEEPSEEK_API_KEY").takeIf { it.isNotBlank() },
        modelId = EnvLoader.get("DISCOVERY_AGENT_MODEL").takeIf { it.isNotBlank() },
        timeoutMillis = EnvLoader.get("INTERVIEW_AGENT_TIMEOUT_MS").toLongOrNull()?.takeIf { it > 0 } ?: AgentStepFiller.DEFAULT_TIMEOUT_MILLIS
    )

    fun from(configured: String?, apiKey: String?, modelId: String? = null, timeoutMillis: Long = AgentStepFiller.DEFAULT_TIMEOUT_MILLIS): InterviewStepFiller? {
        if (configured?.lowercase() != DiscoveryAgents.KOOG) return null
        if (apiKey.isNullOrBlank()) {
            logger.warn("INTERVIEW_AGENT=koog tetapi DEEPSEEK_API_KEY kosong — wawancara memakai tebakan deterministik")
            return null
        }
        val model = DiscoveryAgents.resolveModel(modelId)
        logger.info("Agent wawancara aktif: koog (model {}), timeout {} ms", model.id, timeoutMillis)
        return AgentStepFiller(AgentInterviewGuesser(MultiLLMPromptExecutor(DeepSeekLLMClient(apiKey)), model), timeoutMillis)
    }

    private val logger = LoggerFactory.getLogger(InterviewAgents::class.java)
}

package com.eventverse.app.infrastructure.help

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.singleRunStrategy
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.utils.io.use
import com.eventverse.app.domain.help.DeterministicHelpAgent
import com.eventverse.app.domain.help.HelpAgent
import com.eventverse.app.domain.help.HelpAnswer
import com.eventverse.app.domain.help.HelpQuestion
import com.eventverse.app.domain.tutorial.TutorialId
import com.eventverse.app.infrastructure.discovery.DiscoveryAgents
import com.eventverse.app.infrastructure.discovery.extractJsonObject
import com.eventverse.app.shared.json.JsonParser
import com.eventverse.app.shared.json.JsonValue

/**
 * AI helper LLM (TRD-HELP-001 Fase 3). Satu percakapan Koog tanpa alat per putaran; jawaban yang tidak sah
 * (bukan JSON, id di luar kandidat, langkah di luar rentang) dikirim kembali sekali sebagai umpan balik.
 *
 * Tidak memasang fallback sendiri: kegagalan dikembalikan sebagai `Result` gagal dan `AskHelpUseCase` yang
 * menggantinya dengan jawaban deterministik — satu tempat untuk aturan "jawaban harus berpijak pada kandidat".
 * Tanpa kandidat, LLM tidak dipanggil sama sekali (tidak ada yang bisa dijawab dengan jujur, dan tidak ada biaya).
 */
class KoogHelpAgent(
    private val executor: PromptExecutor,
    private val model: LLModel = DiscoveryAgents.defaultModel,
    private val maxRounds: Int = DEFAULT_MAX_ROUNDS,
) : HelpAgent {

    init { require(maxRounds > 0) { "maxRounds harus positif" } }

    override val agentRef: String = "koog/${model.id}/help-v1"

    override suspend fun answer(question: HelpQuestion): Result<HelpAnswer> = runCatching {
        if (question.candidates.isEmpty()) {
            return@runCatching HelpAnswer(DeterministicHelpAgent.NO_MATCH, null, null, agentRef)
        }
        var feedback: String? = null
        repeat(maxRounds) {
            val raw = ask(question, feedback)
            val parsed = runCatching { parse(raw) }
            val problem = parsed.fold({ problemWith(it, question) }, { it.message ?: "jawaban tidak bisa dibaca" })
            if (problem == null) return@runCatching parsed.getOrThrow()
            feedback = problem
        }
        error("Agent $agentRef gagal memberi jawaban sah setelah $maxRounds putaran: $feedback")
    }

    private suspend fun ask(question: HelpQuestion, feedback: String?): String {
        val agent = AIAgent(
            promptExecutor = executor,
            llmModel = model,
            strategy = singleRunStrategy(),
            systemPrompt = KoogHelpPrompt.system,
            temperature = TEMPERATURE,
            maxIterations = MAX_ITERATIONS,
        )
        return agent.use { it.run(KoogHelpPrompt.userMessage(question, feedback)) }
    }

    private fun parse(raw: String): HelpAnswer {
        val o = JsonParser.parseObject(extractJsonObject(raw))
        val text = o.string("answer")?.trim().orEmpty()
        require(text.isNotEmpty()) { "field 'answer' kosong" }
        val id = (o["tutorialId"] as? JsonValue.Str)?.value?.takeIf { it.isNotBlank() && it != "null" }
        return HelpAnswer(text, id?.let(::TutorialId), o.int("stepIndex"), agentRef)
    }

    private fun problemWith(answer: HelpAnswer, question: HelpQuestion): String? {
        val id = answer.tutorialId ?: return null
        val match = question.candidates.firstOrNull { it.tutorial.id == id }
            ?: return "tutorialId '${id.value}' tidak ada di daftar kandidat"
        val step = answer.stepIndex ?: return null
        return if (step in match.tutorial.steps.indices) null else "stepIndex $step di luar rentang 0..${match.tutorial.steps.lastIndex}"
    }

    companion object {
        /** Dua putaran: satu jawaban + satu koreksi. Lebih dari itu membuat user menunggu untuk jawaban bantuan. */
        const val DEFAULT_MAX_ROUNDS = 2
        private const val MAX_ITERATIONS = 4
        private const val TEMPERATURE = 0.2
    }
}

package com.eventverse.app.infrastructure.discovery

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.Prompt
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.utils.time.KoogClock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * `PromptExecutor` palsu untuk A8: menjawab dari skrip, merekam prompt & daftar alat.
 *
 * Test memakai ini supaya perilaku agent Koog (loop koreksi, umpan balik berpath, jembatan pack bawaan)
 * diuji **tanpa jaringan, tanpa kunci API, tanpa biaya** — tetapi tetap melewati seluruh jalur produksi:
 * `singleRunStrategy`, registry alat, dan parser draf yang sama dengan produksi. Jawaban LLM adalah
 * satu-satunya bagian yang diganti.
 */
internal class ScriptedPromptExecutor(private val answers: List<String>) : PromptExecutor() {

    val prompts = mutableListOf<Prompt>()
    val toolsSeen = mutableListOf<List<ToolDescriptor>>()

    val calls: Int get() = prompts.size

    /** Seluruh teks prompt panggilan terakhir — untuk memeriksa umpan balik yang dikirim ke model. */
    fun lastPromptText(): String = prompts.last().messages.joinToString("\n") { it.textContent() }

    override suspend fun execute(prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>): Message.Assistant {
        val answer = answers.getOrNull(calls)
            ?: error("Skrip habis: panggilan LLM ke-${calls + 1} tidak punya jawaban")
        prompts += prompt
        toolsSeen += tools
        return Message.Assistant(answer, ResponseMetaInfo.create(KoogClock.System))
    }

    override fun executeStreaming(prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>): Flow<StreamFrame> =
        emptyFlow()

    override suspend fun moderate(prompt: Prompt, model: LLModel): ModerationResult =
        ModerationResult(isHarmful = false, categories = emptyMap())

    override fun close() = Unit
}

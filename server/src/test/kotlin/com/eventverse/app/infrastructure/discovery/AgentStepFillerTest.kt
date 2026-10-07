package com.eventverse.app.infrastructure.discovery

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.Prompt
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.executor.clients.deepseek.DeepSeekModels
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.utils.time.KoogClock
import com.eventverse.app.InterviewEvalPacks
import com.eventverse.app.domain.discovery.interview.Basis
import com.eventverse.app.domain.discovery.interview.BasisRef
import com.eventverse.app.domain.discovery.interview.DivisionCode
import com.eventverse.app.domain.discovery.interview.DivisionDraft
import com.eventverse.app.domain.discovery.interview.InterviewSession
import com.eventverse.app.domain.discovery.interview.InterviewStep
import com.eventverse.app.domain.discovery.interview.InterviewValidator
import com.eventverse.app.domain.discovery.interview.ItemSource
import com.eventverse.app.domain.discovery.interview.RoleDraft
import com.eventverse.app.domain.discovery.interview.RoleKey
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Pengisi langkah berbasis agent, **tanpa jaringan dan tanpa biaya** (`ScriptedPromptExecutor`). */
class AgentStepFillerTest {

    private val model = DeepSeekModels.DeepSeekV4Flash
    private val pack = InterviewEvalPacks.klinikPack
    private val narasi = "Kami klinik gigi: pasien mendaftar antrean per poli, dan tagihan pembayaran kasir."

    private val quoted = BasisRef(Basis.NARASI, quote = "pasien mendaftar")

    /** Sesi dasar = tebakan deterministik yang akan diganti: satu divisi tebakan dan satu peran yang menempel padanya. */
    private val baseline = InterviewSession(
        step = InterviewStep.G1_DIVISI,
        divisions = listOf(DivisionDraft(DivisionCode("pendaftaran"), "Pendaftaran", ItemSource.GUESS, quoted)),
        roles = listOf(RoleDraft(RoleKey("resepsionis"), "Resepsionis", DivisionCode("pendaftaran"), ItemSource.GUESS, isHead = true, basisRef = quoted)),
        version = InterviewSession.BASED_ON_STORY,
        narrative = narasi
    )
    private val draft = InterviewEvalPacks.draftOf(pack, baseline)

    private fun g1Answer(code: String, name: String) = """
        {"interview":{"step":"g1_divisi","divisions":[{"code":"$code","name":"$name","source":"guess",
        "basisRef":{"basis":"narasi","quote":"antrean per poli"}}],"roles":[],"links":[],"handoffs":[],"answers":[]}}
    """.trimIndent()

    @Test
    fun `usulan agent menggantikan tebakan G1, peran yang menggantung dipangkas, hasil lolos validator`() = runBlocking {
        val executor = ScriptedPromptExecutor(listOf(g1Answer("antrean_poli", "Antrean Poli")))
        val filler = AgentStepFiller(AgentInterviewGuesser(executor, model = model))
        val out = filler.fill(draft, baseline, InterviewStep.G1_DIVISI, narasi)
        assertEquals(listOf("antrean_poli"), out.divisions.map { it.code.value })
        assertTrue(out.roles.isEmpty(), "peran tebakan lama menunjuk divisi yang sudah diganti ⇒ dipangkas")
        assertEquals(emptyList(), InterviewValidator.validate(out, pack))
        assertEquals(1, executor.calls)
    }

    @Test
    fun `keluaran sampah menjadi galat sehingga pemanggil mempertahankan tebakan deterministik`() = runBlocking<Unit> {
        val executor = ScriptedPromptExecutor(listOf("bukan json", "juga bukan", "tetap bukan"))
        val filler = AgentStepFiller(AgentInterviewGuesser(executor, model = model))
        assertFailsWith<IllegalStateException> { filler.fill(draft, baseline, InterviewStep.G1_DIVISI, narasi) }
    }

    @Test
    fun `agent yang terlalu lambat dibatalkan oleh timeout`() = runBlocking<Unit> {
        val slow = object : PromptExecutor() {
            override suspend fun execute(prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>): Message.Assistant {
                delay(2_000)
                return Message.Assistant(g1Answer("antrean_poli", "Antrean Poli"), ResponseMetaInfo.create(KoogClock.System))
            }
            override fun executeStreaming(prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>): Flow<StreamFrame> = emptyFlow()
            override suspend fun moderate(prompt: Prompt, model: LLModel) = ModerationResult(isHarmful = false, categories = emptyMap())
            override fun close() = Unit
        }
        val filler = AgentStepFiller(AgentInterviewGuesser(slow, model = model), timeoutMillis = 100)
        assertFailsWith<TimeoutCancellationException> { filler.fill(draft, baseline, InterviewStep.G1_DIVISI, narasi) }
    }

    @Test
    fun `saklar lingkungan, tanpa kunci atau bukan koog berarti tidak ada agent`() {
        assertEquals(null, InterviewAgents.from(configured = null, apiKey = "k"))
        assertEquals(null, InterviewAgents.from(configured = "deterministik", apiKey = "k"))
        assertEquals(null, InterviewAgents.from(configured = "koog", apiKey = null))
        assertEquals(null, InterviewAgents.from(configured = "koog", apiKey = " "))
        assertTrue(InterviewAgents.from(configured = "KOOG", apiKey = "kunci-uji") is AgentStepFiller)
    }
}

package com.eventverse.app.domain.builder

import com.eventverse.app.domain.discovery.interview.Clarification
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Kontrak TRD-BLDR-001 Track 0 + FR-2 TRD-BLDR-003: bentuk `ChatMessage` BRIEF dan port [ModuleBriefer] dibekukan di sini. */
class ModuleBriefContractTest {
    private val conv = BuilderConversationId("c1")
    private val tenant = TenantId("bordir-uji")

    private fun brief(
        role: ChatRole = ChatRole.AGENT,
        module: String? = "sampling_order",
        source: BriefSource? = BriefSource.AI,
        fingerprint: String? = "b1",
        features: List<BriefFeature> = listOf(BriefFeature("Lampirkan foto")),
        stages: List<StageSketch> = listOf(StageSketch("Intake"), StageSketch("Revisi", reworkTo = "Intake"))
    ) = ChatMessage(
        ChatMessageId("m1"), conv, tenant, role, "Ringkasan modul", moduleId = module, kind = ChatMessageKind.BRIEF,
        features = features, stages = stages, inputFingerprint = fingerprint, briefSource = source
    )

    private fun text(
        features: List<BriefFeature> = emptyList(), stages: List<StageSketch> = emptyList(), source: BriefSource? = null
    ) = ChatMessage(
        ChatMessageId("m"), conv, tenant, ChatRole.AGENT, "x", features = features, stages = stages, briefSource = source
    )

    @Test
    fun `brief message with full data should be valid`() {
        val m = brief()
        assertEquals(ChatMessageKind.BRIEF, m.kind)
        assertEquals(2, m.stages.size)
        assertEquals("Intake", m.stages[1].reworkTo)
    }

    @Test
    fun `brief message from user should be rejected`() {
        assertFailsWith<IllegalArgumentException> { brief(role = ChatRole.USER) }
    }

    @Test
    fun `brief message without module thread should be rejected`() {
        assertFailsWith<IllegalArgumentException> { brief(module = null) }
    }

    @Test
    fun `brief message without source or fingerprint should be rejected`() {
        assertFailsWith<IllegalArgumentException> { brief(source = null) }
        assertFailsWith<IllegalArgumentException> { brief(fingerprint = null) }
        assertFailsWith<IllegalArgumentException> { brief(fingerprint = " ") }
    }

    @Test
    fun `text message carrying brief fields should be rejected`() {
        assertFailsWith<IllegalArgumentException> { text(features = listOf(BriefFeature("a"))) }
        assertFailsWith<IllegalArgumentException> { text(stages = listOf(StageSketch("a"))) }
        assertFailsWith<IllegalArgumentException> { text(source = BriefSource.AI) }
    }

    @Test
    fun `brief message with questions should be rejected`() {
        assertFailsWith<IllegalArgumentException> {
            ChatMessage(
                ChatMessageId("m"), conv, tenant, ChatRole.AGENT, "x", moduleId = "m1", kind = ChatMessageKind.BRIEF,
                inputFingerprint = "b", briefSource = BriefSource.AI, questions = listOf(Clarification("q", "apa?"))
            )
        }
    }

    @Test
    fun `brief message should not count as pending follow-up or asked question`() {
        val thread = listOf(brief())
        assertTrue(thread.pendingFollowUps("sampling_order").isEmpty())
        assertTrue(ModuleGapAnalyzer.unasked(emptyList(), thread).isEmpty())
    }

    @Test
    fun `briefer port should be satisfiable by a plain lambda for non-default module`() = runTest {
        val briefer = ModuleBriefer { r -> Result.success(ModuleBrief("Ringkasan ${r.moduleName}", source = BriefSource.DETERMINISTIC)) }
        val out = briefer.brief(ModuleBriefRequest(tenant, "klinik_poli", "Poli Umum", "cerita klinik")).getOrThrow()
        assertEquals("Ringkasan Poli Umum", out.summary)
        assertEquals(BriefSource.DETERMINISTIC, out.source)
    }

    @Test
    fun `limits should match TRD-BLDR-001 FR-4`() {
        assertEquals(600, ModuleBriefLimits.SUMMARY_MAX_CHARS)
        assertEquals(7, ModuleBriefLimits.STAGES_MAX)
        assertEquals(3, ModuleBriefLimits.FOLLOW_UPS_MAX)
        assertEquals(2, ModuleBriefLimits.CONTRACT_VERSION_EVIDENCE)
    }
}

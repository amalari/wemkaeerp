package com.eventverse.app.domain.builder

import com.eventverse.app.domain.evidence.EvidenceRef
import com.eventverse.app.domain.evidence.EvidenceSource
import com.eventverse.app.domain.discovery.scenario.ScenarioSketch
import com.eventverse.app.domain.discovery.scenario.ScenarioId
import com.eventverse.app.domain.discovery.scenario.ScenarioStep
import com.eventverse.app.domain.discovery.scenario.ScenarioSource
import com.eventverse.app.domain.pack.ModuleId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * TRD-BLDR-003 F3: Live Eval Gate Tests
 */
class LiveEvalGateF3Test {

    @Test
    fun `brief with v2 and evidence passes gate`() {
        val brief = ModuleBrief(
            summary = "Test module",
            features = listOf(
                BriefFeature("Feature 1", EvidenceRef(EvidenceSource.NARRATIVE, quote = "test"))
            ),
            stages = listOf(StageSketch("Stage 1", evidence = EvidenceRef(EvidenceSource.NARRATIVE, quote = "test"))),
            source = BriefSource.AI,
            contractVersion = 2
        )

        val result = LiveEvalGate.evaluateBrief(brief, "gpt-4", "test-case", 1000)

        assertTrue(result.passed)
        assertEquals(3, result.gateResults.size)
    }

    @Test
    fun `brief v1 passes gate (legacy)`() {
        val brief = ModuleBrief(
            summary = "Test module",
            features = listOf(BriefFeature("Feature 1", null)),
            stages = listOf(StageSketch("Stage 1")),
            source = BriefSource.AI,
            contractVersion = 1
        )

        val result = LiveEvalGate.evaluateBrief(brief, "gpt-4", "test-case", 1000)

        // v1 is always pass (legacy)
        assertTrue(result.passed)
    }

    @Test
    fun `brief v2 without evidence fails gate`() {
        val brief = ModuleBrief(
            summary = "Test module",
            features = listOf(BriefFeature("Feature 1", null)),
            stages = listOf(StageSketch(name = "Stage 1", evidence = null)),
            source = BriefSource.AI,
            contractVersion = 2
        )

        val result = LiveEvalGate.evaluateBrief(brief, "gpt-4", "test-case", 1000)

        assertFalse(result.passed)
    }

    @Test
    fun `scenario with v2 and evidence passes gate`() {
        val scenario = ScenarioSketch(
            id = ScenarioId("test"),
            title = "Test Scenario",
            steps = listOf(
                ScenarioStep(
                    moduleId = ModuleId("mrp"),
                    screenId = null,
                    label = "Step 1",
                    evidence = EvidenceRef(EvidenceSource.NARRATIVE, quote = "test")
                )
            ),
            source = ScenarioSource.AI,
            contractVersion = 2
        )

        val result = LiveEvalGate.evaluateScenario(scenario, "gpt-4", "test-case", 1000)

        assertTrue(result.passed)
    }

    @Test
    fun `scenario v1 passes gate (legacy)`() {
        val scenario = ScenarioSketch(
            id = ScenarioId("test"),
            title = "Test Scenario",
            steps = listOf(
                ScenarioStep(moduleId = ModuleId("mrp"), screenId = null, label = "Step 1")
            ),
            source = ScenarioSource.AI,
            contractVersion = 1
        )

        val result = LiveEvalGate.evaluateScenario(scenario, "gpt-4", "test-case", 1000)

        assertTrue(result.passed)
    }

    @Test
    fun `check budget returns true when sufficient`() {
        val result = LiveEvalGate.checkBudget(10.0, 1.0, 5)
        assertTrue(result)
    }

    @Test
    fun `check budget returns false when insufficient`() {
        val result = LiveEvalGate.checkBudget(3.0, 1.0, 5)
        assertFalse(result)
    }

    @Test
    fun `format log produces correct format`() {
        val evalResult = LiveEvalResult(
            passed = true,
            gateResults = listOf(GateResult("contract_v2", true, "ok")),
            scoreResults = listOf(ScoreResult("test", 1f, 1, 1)),
            model = "gpt-4",
            caseName = "test-case",
            durationMs = 1000,
            tokensUsed = 500,
            costUsd = 0.01
        )

        val log = LiveEvalGate.formatLog(evalResult)

        assertTrue(log.contains("PASS"))
        assertTrue(log.contains("gpt-4"))
        assertTrue(log.contains("test-case"))
    }
}

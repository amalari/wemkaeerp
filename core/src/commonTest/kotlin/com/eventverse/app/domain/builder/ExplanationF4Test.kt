package com.eventverse.app.domain.builder

import com.eventverse.app.domain.evidence.EvidenceRef
import com.eventverse.app.domain.evidence.EvidenceSource
import com.eventverse.app.domain.discovery.scenario.ScenarioExplanation
import com.eventverse.app.domain.discovery.scenario.ScenarioSketch
import com.eventverse.app.domain.discovery.scenario.ScenarioId
import com.eventverse.app.domain.discovery.scenario.ScenarioSource
import com.eventverse.app.domain.discovery.scenario.ScenarioStep
import com.eventverse.app.domain.discovery.scenario.AllScenariosExplanation
import com.eventverse.app.domain.pack.ModuleId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * TRD-BLDR-003 F4: Explainability UI Tests
 */
class ExplanationF4Test {

    // ========== BRIEF EXPLANATION TESTS ==========

    @Test
    fun `brief explanation - with evidence shows why chosen`() {
        val brief = ModuleBrief(
            summary = "Modul Potong: untuk memotong kain",
            features = listOf(
                BriefFeature("Spreading", EvidenceRef(EvidenceSource.NARRATIVE, quote = "kain kami beli"))
            ),
            stages = listOf(StageSketch("Potong", evidence = EvidenceRef(EvidenceSource.NARRATIVE, quote = "potong bahan"))),
            source = BriefSource.AI,
            contractVersion = 2
        )

        val explanation = ModuleBriefExplanation.fromBrief("cutting", brief, null)

        assertFalse(explanation.isLegacy)
        assertTrue(explanation.hasEvidence)
        assertEquals(1, explanation.featureExplanations.size)
        assertTrue(explanation.featureExplanations.first().explanation.contains("Dipilih karena"))
    }

    @Test
    fun `brief explanation - legacy shows not available`() {
        val brief = ModuleBrief(
            summary = "Modul Potong",
            features = listOf(BriefFeature("Spreading", null)),
            stages = listOf(StageSketch("Potong")),
            source = BriefSource.AI,
            contractVersion = 1
        )

        val explanation = ModuleBriefExplanation.fromBrief("cutting", brief, null)

        assertTrue(explanation.isLegacy)
        assertFalse(explanation.hasEvidence)
        assertTrue(explanation.featureExplanations.all { it.isLegacy })
        assertTrue(explanation.featureExplanations.first().explanation.contains("belum tersedia"))
    }

    @Test
    fun `brief explanation - v2 without evidence shows no proof`() {
        val brief = ModuleBrief(
            summary = "Modul Potong",
            features = listOf(BriefFeature("Spreading", null)), // v2 tapi tanpa evidence
            stages = listOf(StageSketch(name = "Potong", evidence = null)),
            source = BriefSource.AI,
            contractVersion = 2
        )

        val explanation = ModuleBriefExplanation.fromBrief("cutting", brief, null)

        assertFalse(explanation.isLegacy)
        assertFalse(explanation.hasEvidence)
    }

    // ========== SCENARIO EXPLANATION TESTS ==========

    @Test
    fun `scenario explanation - with evidence shows why chosen`() {
        val scenario = ScenarioSketch(
            id = ScenarioId("scn-1"),
            title = "Produksi Lengkap",
            steps = listOf(
                ScenarioStep(ModuleId("mrp"), null, "Input Order", 
                    evidence = EvidenceRef(EvidenceSource.NARRATIVE, quote = "kain beli")),
                ScenarioStep(ModuleId("cutting"), null, "Potong",
                    evidence = EvidenceRef(EvidenceSource.NARRATIVE, quote = "potong"))
            ),
            source = ScenarioSource.AI,
            contractVersion = 2
        )

        val explanation = ScenarioExplanation.fromScenario(scenario, null)

        assertFalse(explanation.isLegacy)
        assertTrue(explanation.hasEvidence)
        assertEquals(2, explanation.stepExplanations.size)
        assertTrue(explanation.stepExplanations.first().explanation.contains("Dipilih"))
    }

    @Test
    fun `scenario explanation - legacy shows not available`() {
        val scenario = ScenarioSketch(
            id = ScenarioId("scn-1"),
            title = "Produksi",
            steps = listOf(
                ScenarioStep(ModuleId("mrp"), null, "Input Order")
            ),
            source = ScenarioSource.AI,
            contractVersion = 1
        )

        val explanation = ScenarioExplanation.fromScenario(scenario, null)

        assertTrue(explanation.isLegacy)
        assertTrue(explanation.stepExplanations.all { it.isLegacy })
    }

    @Test
    fun `all scenarios explanation - aggregates correctly`() {
        val scenarios = listOf(
            ScenarioSketch(
                id = ScenarioId("scn-1"),
                title = "Skenario 1",
                steps = listOf(ScenarioStep(ModuleId("mrp"), null, "Input")),
                source = ScenarioSource.AI,
                contractVersion = 2
            ),
            ScenarioSketch(
                id = ScenarioId("scn-2"),
                title = "Skenario 2",
                steps = listOf(ScenarioStep(ModuleId("cutting"), null, "Potong")),
                source = ScenarioSource.AI,
                contractVersion = 1 // legacy
            )
        )

        val allExplanation = AllScenariosExplanation.fromScenarios(scenarios, null)

        assertEquals(2, allExplanation.totalScenarios)
        assertEquals(0, allExplanation.scenariosWithEvidence) // v2 but no evidence on steps
        assertEquals(1, allExplanation.legacyScenarios)
    }
}

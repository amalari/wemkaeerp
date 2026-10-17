package com.eventverse.app.domain.discovery.scenario

import com.eventverse.app.domain.evidence.EvidenceRef
import com.eventverse.app.domain.evidence.EvidenceContext

/**
 * TRD-BLDR-003 F4: Explainability UI for Scenario
 *
 * Menjelaskan "mengapa dipilih" untuk skenario.
 */

/**
 * Satu penjelasan untuk satu step dalam skenario.
 */
data class StepExplanation(
    val stepIndex: Int,
    val moduleId: String,
    val label: String,
    val explanation: String,
    val evidence: EvidenceRef?,
    val carriesExplanation: String?,
    val isLegacy: Boolean = false
)

/**
 * Explanations untuk satu skenario lengkap.
 */
data class ScenarioExplanation(
    val scenarioId: String,
    val title: String,
    val titleExplanation: String,
    val stepExplanations: List<StepExplanation>,
    val hasEvidence: Boolean,
    val isLegacy: Boolean
) {
    companion object {
        /**
         * Buat explanation dari skenario.
         */
        fun fromScenario(
            scenario: ScenarioSketch,
            evidenceContext: EvidenceContext?
        ): ScenarioExplanation {
            val hasEvidence = scenario.contractVersion == 2 && 
                scenario.steps.any { it.evidence != null }
            
            val isLegacy = scenario.contractVersion == 1

            val titleExplanation = when {
                isLegacy -> "Skenario dibuat sebelum fitur evidence tersedia"
                hasEvidence -> "Skenario disusun berdasarkan alur bisnis dari narasi"
                else -> "Tidak ada bukti yang cukup"
            }

            val stepExplanations = scenario.steps.mapIndexed { index, step ->
                val evidenceExplanation = when {
                    step.evidence != null -> "Dipilih: ${step.evidence.quote ?: "berdasarkan narasi"}"
                    isLegacy -> "Alasan belum tersedia (legacy)"
                    else -> "Tidak ada bukti"
                }

                val carriesExplanation = if (step.carries.isNotEmpty()) {
                    "Mengalirkan: ${step.carries.joinToString { "${it.fromField} → ${it.toField}" }}"
                } else null

                StepExplanation(
                    stepIndex = index,
                    moduleId = step.moduleId.value,
                    label = step.label,
                    explanation = evidenceExplanation,
                    evidence = step.evidence,
                    carriesExplanation = carriesExplanation,
                    isLegacy = isLegacy
                )
            }

            return ScenarioExplanation(
                scenarioId = scenario.id.value,
                title = scenario.title,
                titleExplanation = titleExplanation,
                stepExplanations = stepExplanations,
                hasEvidence = hasEvidence,
                isLegacy = isLegacy
            )
        }
    }
}

/**
 * Explanations untuk semua skenario dalam draft.
 */
data class AllScenariosExplanation(
    val scenarioExplanations: List<ScenarioExplanation>,
    val totalScenarios: Int,
    val scenariosWithEvidence: Int,
    val legacyScenarios: Int
) {
    companion object {
        fun fromScenarios(
            scenarios: List<ScenarioSketch>,
            evidenceContext: EvidenceContext?
        ): AllScenariosExplanation {
            val explanations = scenarios.map { ScenarioExplanation.fromScenario(it, evidenceContext) }
            
            return AllScenariosExplanation(
                scenarioExplanations = explanations,
                totalScenarios = scenarios.size,
                scenariosWithEvidence = explanations.count { it.hasEvidence },
                legacyScenarios = explanations.count { it.isLegacy }
            )
        }
    }
}

/**
 * Label untuk UI scenario explanation.
 */
object ScenarioWhyChosenLabels {
    const val TITLE = "Mengapa Alur Ini Dipilih"
    const val LEGACY_NOTE = "Alasan belum tersedia"
    const val NO_EVIDENCE = "Tidak ada bukti yang cukup"
    const val STEP_PREFIX = "Langkah"
    const val CARRIES_LABEL = "Aliran Data"
}

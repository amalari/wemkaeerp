package com.eventverse.app.domain.builder

import com.eventverse.app.domain.evidence.EvidenceContext
import com.eventverse.app.domain.evidence.EvidenceRef

/**
 * TRD-BLDR-003 F4: Explainability UI
 *
 * Menjelaskan "mengapa dipilih" untuk modul, fitur, tahap, dan skenario.
 */

/**
 * Satu penjelasan untuk satu item brief (feature atau stage).
 */
data class BriefExplanation(
    val itemKey: String,
    val itemLabel: String,
    val explanation: String,
    val evidence: EvidenceRef?,
    val isLegacy: Boolean = false
)

/**
 * Explanations untuk satu brief lengkap.
 */
data class ModuleBriefExplanation(
    val moduleId: String,
    val summaryExplanation: String,
    val featureExplanations: List<BriefExplanation>,
    val stageExplanations: List<BriefExplanation>,
    val hasEvidence: Boolean,
    val isLegacy: Boolean
) {
    companion object {
        /**
         * Buat explanation dari brief dan evidence context.
         */
        fun fromBrief(
            moduleId: String,
            brief: ModuleBrief,
            evidenceContext: EvidenceContext?
        ): ModuleBriefExplanation {
            val hasEvidence = brief.contractVersion == 2 && 
                brief.features.any { it.evidence != null } && 
                brief.stages.any { it.evidence != null }
            
            val isLegacy = brief.contractVersion == 1

            val featureExplanations = brief.features.map { feature ->
                val explanation = if (feature.evidence != null) {
                    "Dipilih karena: ${feature.evidence.quote ?: "berdasarkan narasi"}"
                } else if (isLegacy) {
                    "Alasan belum tersedia (legacy)"
                } else {
                    "Tidak ada bukti yang cukup"
                }
                
                BriefExplanation(
                    itemKey = feature.text,
                    itemLabel = feature.text,
                    explanation = explanation,
                    evidence = feature.evidence,
                    isLegacy = isLegacy
                )
            }

            val stageExplanations = brief.stages.map { stage ->
                val explanation = if (stage.evidence != null) {
                    "Tahap ${stage.name} dipilih karena: ${stage.evidence.quote ?: "berdasarkan narasi"}"
                } else if (isLegacy) {
                    "Alasan belum tersedia (legacy)"
                } else {
                    "Tidak ada bukti yang cukup"
                }
                
                BriefExplanation(
                    itemKey = stage.name,
                    itemLabel = stage.name,
                    explanation = explanation,
                    evidence = stage.evidence,
                    isLegacy = isLegacy
                )
            }

            val summaryExplanation = when {
                isLegacy -> "Ringkasan dibuat sebelum fitur evidence tersedia"
                hasEvidence -> "Ringkasan berdasarkan analisis narasi dan bukti"
                else -> "Tidak ada enough evidence untuk生成 ringkasan"
            }

            return ModuleBriefExplanation(
                moduleId = moduleId,
                summaryExplanation = summaryExplanation,
                featureExplanations = featureExplanations,
                stageExplanations = stageExplanations,
                hasEvidence = hasEvidence,
                isLegacy = isLegacy
            )
        }
    }
}

/**
 * Label untuk UI: "Mengapa dipilih".
 */
object WhyChosenLabels {
    const val TITLE = "Mengapa Dipilih"
    const val LEGACY_NOTE = "Alasan belum tersedia"
    const val NO_EVIDENCE = "Tidak ada bukti yang cukup"
    const val EVIDENCE_BASED = "Berdasarkan bukti:"
    const val FEATURES_SECTION = "Fitur"
    const val STAGES_SECTION = "Tahap"
}

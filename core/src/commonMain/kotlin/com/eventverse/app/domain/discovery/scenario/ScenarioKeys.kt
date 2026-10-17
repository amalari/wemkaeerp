package com.eventverse.app.domain.discovery.scenario

import com.eventverse.app.domain.builder.BriefKeys

/**
 * Kunci stabil pertanyaan skenario (FR-9). Dibekukan: menentukan idempotensi dan `Clarification.id`. Rumus fingerprint = milik
 * [BriefKeys] (FNV-1a atas teks ternormalisasi) supaya satu pertanyaan yang sama tidak ditanyakan dua kali.
 */
object ScenarioKeys {
    /** Awalan id pertanyaan skenario (lawan: `ai:` brief modul, `gap:` celah template). */
    const val QUESTION_ID_PREFIX = "scn:"

    /** Stabil per (skenario, teks pertanyaan ternormalisasi); memuat id skenario sehingga dua skenario tak pernah bentrok. */
    fun questionId(scenarioId: ScenarioId, question: String): String =
        "$QUESTION_ID_PREFIX${scenarioId.value}:${BriefKeys.fingerprint(question)}"

    fun isQuestionId(id: String): Boolean = id.startsWith(QUESTION_ID_PREFIX)
}

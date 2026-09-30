package com.eventverse.app.domain.tutorial

import com.eventverse.app.domain.pack.ModuleId

/** Satu kandidat tutorial untuk sebuah pertanyaan, beserta langkah yang paling relevan. */
data class TutorialMatch(val tutorial: ModuleTutorial, val stepIndex: Int, val score: Int)

/**
 * Pemeringkat tutorial untuk pertanyaan bebas (TRD-HELP-001 FR-5). [candidates] **wajib** sudah disaring wewenang —
 * pencocok tidak memutuskan akses.
 */
fun interface TutorialMatcher {
    fun rank(question: String, candidates: List<ModuleTutorial>, currentModule: ModuleId?): List<TutorialMatch>
}

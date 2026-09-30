package com.eventverse.app.domain.help

import com.eventverse.app.domain.pack.ModuleId
import com.eventverse.app.domain.tutorial.TutorialId
import com.eventverse.app.domain.tutorial.TutorialMatch

/** Pertanyaan ke AI helper beserta kandidat tutorial yang **sudah** lolos wewenang dan pencocok. */
data class HelpQuestion(val text: String, val currentModule: ModuleId?, val candidates: List<TutorialMatch>)

/**
 * Jawaban agent. [tutorialId] wajib salah satu kandidat — `AskHelpUseCase` menolak id di luar daftar
 * (anti-halusinasi) dan jatuh ke agent deterministik.
 */
data class HelpAnswer(val text: String, val tutorialId: TutorialId?, val stepIndex: Int?, val agentRef: String)

/** Port AI helper (TRD-HELP-001). Implementasi: [DeterministicHelpAgent] di core, Koog di server (Fase 3). */
interface HelpAgent {
    val agentRef: String
    suspend fun answer(question: HelpQuestion): Result<HelpAnswer>
}

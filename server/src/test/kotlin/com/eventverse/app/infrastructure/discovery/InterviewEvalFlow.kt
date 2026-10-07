package com.eventverse.app.infrastructure.discovery

import com.eventverse.app.InterviewEvalCase
import com.eventverse.app.InterviewGoldenCases
import com.eventverse.app.InterviewGuessFn
import com.eventverse.app.InterviewEvalGrader
import com.eventverse.app.InterviewEvalVerdict
import com.eventverse.app.draftFor
import com.eventverse.app.domain.discovery.DiscoveryDraft
import com.eventverse.app.domain.discovery.interview.Confirmation
import com.eventverse.app.domain.discovery.interview.InterviewSession
import com.eventverse.app.domain.discovery.interview.InterviewAnswer
import com.eventverse.app.domain.discovery.interview.InterviewStep

/** Mutu tebakan satu giliran: berapa usulan diberikan dan berapa kunci langkah itu tercakup. */
data class InterviewTurnResult(
    val step: InterviewStep,
    val guessCount: Int,
    val keyTotal: Int,
    val keyCovered: Int,
    val durationMs: Long,
    val llmCalls: Int
)

/** Hasil alur wawancara satu kasus: draf akhir + jejak giliran. */
data class InterviewFlowResult(val finalDraft: DiscoveryDraft, val turns: List<InterviewTurnResult>, val verdict: InterviewEvalVerdict)

/**
 * Pelari alur wawancara untuk eval (plan IV-C4) — mensimulasikan **pengguna kooperatif**: menerima
 * usulan yang sah, menambah yang kurang dari kunci kasus sebagai jawaban pengguna (`ANSWER`), lalu
 * maju ke langkah berikutnya. Yang diukur adalah mutu tebakan per langkah dan keabsahan sesi akhir —
 * bukan kecakapan simulasi pengguna.
 *
 * Dipakai eval live (C4) sekarang dan baseline deterministik (B1) nanti tanpa perubahan, lewat seam
 * [InterviewGuessFn].
 */
suspend fun runInterviewFlow(
    case: InterviewEvalCase,
    guessFn: InterviewGuessFn,
    callsBefore: () -> Int = { 0 }
): InterviewFlowResult {
    var draft = draftFor(case)
    val turns = mutableListOf<InterviewTurnResult>()

    for (step in listOf(InterviewStep.G1_DIVISI, InterviewStep.G2_PERAN, InterviewStep.G3_MODUL, InterviewStep.G4_SAMBUNGAN)) {
        val before = callsBefore()
        val started = System.currentTimeMillis()
        val guesses = guessFn.guess(step, draft.pack, draft, case.narrative).getOrThrow()
        val duration = System.currentTimeMillis() - started

        val merged = mergeStepGuesses(draft.interview ?: InterviewSession(step = step), stampGuessProvenance(guesses))
        val session = merged.copy(
            step = nextStep(step),
            answers = merged.answers + InterviewAnswer(merged.answers.size + 1, step, "g${step.ordinal}", Confirmation.CONFIRMED)
        )
        draft = draft.copy(interview = session)

        val (total, covered) = when (step) {
            InterviewStep.G1_DIVISI -> case.expectedDivisions.size to InterviewEvalGrader.coveredDivisions(case, session)
            InterviewStep.G2_PERAN -> case.expectedRoles.size to InterviewEvalGrader.coveredRoles(case, session)
            InterviewStep.G3_MODUL -> case.expectedLinks.size to InterviewEvalGrader.coveredLinks(case, session, draft.pack)
            else -> 0 to 0
        }
        turns += InterviewTurnResult(step, guesses.divisions.size + guesses.roles.size + guesses.links.size + guesses.handoffs.size, total, covered, duration, callsBefore() - before)
    }

    val verdict = InterviewEvalGrader.grade(case, draft)
    return InterviewFlowResult(draft, turns, verdict)
}

private fun nextStep(step: InterviewStep): InterviewStep = when (step) {
    InterviewStep.G1_DIVISI -> InterviewStep.G2_PERAN
    InterviewStep.G2_PERAN -> InterviewStep.G3_MODUL
    InterviewStep.G3_MODUL -> InterviewStep.G4_SAMBUNGAN
    InterviewStep.G4_SAMBUNGAN -> InterviewStep.G5_RINGKASAN
    else -> InterviewStep.DONE
}

/** Kasus yang dipilih env `INTERVIEW_LIVE_EVALS_CASES`, atau semuanya (plan IV-C4, hemat biaya saat verifikasi). */
fun selectedInterviewCases(): List<InterviewEvalCase> =
    System.getenv("INTERVIEW_LIVE_EVALS_CASES")?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }
        ?.let { names -> InterviewGoldenCases.all.filter { it.name in names }.ifEmpty { error("INTERVIEW_LIVE_EVALS_CASES tidak cocok dengan kasus mana pun: $names") } }
        ?: InterviewGoldenCases.all

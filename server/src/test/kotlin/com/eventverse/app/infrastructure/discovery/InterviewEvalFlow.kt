package com.eventverse.app.infrastructure.discovery

import com.eventverse.app.InterviewEvalCase
import com.eventverse.app.InterviewGoldenCases
import com.eventverse.app.InterviewGuessFn
import com.eventverse.app.InterviewEvalGrader
import com.eventverse.app.InterviewEvalVerdict
import com.eventverse.app.draftFor
import com.eventverse.app.domain.discovery.DiscoveryDraft
import com.eventverse.app.domain.pack.DomainPack
import com.eventverse.app.domain.discovery.interview.Confirmation
import com.eventverse.app.domain.discovery.interview.DivisionCode
import com.eventverse.app.domain.discovery.interview.DivisionDraft
import com.eventverse.app.domain.discovery.interview.ItemSource
import com.eventverse.app.domain.discovery.interview.InterviewSession
import com.eventverse.app.domain.discovery.interview.RoleDraft
import com.eventverse.app.domain.discovery.interview.RoleKey
import com.eventverse.app.domain.discovery.interview.RoleModuleLink
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
 * Pelari alur wawancara untuk eval (plan IV-C4) - mensimulasikan **pengguna kooperatif**: menerima
 * usulan yang sah, menambah yang kurang dari kunci kasus sebagai jawaban pengguna (`ANSWER`), lalu
 * maju ke langkah berikutnya. Mutu tebakan per langkah diukur **sebelum** suplemen pengguna supaya
 * suplemen tidak menaikkan skor tebakan.
 *
 * Dipakai baseline deterministik (B1, wajib 100%) dan eval live (C4) tanpa perubahan, lewat seam
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

        // Mutu tebakan diukur SEBELUM suplemen pengguna.
        val (keyTotal, keyCovered) = keyCoverage(case, step, merged, draft.pack)

        val supplemented = completeFromKey(case, step, merged, draft.pack)
        val session = supplemented.copy(
            step = nextStep(step),
            answers = supplemented.answers + InterviewAnswer(supplemented.answers.size + 1, step, "g${'$'}{step.ordinal}", Confirmation.CONFIRMED)
        )
        draft = draft.copy(interview = session)

        turns += InterviewTurnResult(
            step,
            guesses.divisions.size + guesses.roles.size + guesses.links.size + guesses.handoffs.size,
            keyTotal, keyCovered, duration, callsBefore() - before
        )
    }

    val verdict = InterviewEvalGrader.grade(case, draft)
    return InterviewFlowResult(draft, turns, verdict)
}

private fun keyCoverage(case: InterviewEvalCase, step: InterviewStep, session: InterviewSession, pack: DomainPack): Pair<Int, Int> = when (step) {
    InterviewStep.G1_DIVISI -> case.expectedDivisions.size to InterviewEvalGrader.coveredDivisions(case, session)
    InterviewStep.G2_PERAN -> case.expectedRoles.size to InterviewEvalGrader.coveredRoles(case, session)
    InterviewStep.G3_MODUL -> case.expectedLinks.size to InterviewEvalGrader.coveredLinks(case, session, pack)
    else -> 0 to 0
}

/** Nama tampil dari sinonim kunci: kapital di awal (cukup untuk pencocok `contains`). */
private fun keyLabel(synonyms: Set<String>): String = synonyms.first().replaceFirstChar { it.uppercase() }

/** Slug aman dari sinonim kunci - huruf kecil, non-alfanumerik jadi underscore, diawali huruf. */
private fun keySlug(synonyms: Set<String>): String =
    synonyms.first().lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_').ifEmpty { "area" }
        .let { if (it.first().isLetter()) it else "x$it" }

/**
 * Suplemen pengguna kooperatif: butir kunci yang tak tertebak ditambahkan sebagai jawaban pengguna
 * (`ANSWER`, `CONFIRMED`) - divisi di G1, peran di G2, tautan di G3. Sesi akhir selalu lengkap dan
 * sah; nilai evalnya ada di berapa banyak yang BENAR-benar ditebak sistem, bukan di suplemen.
 */
private fun completeFromKey(case: InterviewEvalCase, step: InterviewStep, session: InterviewSession, pack: DomainPack): InterviewSession = when (step) {
    InterviewStep.G1_DIVISI -> {
        val additions = case.expectedDivisions
            .filter { synonyms -> session.divisions.none { d -> synonyms.any { d.name.lowercase().contains(it) } } }
            .map { synonyms -> DivisionDraft(DivisionCode(keySlug(synonyms)), keyLabel(synonyms), ItemSource.ANSWER) }
        session.copy(divisions = session.divisions + additions)
    }
    InterviewStep.G2_PERAN -> {
        val divisions = session.divisions.associate { d -> d.code to d.name.lowercase() }
        val additions = case.expectedRoles.mapNotNull { expected ->
            val covered = session.roles.any { r -> expected.roleSynonyms.any { r.label.lowercase().contains(it) } }
            if (covered) return@mapNotNull null
            val target = divisions.entries.firstOrNull { (_, name) -> expected.divisionSynonyms.any { name.contains(it) } }
                ?: return@mapNotNull null
            RoleDraft(RoleKey(keySlug(expected.roleSynonyms)), keyLabel(expected.roleSynonyms), target.key, ItemSource.ANSWER)
        }
        session.copy(roles = session.roles + additions)
    }
    InterviewStep.G3_MODUL -> {
        val roleByKey = session.roles.associateBy { it.roleKey }
        val additions = case.expectedLinks.mapNotNull { expected ->
            val covered = session.links.any { l ->
                val label = roleByKey[l.roleKey]?.label?.lowercase() ?: return@any false
                expected.roleSynonyms.any { label.contains(it) } &&
                    pack.module(l.moduleId)?.let { m ->
                        expected.moduleSynonyms.any { s -> m.id.value.contains(s) || m.displayName.lowercase().contains(s) }
                    } == true
            }
            if (covered) return@mapNotNull null
            val role = roleByKey.entries.firstOrNull { (_, r) ->
                expected.roleSynonyms.any { r.label.lowercase().contains(it) }
            } ?: return@mapNotNull null
            val moduleId = InterviewEvalGrader.matchingModule(pack, expected.moduleSynonyms) ?: return@mapNotNull null
            RoleModuleLink(role.key, moduleId, expected.origins.first(), emptyList(), Confirmation.CONFIRMED)
        }
        session.copy(links = session.links + additions)
    }
    else -> session
}

private fun nextStep(step: InterviewStep): InterviewStep = when (step) {
    InterviewStep.G1_DIVISI -> InterviewStep.G2_PERAN
    InterviewStep.G2_PERAN -> InterviewStep.G3_MODUL
    InterviewStep.G3_MODUL -> InterviewStep.G4_SAMBUNGAN
    InterviewStep.G4_SAMBUNGAN -> InterviewStep.G5_RINGKASAN
    else -> InterviewStep.DONE
}

/**
 * Seam kamus deterministik B (baseline): [com.eventverse.app.domain.discovery.interview.DeterministicInterviewGuesser.propose]
 * mengembalikan sesi penuh (divisi ber-peran, peran ber-divisi, tautan ber-asal) - dipotong per langkah
 * seperti penebak yang bekerja langkah demi langkah.
 */
fun deterministicKamusSeam(): InterviewGuessFn = InterviewGuessFn { step, pack, _, narrative ->
    val proposal = com.eventverse.app.domain.discovery.interview.DeterministicInterviewGuesser.propose(pack, narrative)
    Result.success(
        InterviewStepGuesses(
            step = step,
            divisions = if (step == InterviewStep.G1_DIVISI) proposal.divisions else emptyList(),
            roles = if (step == InterviewStep.G2_PERAN) proposal.roles else emptyList(),
            links = if (step == InterviewStep.G3_MODUL) proposal.links else emptyList(),
            handoffs = if (step == InterviewStep.G4_SAMBUNGAN) proposal.handoffs else emptyList()
        )
    )
}

/** Kasus yang dipilih env `INTERVIEW_LIVE_EVALS_CASES`, atau semuanya (plan IV-C4, hemat biaya saat verifikasi). */
fun selectedInterviewCases(): List<InterviewEvalCase> =
    System.getenv("INTERVIEW_LIVE_EVALS_CASES")?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }
        ?.let { names -> InterviewGoldenCases.all.filter { it.name in names }.ifEmpty { error("INTERVIEW_LIVE_EVALS_CASES tidak cocok dengan kasus mana pun: $names") } }
        ?: InterviewGoldenCases.all

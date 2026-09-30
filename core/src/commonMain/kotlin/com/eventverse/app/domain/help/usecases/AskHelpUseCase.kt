package com.eventverse.app.domain.help.usecases

import com.eventverse.app.domain.help.DeterministicHelpAgent
import com.eventverse.app.domain.crm.prefill.PiiMasker
import com.eventverse.app.domain.help.HelpAction
import com.eventverse.app.domain.help.HelpActionResolver
import com.eventverse.app.domain.help.HelpAgent
import com.eventverse.app.domain.help.HelpAnswer
import com.eventverse.app.domain.help.HelpQuestion
import com.eventverse.app.domain.pack.DomainPack
import com.eventverse.app.domain.pack.ModuleId
import com.eventverse.app.domain.rbac.AccessDecision
import com.eventverse.app.domain.tutorial.SurfaceCode
import com.eventverse.app.domain.tutorial.TutorialAccess
import com.eventverse.app.domain.tutorial.TutorialCatalog
import com.eventverse.app.domain.tutorial.TutorialId
import com.eventverse.app.domain.tutorial.TutorialMatch
import com.eventverse.app.domain.tutorial.TutorialMatcher

data class AskHelpCommand(
    val question: String,
    val currentModule: ModuleId?,
    val pack: DomainPack,
    val decisions: Map<ModuleId, AccessDecision>,
    val allowedSurfaces: Set<SurfaceCode> = emptySet(),
    /** Aksi yang boleh ditawarkan ke pemanggil ini (sudah lolos wewenang & opt-in di route). */
    val actionResolver: HelpActionResolver? = null,
)

data class HelpSuggestion(val tutorialId: TutorialId, val title: String, val moduleId: ModuleId?, val stepIndex: Int)

data class HelpResult(
    val answer: String,
    val suggestion: HelpSuggestion?,
    val alternatives: List<HelpSuggestion>,
    val agentRef: String,
    val action: HelpAction? = null,
)

/**
 * Menjawab pertanyaan bantuan (TRD-HELP-001 FR-6): wewenang → pencocok → agent → validasi.
 *
 * Urutan ini disengaja: wewenang disaring **sebelum** pencocokan, sehingga agent (termasuk LLM) tidak pernah melihat
 * tutorial modul yang tidak boleh dibaca pemanggil. Agent gagal, atau menyebut tutorial di luar kandidat → jawaban
 * [fallback], bukan jawaban agent yang dibuang separuh.
 */
class AskHelpUseCase(
    private val catalog: TutorialCatalog,
    private val matcher: TutorialMatcher,
    private val agent: HelpAgent,
    private val fallback: HelpAgent = DeterministicHelpAgent(),
) {
    suspend operator fun invoke(command: AskHelpCommand): Result<HelpResult> = runCatching {
        val question = command.question.trim()
        require(question.isNotEmpty()) { "Pertanyaan kosong" }
        require(question.length <= MAX_QUESTION_LENGTH) { "Pertanyaan melebihi $MAX_QUESTION_LENGTH karakter" }

        // Permintaan input (Fase 5b): jawab dengan aksi tanpa agent — pesan berisi data pelanggan tidak dikirim ke LLM helper.
        command.actionResolver?.resolve(question)?.let { action ->
            return@runCatching HelpResult(ACTION_ANSWER, suggestion = null, alternatives = emptyList(), agentRef = ACTION_REF, action = action)
        }

        val visible = TutorialAccess.accessible(catalog.forPack(command.pack), command.decisions, command.allowedSurfaces)
        val candidates = matcher.rank(question, visible, command.currentModule)
        // Nomor telepon & email disamarkan sebelum sampai ke agent (bisa LLM); pencocok memakai teks asli di memori.
        val helpQuestion = HelpQuestion(PiiMasker.mask(question).text, command.currentModule, candidates)

        val answer = agent.answer(helpQuestion).getOrNull()?.takeIf { it.isGroundedIn(candidates) }
            ?: fallback.answer(helpQuestion).getOrThrow()

        val chosen = answer.tutorialId?.let { id -> candidates.first { it.tutorial.id == id } }
        HelpResult(
            answer = answer.text,
            suggestion = chosen?.toSuggestion(answer.stepIndex ?: chosen.stepIndex),
            alternatives = candidates.filter { it !== chosen }.map { it.toSuggestion(it.stepIndex) },
            agentRef = answer.agentRef,
        )
    }

    private fun HelpAnswer.isGroundedIn(candidates: List<TutorialMatch>): Boolean {
        val id = tutorialId ?: return candidates.isEmpty() || text.isNotBlank()
        val match = candidates.firstOrNull { it.tutorial.id == id } ?: return false
        return stepIndex == null || stepIndex in match.tutorial.steps.indices
    }

    private fun TutorialMatch.toSuggestion(step: Int) = HelpSuggestion(tutorial.id, tutorial.title, tutorial.moduleId, step)

    companion object {
        const val MAX_QUESTION_LENGTH = 500
        const val ACTION_REF = "intent/help-action-v1"
        const val ACTION_ANSWER = "Saya bisa mengisikan form dari pesan ini. Tekan tombol di bawah, periksa isiannya, lalu simpan sendiri."
    }
}

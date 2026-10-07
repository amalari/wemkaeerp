package com.eventverse.app.infrastructure.discovery

import com.eventverse.app.domain.discovery.DiscoveryDraft
import com.eventverse.app.domain.discovery.DiscoveryValidationIssue
import com.eventverse.app.domain.discovery.interview.Confirmation
import com.eventverse.app.domain.discovery.interview.Guess
import com.eventverse.app.domain.discovery.interview.InterviewGuesser
import com.eventverse.app.domain.discovery.interview.DivisionDraft
import com.eventverse.app.domain.discovery.interview.ModuleHandoff
import com.eventverse.app.domain.discovery.interview.RoleDraft
import com.eventverse.app.domain.discovery.interview.RoleModuleLink
import com.eventverse.app.domain.discovery.interview.InterviewSession
import com.eventverse.app.domain.discovery.interview.InterviewStep
import com.eventverse.app.domain.discovery.interview.InterviewValidator
import com.eventverse.app.domain.discovery.interview.ItemSource
import com.eventverse.app.domain.pack.DomainPack
import ai.koog.utils.io.use

/**
 * Usulan penebak untuk satu langkah wawancara — keluaran [AgentInterviewGuesser] dan seam eval.
 * Hanya butir langkah [step] yang berisi; sisanya kosong. Hasil ini **usulan**: pemanggil menggabungkannya
 * ([mergeStepGuesses]) dan validator yang menegakkan.
 */
data class InterviewStepGuesses(
    val step: InterviewStep,
    val divisions: List<DivisionDraft> = emptyList(),
    val roles: List<RoleDraft> = emptyList(),
    val links: List<RoleModuleLink> = emptyList(),
    val handoffs: List<ModuleHandoff> = emptyList()
)

/**
 * Menggabungkan usulan satu langkah ke sesi berjalan — **fungsi murni**, dites langsung.
 * Butir lama dipertahankan; butir dengan kunci sama digantikan usulan baru (tebakan terbaru menang),
 * tanpa mengubah langkah — langkah dimajukan oleh pemanggil (route/alur), bukan oleh penebak.
 */
internal fun mergeStepGuesses(session: InterviewSession, guesses: InterviewStepGuesses): InterviewSession = when (guesses.step) {
    InterviewStep.G1_DIVISI -> session.copy(
        divisions = mergeByKey(session.divisions, guesses.divisions, { it.code.value })
    )
    InterviewStep.G2_PERAN -> session.copy(
        roles = mergeByKey(session.roles, guesses.roles, { it.roleKey.value })
    )
    InterviewStep.G3_MODUL -> session.copy(
        links = mergeByKey(session.links, guesses.links) { "${it.roleKey.value}:${it.moduleId.value}" }
    )
    InterviewStep.G4_SAMBUNGAN -> session.copy(
        handoffs = mergeByKey(session.handoffs, guesses.handoffs) { "${it.from.value}->${it.to.value}:${it.portType.value}" }
    )
    // Fase konsultan F0–F2 bukan tebakan G1–G5: tidak ada yang digabung (persona konsultan = C6).
    InterviewStep.F0_BISNIS, InterviewStep.F1_TUJUAN, InterviewStep.F2_SPEK,
    InterviewStep.G5_RINGKASAN, InterviewStep.DONE -> session
}

private fun <T> mergeByKey(old: List<T>, new: List<T>, key: (T) -> String): List<T> {
    val replaced = new.associateBy(key)
    val kept = old.filter { it.let(key) !in replaced }
    return kept + new
}

/**
 * Membubuhkan provenance tebakan **di server, bukan dipercaya ke model**: divisi/peran dari model
 * selalu `GUESS`, tautan/sambungan selalu `GUESSED`. Confidence model dipertahankan (dipatok 0–100);
 * yang tidak menyebutnya diberi nilai bawaan supaya mutu tebakan tetap terukur.
 */
internal fun stampGuessProvenance(guesses: InterviewStepGuesses, defaultConfidence: Int = 70): InterviewStepGuesses = guesses.copy(
    divisions = guesses.divisions.map { it.copy(source = ItemSource.GUESS) },
    roles = guesses.roles.map { it.copy(source = ItemSource.GUESS) },
    links = guesses.links.map {
        it.copy(
            confirmed = Confirmation.GUESSED,
            confidence = (it.confidence ?: defaultConfidence).coerceIn(0, 100)
        )
    },
    handoffs = guesses.handoffs.map { it.copy(confirmed = Confirmation.GUESSED) }
)

/**
 * Penebak wawancara Koog (plan IV-C3) — mengimplementasikan bentuk port plan induk §6
 * (`InterviewGuesser.guess(step, pack, draft, narrative)`): keluarannya hanya **usulan** satu langkah,
 * validator yang menegakkan. `DeterministicInterviewGuesser` (B1) dan kelas ini dipakai bergantian
 * di belakang seam yang sama.
 *
 * Bentuk kerjanya mengikuti `KoogDiscoveryAgent` yang sudah terbukti di eval:
 *
 * 1. **Satu run Koog per giliran** (`singleRunStrategy`) dengan dua alat (`interview_state`,
 *    `interview_catalog`). Loop koreksi **di luar** agent: tiap putaran percakapan baru berisi galat
 *    berpath putaran sebelumnya — biaya terhitung dan jawaban bisa diganti skrip di test.
 * 2. **Dekode lewat parser produksi** (`KoogInterviewBridge.decodeInterviewAnswer` → `DiscoveryDraftCodec`),
 *    jembatan yang sama dengan alat `interview_state` — tidak ada bentuk dokumen kedua.
 * 3. **Provenance dibubuhkan server** ([stampGuessProvenance]) dan usulan divalidasi **setelah digabung**
 *    ke sesi berjalan ([mergeStepGuesses] + [InterviewValidator]) — galat berpathnya persis yang akan
 *    dilihat penyimpanan dokumen, sehingga koreksi diri menyasar bagian yang benar.
 *
 * Kegagalan yang tidak bisa dikoreksi dikembalikan sebagai `Result` gagal (galat terakhir disertakan);
 * keputusan jatuh ke deterministik ada di pemanggil, bukan di sini.
 */
class AgentInterviewGuesser(
    private val executor: ai.koog.prompt.executor.model.PromptExecutor,
    private val model: ai.koog.prompt.llm.LLModel = DiscoveryAgents.defaultModel,
    private val maxCorrectionRounds: Int = DEFAULT_MAX_CORRECTION_ROUNDS,
    private val maxToolIterations: Int = DEFAULT_MAX_TOOL_ITERATIONS
) {

    init {
        require(maxCorrectionRounds > 0) { "maxCorrectionRounds harus positif" }
        require(maxToolIterations > 0) { "maxToolIterations harus positif" }
    }

    /** `interview-v1` = bentuk keluaran dokumen interview saat ini; naikkan bila kontrak keluaran berubah. */
    val agentRef: String = "koog/${model.id}/interview-v1"

    suspend fun guess(
        step: InterviewStep,
        pack: DomainPack,
        draft: DiscoveryDraft,
        narrative: String
    ): Result<InterviewStepGuesses> = runCatching { generate(step, pack, draft, narrative) }

    /** Putaran koreksi: jawaban → dekode → bubuh → gabung → validasi; galat berpath ke putaran berikut. */
    private suspend fun generate(
        step: InterviewStep,
        pack: DomainPack,
        draft: DiscoveryDraft,
        narrative: String
    ): InterviewStepGuesses {
        var feedback: List<DiscoveryValidationIssue> = emptyList()
        var previousAnswer: String? = null
        var lastFailure: Throwable? = null

        for (round in 1..maxCorrectionRounds) {
            val answer = askAgent(step, draft, narrative, feedback, previousAnswer, round)
            previousAnswer = answer
            val guesses = try {
                stampGuessProvenance(stepGuessesOf(step, KoogInterviewBridge.decodeInterviewAnswer(answer, draft)))
            } catch (e: Exception) {
                lastFailure = e
                feedback = listOf(issueOf(e))
                continue
            }
            val merged = mergeStepGuesses(draft.interview ?: InterviewSession(step = step), guesses)
            val issues = InterviewValidator.validate(merged, pack)
            if (issues.isEmpty()) return guesses
            lastFailure = IllegalStateException(issues.joinToString("; ") { "${it.path}: ${it.message}" })
            feedback = issues
        }

        throw IllegalStateException(
            "Agent $agentRef gagal menghasilkan tebakan sah untuk $step setelah $maxCorrectionRounds putaran: " +
                feedback.joinToString("; ") { "${it.path}: ${it.message}" },
            lastFailure
        )
    }

    /** Ambil butir langkah [step] saja dari dokumen sesi yang dibalas model — langkah dikendalikan pemanggil. */
    private fun stepGuessesOf(step: InterviewStep, session: InterviewSession) = InterviewStepGuesses(
        step = step,
        divisions = if (step == InterviewStep.G1_DIVISI) session.divisions else emptyList(),
        roles = if (step == InterviewStep.G2_PERAN) session.roles else emptyList(),
        links = if (step == InterviewStep.G3_MODUL) session.links else emptyList(),
        handoffs = if (step == InterviewStep.G4_SAMBUNGAN) session.handoffs else emptyList()
    )

    /** Satu percakapan Koog: prompt sistem konsultan + pesan pengguna, dengan dua alat wawancara. */
    private suspend fun askAgent(
        step: InterviewStep,
        draft: DiscoveryDraft,
        narrative: String,
        feedback: List<DiscoveryValidationIssue>,
        previousAnswer: String?,
        round: Int
    ): String {
        val agent = ai.koog.agents.core.agent.AIAgent(
            promptExecutor = executor,
            llmModel = model,
            strategy = ai.koog.agents.core.agent.singleRunStrategy(),
            toolRegistry = interviewToolRegistry { draft },
            systemPrompt = KoogInterviewPrompt.system,
            temperature = TEMPERATURE,
            maxIterations = maxToolIterations
        )
        val input = KoogInterviewPrompt.userMessage(step, draft, narrative, feedback, previousAnswer, round)
        return agent.use { it.run(input) }
    }

    companion object {
        /** Tiga putaran: cukup untuk memperbaiki asal modul atau tautan yang tertinggal (sama dengan SP). */
        const val DEFAULT_MAX_CORRECTION_ROUNDS = 3

        /** Sama dengan SP: pemanggilan alat memakan dua langkah; 24 mencegah gagal "couldn't finish". */
        const val DEFAULT_MAX_TOOL_ITERATIONS = 24

        /** Rendah, bukan nol: tebakan harus patuh katalog, variasi nama divisi tetap diinginkan. */
        private const val TEMPERATURE = 0.2
    }
}

/**
 * Proyeksi usulan satu langkah ke port core plan induk §6 (`InterviewGuesser` → `List<Guess>`), dengan
 * kunci yang sama dengan pelaksana B: divisi = kode, peran = roleKey, tautan = `role:moduleId`,
 * sambungan = `from>to`. [AgentInterviewGuesser] sendiri tetap mengembalikan bentuk kaya
 * [InterviewStepGuesses] (itu yang dipakai eval dan penggabungan); adapter ini untuk pemanggil yang
 * berbicara lewat port tipis.
 */
fun InterviewStepGuesses.toGuesses(defaultConfidence: Int = 70): List<Guess> = buildList {
    divisions.forEach { add(Guess(it.code.value, it.name, defaultConfidence)) }
    roles.forEach { add(Guess(it.roleKey.value, it.label, defaultConfidence)) }
    links.forEach { add(Guess("${it.roleKey.value}:${it.moduleId.value}", "${it.roleKey.value} -> ${it.moduleId.value}", it.confidence ?: defaultConfidence, it.origin)) }
    handoffs.forEach { add(Guess("${it.from.value}>${it.to.value}", "${it.from.value} -> ${it.to.value}", 60)) }
}

/** Melihat [AgentInterviewGuesser] dari port tipis core — inilah sambungan ke dunia B. */
fun AgentInterviewGuesser.asInterviewGuesser(): InterviewGuesser = object : InterviewGuesser {
    override suspend fun guess(step: InterviewStep, pack: DomainPack, draft: DiscoveryDraft, narrative: String): Result<List<Guess>> =
        this@asInterviewGuesser.guess(step, pack, draft, narrative).map { it.toGuesses() }
}

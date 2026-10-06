package com.eventverse.app.infrastructure.discovery

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.singleRunStrategy
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.utils.io.use
import com.eventverse.app.domain.discovery.DiscoveryAgent
import com.eventverse.app.domain.discovery.DiscoveryDraft
import com.eventverse.app.domain.discovery.DiscoveryDraftValidator
import com.eventverse.app.domain.discovery.DiscoveryRequest
import com.eventverse.app.domain.discovery.DiscoveryValidationIssue
import com.eventverse.app.domain.discovery.proposal.ProposalSource
import com.eventverse.app.domain.pack.DomainPack
import com.eventverse.app.domain.pack.DomainPackRegistry
import com.eventverse.app.shared.discovery.DiscoveryDraftCodec
import com.eventverse.app.shared.discovery.DiscoveryDraftDecodeException
import com.eventverse.app.shared.json.JsonParser
import com.eventverse.app.shared.json.JsonValue
import com.eventverse.app.shared.pack.DomainPackCodec
import org.slf4j.LoggerFactory

/**
 * Agent discovery LLM (plan §2 A8, keputusan D4) di belakang kontrak domain `DiscoveryAgent`.
 *
 * Bentuk kerjanya tiga lapis, dan lapisannya sengaja dipisah agar bisa diuji tanpa jaringan:
 *
 * 1. **Satu run Koog** = satu percakapan bersih (`singleRunStrategy`) dengan dua alat
 *    (`platform_modules`, `validate_draft`). Loop koreksi **berada di luar** agent: setiap putaran
 *    memulai percakapan baru yang memuat galat berpath dari putaran sebelumnya. Alasannya praktis —
 *    biaya per putaran bisa dihitung dan dibatasi, dan jawaban LLM bisa diganti skrip deterministik
 *    di test.
 * 2. **Dekode lewat parser produksi** `DiscoveryDraftCodec` (bukan parser sendiri), sehingga bentuk
 *    dokumen yang diterima agent identik dengan yang diterima `PUT /api/discovery/drafts/{id}`.
 * 3. **Jembatan pack bawaan** ([applyShippedPackBridge]): validator menuntut dokumen pack bawaan
 *    platform dikembalikan identik, dan model tidak mungkin menulis ulang dokumen itu dari ingatan.
 *    Jadi model boleh menulis `{"pack":{"useShipped":"garment"}}`; di sini singkatan itu diganti
 *    dokumen asli **sebelum** validator melihatnya — bukan pelonggaran aturan, melainkan pemetaan ke
 *    dokumen yang sudah ada.
 *
 * [agentRef] memuat id model supaya jejak audit draf menyebut model yang menghasilkannya. Kegagalan
 * yang tidak bisa dikoreksi (setelah [maxCorrectionRounds]) dikembalikan sebagai `Result` gagal —
 * atau diteruskan ke [fallback] bila dipasang (`DiscoveryAgents` memasang jalur deterministik).
 *
 * Model default = [DiscoveryAgents.defaultModel] (id produksi yang sudah diverifikasi ke API), bukan
 * entri katalog Koog yang id-nya bisa tertinggal.
 */
class KoogDiscoveryAgent(
    private val executor: PromptExecutor,
    private val model: LLModel = DiscoveryAgents.defaultModel,
    private val maxCorrectionRounds: Int = DEFAULT_MAX_CORRECTION_ROUNDS,
    private val maxToolIterations: Int = DEFAULT_MAX_TOOL_ITERATIONS,
    private val fallback: DiscoveryAgent? = null
) : DiscoveryAgent {

    init {
        require(maxCorrectionRounds > 0) { "maxCorrectionRounds harus positif" }
        require(maxToolIterations > 0) { "maxToolIterations harus positif" }
    }

    /**
     * `draft-v2` = prompt & contoh membawa `proposal` (SP-C1). Naikkan versi setiap kali bentuk keluaran
     * yang diharapkan berubah; draf lama `draft-v1` tetap terbaca karena `proposal` bersifat tambatif.
     */
    override val agentRef: String = "koog/${model.id}/draft-v2"

    override suspend fun draft(request: DiscoveryRequest): Result<DiscoveryDraft> =
        runCatching { generate(request) }.recoverCatching { failure ->
            val alternative = fallback ?: throw failure
            logger.warn(
                "Agent {} gagal ({}); funnel dilanjutkan agent deterministik {}",
                agentRef, failure.message, alternative.agentRef
            )
            alternative.draft(request).getOrThrow()
        }

    /** Putaran koreksi: jawaban → dekode → validasi; galat berpath dikirim kembali ke putaran berikut. */
    private suspend fun generate(request: DiscoveryRequest): DiscoveryDraft {
        var feedback: List<DiscoveryValidationIssue> = emptyList()
        var previousAnswer: String? = null
        var lastFailure: Throwable? = null

        for (round in 1..maxCorrectionRounds) {
            val answer = askAgent(request, feedback, previousAnswer, round)
            previousAnswer = answer

            val draft = try {
                stampAgentProvenance(decodeAnswer(answer), agentRef)
            } catch (e: Exception) {
                lastFailure = e
                feedback = listOf(issueOf(e))
                continue
            }

            val issues = DiscoveryDraftValidator.validate(draft)
            if (issues.isEmpty()) return draft
            lastFailure = IllegalArgumentException(issues.joinToString("; ") { "${it.path}: ${it.message}" })
            feedback = issues
        }

        throw IllegalStateException(
            "Agent $agentRef gagal menghasilkan draf sah setelah $maxCorrectionRounds putaran: " +
                feedback.joinToString("; ") { "${it.path}: ${it.message}" },
            lastFailure
        )
    }

    /** Satu percakapan Koog: prompt sistem + pesan pengguna, dengan dua alat discovery. */
    private suspend fun askAgent(
        request: DiscoveryRequest,
        feedback: List<DiscoveryValidationIssue>,
        previousAnswer: String?,
        round: Int
    ): String {
        val agent = AIAgent(
            promptExecutor = executor,
            llmModel = model,
            strategy = singleRunStrategy(),
            toolRegistry = discoveryToolRegistry(),
            systemPrompt = KoogDiscoveryPrompt.system,
            temperature = TEMPERATURE,
            maxIterations = maxToolIterations
        )
        val input = KoogDiscoveryPrompt.userMessage(request, feedback, previousAnswer, round, agentRef)
        return agent.use { it.run(input) }
    }

    companion object {
        /** Tiga putaran: cukup untuk memperbaiki prefiks id atau modul blueprint yang tertinggal. */
        const val DEFAULT_MAX_CORRECTION_ROUNDS = 3

        /**
         * Batas langkah agent (panggilan LLM + eksekusi alat) satu putaran. 24, bukan 12: setiap
         * pemanggilan alat memakan dua langkah (minta → eksekusi), dan evals LLM hidup membuktikan
         * model sah memakai lima langkah untuk `platform_modules` + dua `validate_draft` + jawaban.
         * Batas yang terlalu ketat membuat agent gagal dengan "couldn't finish in given number of steps",
         * bukan dengan draf yang salah — kegagalan yang menyesatkan.
         */
        const val DEFAULT_MAX_TOOL_ITERATIONS = 24

        /** Rendah, bukan nol: draf harus patuh kontrak, tapi variasi nama modul tetap diinginkan. */
        private const val TEMPERATURE = 0.2

        private val logger = LoggerFactory.getLogger(KoogDiscoveryAgent::class.java)
    }
}

/**
 * Jawaban LLM → draf. Tiga langkah, semuanya bisa gagal dengan pesan berpath (tidak pernah senyap):
 * ambil objek JSON dari teks, jembatani pack bawaan, lalu dekode dengan parser produksi.
 */
internal fun decodeAnswer(answer: String): DiscoveryDraft =
    DiscoveryDraftCodec.decode(applyShippedPackBridge(extractJsonObject(answer)))

/**
 * Membubuhkan sumber AGENT pada setiap layar ber-proposal — **di server, bukan dipercaya ke model**.
 * Model yang menulis `{"kind":"PACK"}` atau lupa `source` sama sekali tidak mengubah provenance: seluruh
 * isi draf ini keluaran agent ini, dan jejak auditnya harus menyebut [agentRef] yang sebenarnya. Layar
 * tanpa proposal (gaya draf lama) dibiarkan apa adanya. Dipanggil sebelum validasi supaya model tidak
 * membuang satu putaran koreksi hanya untuk menyalin `source` yang sebenarnya diketahui server.
 */
internal fun stampAgentProvenance(draft: DiscoveryDraft, agentRef: String): DiscoveryDraft =
    if (draft.screens.none { it.proposal != null }) draft
    else draft.copy(
        screens = draft.screens.map { s ->
            if (s.proposal == null) s else s.copy(source = ProposalSource.Agent(agentRef))
        }
    )

/**
 * Model sering membungkus JSON dalam pagar kode (` ```json … ``` `) atau menambah kalimat pembuka.
 * Kita ambil objek pertama sampai `}` terakhir — **tidak** mencoba memperbaiki isi JSON; kalau isinya
 * rusak, `DiscoveryDraftCodec` yang melaporkannya berpath.
 */
internal fun extractJsonObject(text: String): String {
    val cleaned = text.trim()
        .removePrefix("```json").removePrefix("```")
        .removeSuffix("```")
        .trim()
    val start = cleaned.indexOf('{')
    val end = cleaned.lastIndexOf('}')
    if (start < 0 || end <= start) {
        throw DiscoveryDraftDecodeException("$", "jawaban agent tidak memuat objek JSON")
    }
    return cleaned.substring(start, end + 1)
}

/**
 * Mengganti singkatan `"pack": {"useShipped": "<kode>"}` dengan dokumen pack bawaan platform.
 *
 * Dokumen lain dikembalikan apa adanya (termasuk JSON yang tidak bisa diurai — biar `DiscoveryDraftCodec`
 * yang memberi pesan galat standar). Kode pack tak dikenal **ditolak berpath**, bukan diabaikan: kalau
 * dibiarkan, model akan menerima galat `$.pack` yang jauh dari akar masalahnya.
 */
internal fun applyShippedPackBridge(
    json: String,
    shipped: List<DomainPack> = DomainPackRegistry.shipped
): String {
    val root = runCatching { JsonParser.parseObject(json) }.getOrNull() ?: return json
    val requested = root.obj("pack")?.string("useShipped")?.takeIf { it.isNotBlank() } ?: return json
    val target = shipped.firstOrNull { it.code.value == requested }
        ?: throw DiscoveryDraftDecodeException(
            "$.pack.useShipped",
            "pack bawaan '$requested' tidak dikenal; pilihan: ${shipped.joinToString { it.code.value }}"
        )
    return JsonValue.Obj(root.entries + ("pack" to DomainPackCodec.encode(target))).encode()
}

/** Galat apa pun menjadi satu baris umpan balik berpath untuk putaran koreksi berikutnya. */
internal fun issueOf(failure: Throwable): DiscoveryValidationIssue = when (failure) {
    is DiscoveryDraftDecodeException -> DiscoveryValidationIssue(failure.path, failure.message ?: "dokumen tidak sah")
    else -> DiscoveryValidationIssue("$", failure.message ?: failure::class.simpleName ?: "tidak sah")
}

package com.eventverse.app.presentation.discovery

import com.eventverse.app.domain.discovery.interview.Confirmation
import com.eventverse.app.domain.discovery.interview.DivisionCode
import com.eventverse.app.domain.discovery.interview.DivisionDraft
import com.eventverse.app.domain.discovery.interview.Guess
import com.eventverse.app.domain.discovery.interview.InterviewAnswer
import com.eventverse.app.domain.discovery.interview.InterviewQuestion
import com.eventverse.app.domain.discovery.interview.InterviewSession
import com.eventverse.app.domain.discovery.interview.InterviewStep
import com.eventverse.app.domain.discovery.interview.ItemSource
import com.eventverse.app.domain.discovery.interview.ModuleHandoff
import com.eventverse.app.domain.discovery.interview.ModuleOrigin
import com.eventverse.app.domain.discovery.interview.RoleDraft
import com.eventverse.app.domain.discovery.interview.RoleKey
import com.eventverse.app.domain.discovery.interview.RoleModuleLink
import com.eventverse.app.domain.discovery.proposal.ProposalSource
import com.eventverse.app.domain.discovery.proposal.ScreenProposal
import com.eventverse.app.domain.pack.ModuleId
import com.eventverse.app.domain.pack.PortType
import com.eventverse.app.domain.prototype.InteractiveScreen
import com.eventverse.app.shared.discovery.ScreenProposalCodec
import com.eventverse.app.shared.json.JsonValue
import com.eventverse.app.shared.pack.InteractiveScreenCodec

/**
 * Model tampilan draf discovery (Fase D): hasil parse ringkasan `GET /api/discovery/drafts/{id}`.
 * Murni data tampilan — validasi domain tetap milik server; klien hanya menggambar.
 */
data class DiscoverySectionUi(
    val code: String,
    val displayName: String,
    val order: Int,
    val colorHex: Long,
    val tintHex: Long
)

data class DiscoveryModuleUi(
    val id: String,
    val displayName: String,
    val section: String,
    val kind: String,
    val iconKey: String? = null,
    val slot: String?,
    val slotInput: String?,
    val slotOutput: String?,
    val active: Boolean,
    val origin: ModuleOrigin? = null
)

data class DiscoveryScreenUi(
    val screenId: String,
    val moduleId: String,
    val title: String,
    val widget: String,
    val sampleRows: List<Map<String, String>>,
    /** Versi bisa dimainkan (TRD-PLAT-003); null = gambar statis dari [sampleRows]. */
    val interactive: InteractiveScreen? = null,
    /** Usulan layar tervalidasi (kontrak ScreenProposal); null untuk draf lama tanpa proposal. */
    val proposal: ScreenProposal? = null,
    /** Alasan pemilihan jenis tampilan bahasa pemilik usaha ("Dipilih karena …"). */
    val rationale: String? = null,
    /** Asal usulan (Pack / Deterministik / Agent). */
    val source: ProposalSource? = null
)

data class DiscoveryDraftUi(
    val id: String,
    val status: String,
    /** Narasi asli (E1/E2) — dipulihkan server dari buku demand; null untuk draf sebelum V80. */
    val narrative: String?,
    val packCode: String,
    val packDisplayName: String,
    val blueprintCode: String,
    val blueprintDescription: String,
    val modules: List<DiscoveryModuleUi>,
    val sectionsMetadata: List<DiscoverySectionUi> = emptyList(),
    val activeModuleCodes: List<String>,
    val screens: List<DiscoveryScreenUi>,
    /**
     * Kosakata label dari pack draf itu sendiri (`summaryObj`: `portLabels`/`slotLabels`).
     * Sumber utama label — bekerja juga untuk draf pra-handoff yang pack-nya belum terdaftar
     * di [com.eventverse.app.domain.pack.DomainPackRegistry]; tanpa entri = kode, bukan pack lain.
     */
    val portLabels: Map<String, String> = emptyMap(),
    val slotLabels: Map<String, String> = emptyMap(),
    /** Sesi wawancara konfirmasi (plan §6): null untuk draf sebelum wawancara dibuat. */
    val interview: InterviewSession? = null,
    /** Pertanyaan giliran berikutnya (data, bukan teks statis). */
    val nextQuestion: InterviewQuestion? = null
) {
    val activeModules: List<DiscoveryModuleUi> get() = modules.filter { it.active }
    val sections: List<String> get() = if (sectionsMetadata.isNotEmpty()) {
        sectionsMetadata.sortedBy { it.order }.map { it.code }
    } else {
        modules.map { it.section }.distinct()
    }

    companion object {
        fun fromJson(o: JsonValue.Obj): DiscoveryDraftUi {
            fun arr(key: String): List<JsonValue> = (o[key] as? JsonValue.Arr)?.items ?: emptyList()
            fun arr2(src: JsonValue.Obj, key: String): List<Map<String, String>> =
                ((src[key] as? JsonValue.Arr)?.items ?: emptyList())
                    .mapNotNull { it as? JsonValue.Obj }
                    .map { row -> row.entries.mapValues { (_, v) -> (v as? JsonValue.Str)?.value.orEmpty() } }
            fun stringMap(key: String): Map<String, String> =
                (o[key] as? JsonValue.Obj)?.entries
                    ?.mapValues { (_, v) -> (v as? JsonValue.Str)?.value.orEmpty() }
                    ?.filterValues { it.isNotEmpty() }
                    ?: emptyMap()

            val interviewObj = o.obj("interview")
            val parsedInterview = interviewObj?.let { obj ->
                runCatching {
                    val stepStr = obj.string("step").orEmpty()
                    val step = InterviewStep.fromCode(stepStr) ?: InterviewStep.G1_DIVISI
                    val rawDivisions = (obj["divisions"] as? JsonValue.Arr)?.items.orEmpty()
                    val divisions = rawDivisions.mapNotNull { it as? JsonValue.Obj }.mapNotNull { d ->
                        val code = d.string("code") ?: return@mapNotNull null
                        val name = d.string("name") ?: return@mapNotNull null
                        val src = d.string("source")?.let { ItemSource.fromCode(it) } ?: ItemSource.GUESS
                        runCatching { DivisionDraft(DivisionCode(code), name, src) }.getOrNull()
                    }
                    val rawRoles = (obj["roles"] as? JsonValue.Arr)?.items.orEmpty()
                    val roles = rawRoles.mapNotNull { it as? JsonValue.Obj }.mapNotNull { r ->
                        val roleKey = r.string("roleKey") ?: return@mapNotNull null
                        val label = r.string("label") ?: return@mapNotNull null
                        val divCode = r.string("divisionCode") ?: return@mapNotNull null
                        val src = r.string("source")?.let { ItemSource.fromCode(it) } ?: ItemSource.GUESS
                        val isHead = r.boolean("isHead") ?: false
                        runCatching { RoleDraft(RoleKey(roleKey), label, DivisionCode(divCode), src, isHead) }.getOrNull()
                    }
                    val rawLinks = (obj["links"] as? JsonValue.Arr)?.items.orEmpty()
                    val links = rawLinks.mapNotNull { it as? JsonValue.Obj }.mapNotNull { l ->
                        val roleKey = l.string("roleKey") ?: return@mapNotNull null
                        val moduleId = l.string("moduleId") ?: return@mapNotNull null
                        val origin = l.string("origin")?.let { ModuleOrigin.fromCode(it) } ?: ModuleOrigin.NEW
                        val features = (l["features"] as? JsonValue.Arr)?.items.orEmpty()
                            .mapNotNull { (it as? JsonValue.Str)?.value }
                        val confirmed = l.string("confirmed")?.let { Confirmation.fromCode(it) } ?: Confirmation.GUESSED
                        val confidence = l.int("confidence")
                        runCatching {
                            RoleModuleLink(RoleKey(roleKey), ModuleId(moduleId), origin, features, confirmed, confidence)
                        }.getOrNull()
                    }
                    val rawHandoffs = (obj["handoffs"] as? JsonValue.Arr)?.items.orEmpty()
                    val handoffs = rawHandoffs.mapNotNull { it as? JsonValue.Obj }.mapNotNull { h ->
                        val from = h.string("from") ?: return@mapNotNull null
                        val to = h.string("to") ?: return@mapNotNull null
                        val portType = h.string("portType") ?: return@mapNotNull null
                        val confirmed = h.string("confirmed")?.let { Confirmation.fromCode(it) } ?: Confirmation.GUESSED
                        runCatching {
                            ModuleHandoff(ModuleId(from), ModuleId(to), PortType(portType), confirmed)
                        }.getOrNull()
                    }
                    val rawAnswers = (obj["answers"] as? JsonValue.Arr)?.items.orEmpty()
                    val answers = rawAnswers.mapNotNull { it as? JsonValue.Obj }.mapNotNull { a ->
                        val turn = a.int("turn") ?: return@mapNotNull null
                        val aStep = a.string("step")?.let { InterviewStep.fromCode(it) } ?: InterviewStep.G1_DIVISI
                        val qId = a.string("questionId") ?: return@mapNotNull null
                        val outcome = a.string("outcome")?.let { Confirmation.fromCode(it) } ?: Confirmation.CONFIRMED
                        val text = a.string("text")
                        runCatching {
                            InterviewAnswer(turn, aStep, qId, outcome, text)
                        }.getOrNull()
                    }
                    InterviewSession(step, divisions, roles, links, handoffs, answers)
                }.getOrNull()
            }

            val nextQuestionObj = o.obj("nextQuestion")
            val parsedNextQuestion = nextQuestionObj?.let { q ->
                runCatching {
                    val qId = q.string("id") ?: return@runCatching null
                    val qStep = q.string("step")?.let { InterviewStep.fromCode(it) } ?: InterviewStep.G1_DIVISI
                    val prompt = q.string("prompt") ?: return@runCatching null
                    val rawGuesses = (q["guesses"] as? JsonValue.Arr)?.items.orEmpty()
                    val guesses = rawGuesses.mapNotNull { it as? JsonValue.Obj }.mapNotNull { g ->
                        val key = g.string("key") ?: return@mapNotNull null
                        val label = g.string("label") ?: return@mapNotNull null
                        val confidence = g.int("confidence") ?: 50
                        val origin = g.string("origin")?.let { ModuleOrigin.fromCode(it) }
                        runCatching { Guess(key, label, confidence, origin) }.getOrNull()
                    }
                    InterviewQuestion(qId, qStep, prompt, guesses)
                }.getOrNull()
            }

            return DiscoveryDraftUi(
                id = o.string("id").orEmpty(),
                status = o.string("status").orEmpty(),
                narrative = o.string("narrative"),
                packCode = o.string("packCode").orEmpty(),
                packDisplayName = o.string("packDisplayName").orEmpty(),
                blueprintCode = o.string("blueprintCode").orEmpty(),
                blueprintDescription = o.string("blueprintDescription").orEmpty(),
                modules = arr("modules").mapNotNull { it as? JsonValue.Obj }.map { m ->
                    DiscoveryModuleUi(
                        id = m.string("id").orEmpty(),
                        displayName = m.string("displayName").orEmpty(),
                        section = m.string("section").orEmpty(),
                        kind = m.string("kind").orEmpty(),
                        iconKey = m.string("iconKey"),
                        slot = m.string("slot"),
                        slotInput = m.string("slotInput"),
                        slotOutput = m.string("slotOutput"),
                        active = m.string("id") in (arr("activeModuleCodes").mapNotNull { (it as? JsonValue.Str)?.value }),
                        origin = m.string("origin")?.let { ModuleOrigin.fromCode(it) }
                    )
                },
                sectionsMetadata = arr("sections").mapNotNull { it as? JsonValue.Obj }.map { s ->
                    DiscoverySectionUi(
                        code = s.string("code").orEmpty(),
                        displayName = s.string("displayName").orEmpty(),
                        order = s.int("order") ?: 0,
                        colorHex = s.long("colorHex") ?: 0L,
                        tintHex = s.long("tintHex") ?: 0L
                    )
                },
                activeModuleCodes = arr("activeModuleCodes").mapNotNull { (it as? JsonValue.Str)?.value },
                screens = arr("screens").mapNotNull { it as? JsonValue.Obj }.map { s ->
                    val proposal = s.obj("proposal")?.let { raw ->
                        runCatching { ScreenProposalCodec.decode(raw, "$.screens.proposal") }.getOrNull()
                    }
                    val source = s.obj("source")?.let { raw ->
                        runCatching { ScreenProposalCodec.decodeSource(raw, "$.screens.source") }.getOrNull()
                    } ?: s.string("source")?.let { str ->
                        when {
                            str.equals("PACK", ignoreCase = true) -> ProposalSource.Pack
                            str.equals("DETERMINISTIC", ignoreCase = true) -> ProposalSource.Deterministic
                            str.startsWith("AGENT", ignoreCase = true) -> {
                                val ref = str.substringAfter(":", "").ifBlank { str }
                                ProposalSource.Agent(ref)
                            }
                            else -> null
                        }
                    }
                    val rationale = s.string("rationale") ?: proposal?.rationale

                    DiscoveryScreenUi(
                        screenId = s.string("screenId").orEmpty(),
                        moduleId = s.string("moduleId").orEmpty(),
                        title = s.string("title").orEmpty(),
                        widget = s.string("widget").orEmpty(),
                        sampleRows = arr2(s, "sampleRows"),
                        interactive = s.obj("interactive")?.let { runCatching { InteractiveScreenCodec.decode(it) }.getOrNull() },
                        proposal = proposal,
                        rationale = rationale,
                        source = source
                    )
                },
                portLabels = stringMap("portLabels"),
                slotLabels = stringMap("slotLabels"),
                interview = parsedInterview,
                nextQuestion = parsedNextQuestion
            )
        }
    }
}

/** Format label tampilan sumber untuk lencana UI. */
val ProposalSource.displayName: String
    get() = when (this) {
        is ProposalSource.Pack -> "Pack"
        is ProposalSource.Deterministic -> "Deterministik"
        is ProposalSource.Agent -> {
            val modelName = agentRef.substringAfterLast('/').ifBlank { agentRef }
            "Agent · $modelName"
        }
    }

/** Label tampilan asal modul untuk lencana wawancara. */
val ModuleOrigin.displayName: String
    get() = when (this) {
        ModuleOrigin.REUSE_PLATFORM -> "Pakai Ulang Platform"
        ModuleOrigin.REUSE_PACK -> "Pakai Ulang Pack"
        ModuleOrigin.EXTEND -> "Kembangkan"
        ModuleOrigin.NEW -> "Baru"
    }


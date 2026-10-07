package com.eventverse.app.presentation.discovery.interview

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.eventverse.app.domain.discovery.interview.Basis
import com.eventverse.app.domain.discovery.interview.BasisRef
import com.eventverse.app.domain.discovery.interview.BusinessProfile
import com.eventverse.app.domain.discovery.interview.Confirmation
import com.eventverse.app.domain.discovery.interview.DivisionCode
import com.eventverse.app.domain.discovery.interview.DivisionDraft
import com.eventverse.app.domain.discovery.interview.InterviewAnswer
import com.eventverse.app.domain.discovery.interview.InterviewQuestion
import com.eventverse.app.domain.discovery.interview.InterviewSession
import com.eventverse.app.domain.discovery.interview.InterviewStep
import com.eventverse.app.domain.discovery.interview.ItemSource
import com.eventverse.app.domain.discovery.interview.ModuleHandoff
import com.eventverse.app.domain.discovery.interview.ModuleOrigin
import com.eventverse.app.domain.discovery.interview.RequirementSpec
import com.eventverse.app.domain.discovery.interview.RoleDraft
import com.eventverse.app.domain.discovery.interview.RoleKey
import com.eventverse.app.domain.discovery.interview.RoleModuleLink
import com.eventverse.app.domain.pack.ModuleId
import com.eventverse.app.domain.pack.PortType

/** Status saran konsultan (fase F0-F2). */
enum class ConsultantSuggestionStatus { PENDING, ACCEPTED, REJECTED, MODIFIED }

/** Model kartu saran konsultan. */
data class ConsultantSuggestion(
    val id: String,
    val title: String,
    val rationale: String,
    val basisRef: String,
    val status: ConsultantSuggestionStatus = ConsultantSuggestionStatus.PENDING,
    val recommendedModuleId: ModuleId? = null
)

/**
 * State holder untuk sesi wawancara di UI.
 * Mengelola perubahan divisi, peran, modul, fitur, sambungan, profil bisnis, dan jejak giliran secara reaktif.
 */
class InterviewSessionState(
    initialSession: InterviewSession? = null,
    initialQuestion: InterviewQuestion? = null,
    val draftId: String = "",
    val narrative: String = ""
) {
    var step by mutableStateOf(initialSession?.step ?: InterviewStep.G1_DIVISI)
    var turnNumber by mutableStateOf(1)
    var currentQuestion by mutableStateOf(initialQuestion)
    var busy by mutableStateOf(false)
    var errorMessage by mutableStateOf<String?>(null)
    val version = initialSession?.version ?: InterviewSession.BASED_ON_STORY

    var profile by mutableStateOf(initialSession?.profile)
    val specs = mutableStateListOf<RequirementSpec>().apply { addAll(initialSession?.specs.orEmpty()) }
    val divisions = mutableStateListOf<DivisionDraft>().apply { addAll(initialSession?.divisions.orEmpty()) }
    val roles = mutableStateListOf<RoleDraft>().apply { addAll(initialSession?.roles.orEmpty()) }
    val links = mutableStateListOf<RoleModuleLink>().apply { addAll(initialSession?.links.orEmpty()) }
    val handoffs = mutableStateListOf<ModuleHandoff>().apply { addAll(initialSession?.handoffs.orEmpty()) }
    val answers = mutableStateListOf<InterviewAnswer>().apply { addAll(initialSession?.answers.orEmpty()) }
    val consultantSuggestions = mutableStateListOf<ConsultantSuggestion>()

    init {
        // Inisialisasi saran konsultan awal bila belum ada
        if (narrative.isNotBlank() && consultantSuggestions.isEmpty()) {
            consultantSuggestions.add(
                ConsultantSuggestion(
                    id = "sug_qc",
                    title = "Otomasi Serah-Terima Antar Unit",
                    rationale = "Alur operasional akan lebih tertib bila dokumen serah-terima divalidasi langsung.",
                    basisRef = "Berdasarkan narasi kebutuhan Anda",
                    status = ConsultantSuggestionStatus.PENDING,
                    recommendedModuleId = ModuleId("qc_inspection")
                )
            )
        }
        syncTurnNumber()
    }

    private fun syncTurnNumber() {
        val consultantTurns = answers.count { it.step.isConsultant }
        turnNumber = when (step) {
            InterviewStep.F0_BISNIS -> 1
            InterviewStep.F1_TUJUAN -> 2
            InterviewStep.F2_SPEK -> 3
            InterviewStep.G1_DIVISI -> 1 + consultantTurns
            InterviewStep.G2_PERAN -> 2 + consultantTurns
            InterviewStep.G3_MODUL -> 3 + consultantTurns
            InterviewStep.G4_SAMBUNGAN -> 4 + consultantTurns
            InterviewStep.G5_RINGKASAN, InterviewStep.DONE -> 5 + consultantTurns
        }
    }

    /** Menghasilkan slug aman untuk DivisionCode dan RoleKey. */
    fun toSlug(raw: String, prefix: String = "item"): String {
        val clean = raw.trim().lowercase()
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
        val valid = if (clean.isEmpty() || !clean.first().isLetter()) "${prefix}_$clean" else clean
        return valid.take(64).trimEnd('_').ifBlank { prefix }
    }

    // --- Aksi G1 Divisi ---
    fun addDivision(name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        val slug = toSlug(trimmed, "div")
        if (divisions.none { it.code.value == slug }) {
            val ref = BasisRef(Basis.JAWABAN, answerId = "turn_$turnNumber")
            divisions.add(DivisionDraft(DivisionCode(slug), trimmed, ItemSource.ANSWER, ref))
        }
    }

    fun renameDivision(code: DivisionCode, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isBlank()) return
        val idx = divisions.indexOfFirst { it.code == code }
        if (idx >= 0) {
            val ref = BasisRef(Basis.JAWABAN, answerId = "turn_$turnNumber")
            divisions[idx] = divisions[idx].copy(name = trimmed, source = ItemSource.ANSWER, basisRef = ref)
        }
    }

    fun removeDivision(code: DivisionCode) {
        divisions.removeAll { it.code == code }
        roles.removeAll { it.divisionCode == code }
    }

    // --- Aksi G2 Peran ---
    fun addRole(label: String, divisionCode: DivisionCode, isHead: Boolean = false) {
        val trimmed = label.trim()
        if (trimmed.isBlank()) return
        val key = toSlug(trimmed, "role")
        if (roles.none { it.roleKey.value == key }) {
            if (isHead) {
                // Pastikan hanya satu kepala divisi
                setHeadOfDivision(divisionCode, null)
            }
            val ref = BasisRef(Basis.JAWABAN, answerId = "turn_$turnNumber")
            roles.add(RoleDraft(RoleKey(key), trimmed, divisionCode, ItemSource.ANSWER, isHead, ref))
        }
    }

    fun renameRole(roleKey: RoleKey, newLabel: String) {
        val trimmed = newLabel.trim()
        if (trimmed.isBlank()) return
        val idx = roles.indexOfFirst { it.roleKey == roleKey }
        if (idx >= 0) {
            val ref = BasisRef(Basis.JAWABAN, answerId = "turn_$turnNumber")
            roles[idx] = roles[idx].copy(label = trimmed, source = ItemSource.ANSWER, basisRef = ref)
        }
    }

    fun removeRole(roleKey: RoleKey) {
        roles.removeAll { it.roleKey == roleKey }
        links.removeAll { it.roleKey == roleKey }
    }

    fun toggleRoleHead(roleKey: RoleKey) {
        val r = roles.firstOrNull { it.roleKey == roleKey } ?: return
        val targetHead = !r.isHead
        if (targetHead) {
            setHeadOfDivision(r.divisionCode, roleKey)
        } else {
            val idx = roles.indexOfFirst { it.roleKey == roleKey }
            if (idx >= 0) roles[idx] = r.copy(isHead = false)
        }
    }

    private fun setHeadOfDivision(divCode: DivisionCode, headKey: RoleKey?) {
        for (i in roles.indices) {
            if (roles[i].divisionCode == divCode) {
                roles[i] = roles[i].copy(isHead = roles[i].roleKey == headKey)
            }
        }
    }

    // --- Aksi G3 Modul & Fitur ---
    fun confirmLink(roleKey: RoleKey, moduleId: ModuleId) {
        val idx = links.indexOfFirst { it.roleKey == roleKey && it.moduleId == moduleId }
        if (idx >= 0) {
            val currentRef = links[idx].basisRef ?: BasisRef(Basis.JAWABAN, answerId = "turn_$turnNumber")
            links[idx] = links[idx].copy(confirmed = Confirmation.CONFIRMED, basisRef = currentRef)
        }
    }

    fun changeModuleForRole(roleKey: RoleKey, oldModuleId: ModuleId, newModuleId: ModuleId, newOrigin: ModuleOrigin) {
        val idx = links.indexOfFirst { it.roleKey == roleKey && it.moduleId == oldModuleId }
        if (idx >= 0) {
            val ref = BasisRef(Basis.JAWABAN, answerId = "turn_$turnNumber")
            links[idx] = links[idx].copy(
                moduleId = newModuleId,
                origin = newOrigin,
                confirmed = Confirmation.CHANGED,
                basisRef = ref
            )
        }
    }

    fun addFeatureToLink(roleKey: RoleKey, moduleId: ModuleId, feature: String) {
        val trimmed = feature.trim()
        if (trimmed.isBlank()) return
        val idx = links.indexOfFirst { it.roleKey == roleKey && it.moduleId == moduleId }
        if (idx >= 0) {
            val current = links[idx].features
            if (trimmed !in current) {
                links[idx] = links[idx].copy(
                    features = current + trimmed,
                    confirmed = Confirmation.CHANGED
                )
            }
        }
    }

    fun removeFeatureFromLink(roleKey: RoleKey, moduleId: ModuleId, feature: String) {
        val idx = links.indexOfFirst { it.roleKey == roleKey && it.moduleId == moduleId }
        if (idx >= 0) {
            links[idx] = links[idx].copy(
                features = links[idx].features.filter { it != feature },
                confirmed = Confirmation.CHANGED
            )
        }
    }

    // --- Aksi G4 Sambungan ---
    fun confirmHandoff(from: ModuleId, to: ModuleId, portType: PortType) {
        val idx = handoffs.indexOfFirst { it.from == from && it.to == to && it.portType == portType }
        if (idx >= 0) {
            val currentRef = handoffs[idx].basisRef ?: BasisRef(Basis.JAWABAN, answerId = "turn_$turnNumber")
            handoffs[idx] = handoffs[idx].copy(confirmed = Confirmation.CONFIRMED, basisRef = currentRef)
        }
    }

    fun removeHandoff(from: ModuleId, to: ModuleId, portType: PortType) {
        handoffs.removeAll { it.from == from && it.to == to && it.portType == portType }
    }

    fun addHandoff(from: ModuleId, to: ModuleId, portType: PortType) {
        if (handoffs.none { it.from == from && it.to == to && it.portType == portType }) {
            val ref = BasisRef(Basis.JAWABAN, answerId = "turn_$turnNumber")
            handoffs.add(ModuleHandoff(from, to, portType, Confirmation.CONFIRMED, ref))
        }
    }

    // --- Aksi Konsultan F0-F2 ---
    fun acceptSuggestion(id: String) {
        val idx = consultantSuggestions.indexOfFirst { it.id == id }
        if (idx >= 0) {
            val sug = consultantSuggestions[idx]
            consultantSuggestions[idx] = sug.copy(status = ConsultantSuggestionStatus.ACCEPTED)
            sug.recommendedModuleId?.let { modId ->
                val targetRole = roles.firstOrNull { it.isHead } ?: roles.firstOrNull()
                if (targetRole != null && links.none { it.roleKey == targetRole.roleKey && it.moduleId == modId }) {
                    val ref = BasisRef(Basis.SARAN_DITERIMA, answerId = "turn_$turnNumber")
                    links.add(
                        RoleModuleLink(
                            roleKey = targetRole.roleKey,
                            moduleId = modId,
                            origin = ModuleOrigin.NEW,
                            confirmed = Confirmation.CONFIRMED,
                            basisRef = ref
                        )
                    )
                }
            }
        }
    }

    fun rejectSuggestion(id: String) {
        val idx = consultantSuggestions.indexOfFirst { it.id == id }
        if (idx >= 0) {
            consultantSuggestions[idx] = consultantSuggestions[idx].copy(status = ConsultantSuggestionStatus.REJECTED)
        }
    }

    // --- Aksi Konsultan F0-F2 Profil & Spek ---
    fun updateProfileSummary(summary: String) {
        val trimmed = summary.trim()
        if (trimmed.isNotBlank()) profile = profile?.copy(summary = trimmed) ?: BusinessProfile(trimmed)
    }

    fun addGoal(goal: String) {
        val trimmed = goal.trim()
        if (trimmed.isNotBlank()) {
            val curr = profile ?: BusinessProfile(narrative.ifBlank { "Usaha Pengguna" })
            if (trimmed !in curr.goals) profile = curr.copy(goals = curr.goals + trimmed)
        }
    }

    fun removeGoal(goal: String) {
        profile?.let { profile = it.copy(goals = it.goals.filter { g -> g != goal }) }
    }

    fun addPainPoint(painPoint: String) {
        val trimmed = painPoint.trim()
        if (trimmed.isNotBlank()) {
            val curr = profile ?: BusinessProfile(narrative.ifBlank { "Usaha Pengguna" })
            if (trimmed !in curr.painPoints) profile = curr.copy(painPoints = curr.painPoints + trimmed)
        }
    }

    fun removePainPoint(painPoint: String) {
        profile?.let { profile = it.copy(painPoints = it.painPoints.filter { p -> p != painPoint }) }
    }

    fun addOrUpdateSpec(spec: RequirementSpec) {
        val idx = specs.indexOfFirst { it.areaKey == spec.areaKey }
        if (idx >= 0) specs[idx] = spec else specs.add(spec)
    }

    fun removeSpec(areaKey: RoleKey) {
        specs.removeAll { it.areaKey == areaKey }
    }

    // --- Alur Antar Giliran ---
    fun acceptAllGuesses() {
        for (i in links.indices) {
            links[i] = links[i].copy(confirmed = Confirmation.SKIPPED)
        }
        for (i in handoffs.indices) {
            handoffs[i] = handoffs[i].copy(confirmed = Confirmation.SKIPPED)
        }
        answers.add(
            InterviewAnswer(
                turn = turnNumber,
                step = step,
                questionId = "terima_semua",
                outcome = Confirmation.SKIPPED,
                text = "Terima semua tebakan"
            )
        )
        step = InterviewStep.G5_RINGKASAN
        syncTurnNumber()
    }

    fun nextTurn() {
        answers.add(
            InterviewAnswer(
                turn = turnNumber,
                step = step,
                questionId = "turn_$turnNumber",
                outcome = Confirmation.CONFIRMED
            )
        )
        step = when (step) {
            InterviewStep.F0_BISNIS -> InterviewStep.F1_TUJUAN
            InterviewStep.F1_TUJUAN -> InterviewStep.F2_SPEK
            InterviewStep.F2_SPEK -> InterviewStep.G1_DIVISI
            InterviewStep.G1_DIVISI -> InterviewStep.G2_PERAN
            InterviewStep.G2_PERAN -> InterviewStep.G3_MODUL
            InterviewStep.G3_MODUL -> InterviewStep.G4_SAMBUNGAN
            InterviewStep.G4_SAMBUNGAN -> InterviewStep.G5_RINGKASAN
            InterviewStep.G5_RINGKASAN, InterviewStep.DONE -> InterviewStep.DONE
        }
        syncTurnNumber()
    }

    fun previousTurn() {
        step = when (step) {
            InterviewStep.F0_BISNIS -> InterviewStep.F0_BISNIS
            InterviewStep.F1_TUJUAN -> InterviewStep.F0_BISNIS
            InterviewStep.F2_SPEK -> InterviewStep.F1_TUJUAN
            InterviewStep.G1_DIVISI -> if (answers.any { it.step.isConsultant }) InterviewStep.F2_SPEK else InterviewStep.G1_DIVISI
            InterviewStep.G2_PERAN -> InterviewStep.G1_DIVISI
            InterviewStep.G3_MODUL -> InterviewStep.G2_PERAN
            InterviewStep.G4_SAMBUNGAN -> InterviewStep.G3_MODUL
            InterviewStep.G5_RINGKASAN, InterviewStep.DONE -> InterviewStep.G4_SAMBUNGAN
        }
        syncTurnNumber()
    }

    fun goToStep(targetStep: InterviewStep) {
        step = targetStep
        syncTurnNumber()
    }

    fun toSession(): InterviewSession = InterviewSession(
        step = step,
        divisions = divisions.toList(),
        roles = roles.toList(),
        links = links.toList(),
        handoffs = handoffs.toList(),
        answers = answers.toList(),
        version = version,
        narrative = narrative.ifBlank { null },
        profile = profile,
        specs = specs.toList()
    )
}

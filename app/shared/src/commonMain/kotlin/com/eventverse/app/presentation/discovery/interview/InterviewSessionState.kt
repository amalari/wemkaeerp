package com.eventverse.app.presentation.discovery.interview

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
    val status: ConsultantSuggestionStatus = ConsultantSuggestionStatus.PENDING
)

/**
 * State holder untuk sesi wawancara di UI.
 * Mengelola perubahan divisi, peran, modul, fitur, sambungan, dan jejak giliran secara reaktif.
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

    val divisions = mutableStateListOf<DivisionDraft>().apply {
        addAll(initialSession?.divisions.orEmpty())
    }

    val roles = mutableStateListOf<RoleDraft>().apply {
        addAll(initialSession?.roles.orEmpty())
    }

    val links = mutableStateListOf<RoleModuleLink>().apply {
        addAll(initialSession?.links.orEmpty())
    }

    val handoffs = mutableStateListOf<ModuleHandoff>().apply {
        addAll(initialSession?.handoffs.orEmpty())
    }

    val answers = mutableStateListOf<InterviewAnswer>().apply {
        addAll(initialSession?.answers.orEmpty())
    }

    val consultantSuggestions = mutableStateListOf<ConsultantSuggestion>()

    init {
        // Inisialisasi saran konsultan awal bila belum ada
        if (narrative.isNotBlank() && consultantSuggestions.isEmpty()) {
            consultantSuggestions.add(
                ConsultantSuggestion(
                    id = "saran-1",
                    title = "Otomasi Serah-Terima Antar Unit",
                    rationale = "Alur operasional akan lebih tertib bila dokumen serah-terima divalidasi langsung.",
                    basisRef = "Berdasarkan narasi kebutuhan Anda",
                    status = ConsultantSuggestionStatus.PENDING
                )
            )
        }
        syncTurnNumber()
    }

    private fun syncTurnNumber() {
        turnNumber = when (step) {
            InterviewStep.G1_DIVISI -> 1
            InterviewStep.G2_PERAN -> 2
            InterviewStep.G3_MODUL -> 3
            InterviewStep.G4_SAMBUNGAN -> 4
            InterviewStep.G5_RINGKASAN, InterviewStep.DONE -> 5
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
            divisions.add(DivisionDraft(DivisionCode(slug), trimmed, ItemSource.ANSWER))
        }
    }

    fun renameDivision(code: DivisionCode, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isBlank()) return
        val idx = divisions.indexOfFirst { it.code == code }
        if (idx >= 0) {
            divisions[idx] = divisions[idx].copy(name = trimmed, source = ItemSource.ANSWER)
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
            roles.add(RoleDraft(RoleKey(key), trimmed, divisionCode, ItemSource.ANSWER, isHead))
        }
    }

    fun renameRole(roleKey: RoleKey, newLabel: String) {
        val trimmed = newLabel.trim()
        if (trimmed.isBlank()) return
        val idx = roles.indexOfFirst { it.roleKey == roleKey }
        if (idx >= 0) {
            roles[idx] = roles[idx].copy(label = trimmed, source = ItemSource.ANSWER)
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
            links[idx] = links[idx].copy(confirmed = Confirmation.CONFIRMED)
        }
    }

    fun changeModuleForRole(roleKey: RoleKey, oldModuleId: ModuleId, newModuleId: ModuleId, newOrigin: ModuleOrigin) {
        val idx = links.indexOfFirst { it.roleKey == roleKey && it.moduleId == oldModuleId }
        if (idx >= 0) {
            links[idx] = links[idx].copy(
                moduleId = newModuleId,
                origin = newOrigin,
                confirmed = Confirmation.CHANGED
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
            handoffs[idx] = handoffs[idx].copy(confirmed = Confirmation.CONFIRMED)
        }
    }

    fun removeHandoff(from: ModuleId, to: ModuleId, portType: PortType) {
        handoffs.removeAll { it.from == from && it.to == to && it.portType == portType }
    }

    fun addHandoff(from: ModuleId, to: ModuleId, portType: PortType) {
        if (handoffs.none { it.from == from && it.to == to && it.portType == portType }) {
            handoffs.add(ModuleHandoff(from, to, portType, Confirmation.CONFIRMED))
        }
    }

    // --- Aksi Konsultan F0-F2 ---
    fun acceptSuggestion(id: String) {
        val idx = consultantSuggestions.indexOfFirst { it.id == id }
        if (idx >= 0) {
            consultantSuggestions[idx] = consultantSuggestions[idx].copy(status = ConsultantSuggestionStatus.ACCEPTED)
        }
    }

    fun rejectSuggestion(id: String) {
        val idx = consultantSuggestions.indexOfFirst { it.id == id }
        if (idx >= 0) {
            consultantSuggestions[idx] = consultantSuggestions[idx].copy(status = ConsultantSuggestionStatus.REJECTED)
        }
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
            InterviewStep.G1_DIVISI -> InterviewStep.G1_DIVISI
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
        answers = answers.toList()
    )
}

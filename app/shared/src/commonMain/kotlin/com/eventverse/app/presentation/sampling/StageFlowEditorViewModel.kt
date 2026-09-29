package com.eventverse.app.presentation.sampling

import com.eventverse.app.domain.stageflow.IndustryTemplateCode
import com.eventverse.app.domain.stageflow.StageCode
import com.eventverse.app.domain.stageflow.StageDefinition
import com.eventverse.app.domain.stageflow.StageKind
import com.eventverse.app.infrastructure.api.NewStageDraft
import com.eventverse.app.infrastructure.api.StageFlowApiClient
import com.eventverse.app.infrastructure.api.StageFlowRemoteDataSource
import com.eventverse.app.infrastructure.api.TenantStageFlowView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StageFlowEditorUiState(
    val template: IndustryTemplateCode? = null,
    val stages: List<StageDefinition> = emptyList(),
    val isBusy: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false
)

sealed interface StageFlowEditorUiEvent {
    data object Load : StageFlowEditorUiEvent
    data class Add(val displayName: String, val shortLabel: String, val after: StageCode, val isOperatorDesk: Boolean) : StageFlowEditorUiEvent
    data class Remove(val code: StageCode) : StageFlowEditorUiEvent
    data class MoveUp(val code: StageCode) : StageFlowEditorUiEvent
    data class MoveDown(val code: StageCode) : StageFlowEditorUiEvent
    data class Rename(val code: StageCode, val displayName: String, val shortLabel: String) : StageFlowEditorUiEvent
    data class Reset(val template: IndustryTemplateCode) : StageFlowEditorUiEvent
    data object DismissMessage : StageFlowEditorUiEvent
}

/**
 * Editor kerangka tahap pabrik (TRD-FLOW-001 Tahap 3c). Aturan kerangka ditegakkan server
 * (`TenantStageFlow`); ViewModel ini hanya menerjemahkan niat layar ke operasi API dan
 * menampilkan penolakannya apa adanya. [onChanged] memberi tahu papan bahwa kolomnya berubah.
 */
class StageFlowEditorViewModel(
    private val remote: StageFlowRemoteDataSource = StageFlowApiClient(),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main),
    private val onChanged: (List<StageDefinition>) -> Unit = {}
) {
    private val _uiState = MutableStateFlow(StageFlowEditorUiState())
    val uiState: StateFlow<StageFlowEditorUiState> = _uiState.asStateFlow()

    fun onEvent(event: StageFlowEditorUiEvent) {
        val stages = _uiState.value.stages
        when (event) {
            StageFlowEditorUiEvent.Load -> run(null) { remote.fetchTenantFlow() }
            is StageFlowEditorUiEvent.Add -> {
                val code = suggestStageCode(event.displayName, stages.map { it.code }.toSet())
                    ?: return fail("Nama tahap harus berisi huruf")
                run("Tahap \"${event.displayName.trim()}\" ditambahkan") {
                    remote.addStage(NewStageDraft(code, event.displayName.trim(), event.shortLabel.trim(), event.after, event.isOperatorDesk))
                }
            }
            is StageFlowEditorUiEvent.Remove -> run("Tahap dihapus") { remote.removeStage(event.code) }
            is StageFlowEditorUiEvent.MoveUp -> moveUpTarget(stages, event.code)
                ?.let { after -> run(null) { remote.moveStage(event.code, after) } }
            is StageFlowEditorUiEvent.MoveDown -> moveDownTarget(stages, event.code)
                ?.let { after -> run(null) { remote.moveStage(event.code, after) } }
            is StageFlowEditorUiEvent.Rename -> run("Nama tahap disimpan") {
                remote.renameStage(event.code, event.displayName.trim(), event.shortLabel.trim())
            }
            is StageFlowEditorUiEvent.Reset -> run("Kerangka diganti ke template ${event.template.displayName}") {
                remote.resetTo(event.template)
            }
            StageFlowEditorUiEvent.DismissMessage -> _uiState.update { it.copy(message = null, isError = false) }
        }
    }

    private fun run(success: String?, call: suspend () -> Result<TenantStageFlowView>) {
        _uiState.update { it.copy(isBusy = true) }
        scope.launch {
            call()
                .onSuccess { flow ->
                    _uiState.update { it.copy(template = flow.template, stages = flow.stages, isBusy = false, message = success, isError = false) }
                    onChanged(flow.stages)
                }
                .onFailure { err -> _uiState.update { it.copy(isBusy = false, message = err.message ?: "Perubahan ditolak", isError = true) } }
        }
    }

    private fun fail(message: String) = _uiState.update { it.copy(message = message, isError = true) }
}

/** Naik satu = sisip sesudah tahap dua langkah di atas; tahap masuk tidak bisa dilangkahi. */
internal fun moveUpTarget(stages: List<StageDefinition>, code: StageCode): StageCode? {
    val i = stages.indexOfFirst { it.code == code }
    if (i < 2 || stages[i - 1].kind != StageKind.WORK) return null
    return stages[i - 2].code
}

/** Turun satu = sisip sesudah tahap tepat di bawahnya; tahap keluar tidak bisa dilangkahi. */
internal fun moveDownTarget(stages: List<StageDefinition>, code: StageCode): StageCode? {
    val i = stages.indexOfFirst { it.code == code }
    val below = stages.getOrNull(i + 1) ?: return null
    return below.code.takeIf { i >= 0 && below.kind == StageKind.WORK }
}

/**
 * Kode tahap dari nama yang diketik admin ("Aplikasi Kain" → `APLIKASI_KAIN`), unik terhadap
 * [taken]. Kode dipakai sebagai kunci tersimpan, jadi admin tidak pernah mengetiknya sendiri.
 */
internal fun suggestStageCode(displayName: String, taken: Set<StageCode>): StageCode? {
    val base = displayName.uppercase().replace(Regex("[^A-Z0-9]+"), "_").trim('_').trimStart { it.isDigit() || it == '_' }.take(40)
    if (base.length < 2) return null
    return generateSequence(1) { it + 1 }
        .map { n -> StageCode(if (n == 1) base else "${base}_$n") }
        .first { it !in taken }
}

package com.eventverse.app.presentation.crm.leaddraft

import com.eventverse.app.domain.crm.prefill.usecases.ExtractLeadDraftUseCase
import com.eventverse.app.infrastructure.api.LeadDraftApiClient
import com.eventverse.app.infrastructure.api.LeadDraftDisabledException
import com.eventverse.app.infrastructure.api.LeadDraftGateway
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Bagian "Isi dengan AI" dialog lead. Hanya meminta draf dan memancarkannya sebagai [LeadDraftUiEffect.Apply];
 * dialog yang menerapkan ke field, dan user yang menekan Simpan. Terpisah dari `CrmViewModel` supaya
 * alur simpan lead tidak berubah sama sekali.
 */
class LeadDraftViewModel(
    private val gateway: LeadDraftGateway = LeadDraftApiClient(),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main),
) {
    private val _uiState = MutableStateFlow(LeadDraftUiState())
    val uiState: StateFlow<LeadDraftUiState> = _uiState.asStateFlow()

    private val _effects = Channel<LeadDraftUiEffect>(Channel.BUFFERED)
    val effects: Flow<LeadDraftUiEffect> = _effects.receiveAsFlow()

    fun onEvent(event: LeadDraftUiEvent) {
        when (event) {
            LeadDraftUiEvent.Load -> scope.launch {
                // Gagal memuat = bagian AI tetap tersembunyi (enabled null). Form manual tidak terganggu.
                gateway.settings().onSuccess { s -> _uiState.update { it.copy(enabled = s.leadDraftEnabled, canManage = s.canManage) } }
            }
            is LeadDraftUiEvent.UpdateText -> _uiState.update { it.copy(text = event.text.take(ExtractLeadDraftUseCase.MAX_TEXT_LENGTH)) }
            LeadDraftUiEvent.Extract -> extract()
            LeadDraftUiEvent.Enable -> scope.launch {
                if (!_uiState.value.canManage) return@launch
                gateway.setEnabled(true).fold(
                    { s -> _uiState.update { it.copy(enabled = s.leadDraftEnabled, error = null) } },
                    { e -> _uiState.update { it.copy(error = e.message) } },
                )
            }
        }
    }

    private fun extract() {
        val state = _uiState.value
        if (!state.canExtract) return
        _uiState.update { it.copy(isExtracting = true, error = null, issues = emptyList()) }
        scope.launch {
            gateway.draft(state.text).fold(
                onSuccess = { draft ->
                    _uiState.update { it.copy(isExtracting = false, issues = draft.issues, partial = draft.partial) }
                    _effects.trySend(LeadDraftUiEffect.Apply(draft))
                },
                onFailure = { e ->
                    if (e is LeadDraftDisabledException) _uiState.update { it.copy(isExtracting = false, enabled = false) }
                    else _uiState.update { it.copy(isExtracting = false, error = "Gagal membuat draf: ${e.message ?: "tidak diketahui"}") }
                },
            )
        }
    }
}

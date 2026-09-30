package com.eventverse.app.presentation.help

import com.eventverse.app.domain.help.usecases.AskHelpUseCase
import com.eventverse.app.infrastructure.api.HelpApiClient
import com.eventverse.app.infrastructure.api.HelpGateway
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
 * Chat AI helper (TRD-HELP-001 FR-7). Hanya meneruskan pertanyaan ke server dan menyimpan riwayat sesi — pemilihan
 * tutorial dan penyaringan wewenang terjadi di server. "Mulai tutorial" dipancarkan sebagai [HelpChatUiEffect]
 * supaya ViewModel ini tidak perlu tahu tentang coach mark.
 *
 * Riwayat hanya di memori (Non-Goal: riwayat persisten), dan setiap pertanyaan dijawab tanpa konteks pesan
 * sebelumnya — sengaja, supaya jawaban selalu berpijak pada kandidat pertanyaan itu sendiri.
 */
class HelpChatViewModel(
    private val gateway: HelpGateway = HelpApiClient(),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main),
) {
    private val _uiState = MutableStateFlow(HelpChatUiState())
    val uiState: StateFlow<HelpChatUiState> = _uiState.asStateFlow()

    private val _effects = Channel<HelpChatUiEffect>(Channel.BUFFERED)
    val effects: Flow<HelpChatUiEffect> = _effects.receiveAsFlow()

    private var nextId = 0

    fun onEvent(event: HelpChatUiEvent) {
        when (event) {
            is HelpChatUiEvent.UpdateDraft -> _uiState.update { it.copy(draft = event.text.take(AskHelpUseCase.MAX_QUESTION_LENGTH)) }
            is HelpChatUiEvent.Send -> send(event)
            is HelpChatUiEvent.StartSuggestion -> _effects.trySend(HelpChatUiEffect.StartTutorial(event.suggestion))
            is HelpChatUiEvent.RunAction -> _effects.trySend(HelpChatUiEffect.RunAction(event.action))
            HelpChatUiEvent.DismissError -> _uiState.update { it.copy(error = null) }
        }
    }

    private fun send(event: HelpChatUiEvent.Send) {
        val state = _uiState.value
        if (!state.canSend) return
        val question = state.draft.trim()
        val asked = HelpChatMessage(nextId++, HelpChatRole.USER, question)
        _uiState.update { it.copy(messages = it.messages + asked, draft = "", isSending = true, error = null) }
        scope.launch {
            gateway.ask(question, event.currentModule).fold(
                onSuccess = { result ->
                    val reply = HelpChatMessage(nextId++, HelpChatRole.ASSISTANT, result.answer, result.suggestion, result.alternatives.take(MAX_ALTERNATIVES), result.action)
                    _uiState.update { it.copy(messages = it.messages + reply, isSending = false) }
                },
                onFailure = { e ->
                    // Pertanyaan dikembalikan ke kotak input (bukan digandakan di riwayat) supaya bisa dikirim ulang.
                    _uiState.update { it.copy(messages = it.messages - asked, isSending = false, draft = question, error = "Gagal menghubungi asisten: ${e.message ?: "tidak diketahui"}") }
                },
            )
        }
    }

    private companion object {
        const val MAX_ALTERNATIVES = 2
    }
}

package com.eventverse.app.presentation.help

import com.eventverse.app.domain.help.usecases.HelpSuggestion
import com.eventverse.app.domain.pack.ModuleId

/** Pengirim pesan chat bantuan. Konsep teknis percakapan, sama untuk semua tenant → enum. */
enum class HelpChatRole { USER, ASSISTANT }

data class HelpChatMessage(
    val id: Int,
    val role: HelpChatRole,
    val text: String,
    val suggestion: HelpSuggestion? = null,
    val alternatives: List<HelpSuggestion> = emptyList(),
    val action: com.eventverse.app.domain.help.HelpAction? = null,
)

data class HelpChatUiState(
    val messages: List<HelpChatMessage> = emptyList(),
    val draft: String = "",
    val isSending: Boolean = false,
    val error: String? = null,
) {
    val canSend: Boolean get() = draft.isNotBlank() && !isSending
}

sealed interface HelpChatUiEvent {
    data class UpdateDraft(val text: String) : HelpChatUiEvent
    /** [currentModule] = layar yang sedang dibuka saat tombol kirim ditekan — konteks peringkat, bukan akses. */
    data class Send(val currentModule: ModuleId?) : HelpChatUiEvent
    data class StartSuggestion(val suggestion: HelpSuggestion) : HelpChatUiEvent
    data class RunAction(val action: com.eventverse.app.domain.help.HelpAction) : HelpChatUiEvent
    data object DismissError : HelpChatUiEvent
}

sealed interface HelpChatUiEffect {
    data class StartTutorial(val suggestion: HelpSuggestion) : HelpChatUiEffect
    data class RunAction(val action: com.eventverse.app.domain.help.HelpAction) : HelpChatUiEffect
}

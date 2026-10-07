package com.eventverse.app.presentation.discovery.interview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.eventverse.app.domain.discovery.interview.InterviewStep
import com.eventverse.app.presentation.designsystem.ClayButton
import com.eventverse.app.presentation.designsystem.ClayButtonStyle
import com.eventverse.app.presentation.designsystem.ClayCard
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.discovery.DiscoveryDraftUi
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Panel utama Wawancara Discovery (Fase A).
 * Menampilkan alur giliran G1 hingga G5, saran konsultan, dan jalur aman bila ada galat.
 */
@Composable
fun DiscoveryInterviewPane(
    state: InterviewSessionState,
    draft: DiscoveryDraftUi,
    onComplete: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ClaySpacing.Lg)
    ) {
        // Header Giliran
        InterviewTurnHeader(
            step = state.step,
            turnNumber = state.turnNumber,
            onAcceptAll = { state.acceptAllGuesses() },
            onSkip = onSkip
        )

        // Jalur Aman: Pesan Galat & Tombol Keluar (A5)
        state.errorMessage?.let { error ->
            ClayCard(modifier = Modifier.fillMaxWidth(), outlineColor = WeMadeColors.Error) {
                Text(
                    text = "Perhatian: $error",
                    color = WeMadeColors.Error,
                    style = MaterialTheme.typography.bodySmall
                )
                ClayButton(
                    text = "Lewati Wawancara & Lanjut ke Draf",
                    onClick = onSkip,
                    style = ClayButtonStyle.Secondary,
                    modifier = Modifier.padding(top = ClaySpacing.Sm)
                )
            }
        }

        // Saran Konsultan (A7) jika ada saran pending di giliran F0-F2 atau G1-G2
        if (state.step.isConsultant || state.step in listOf(InterviewStep.G1_DIVISI, InterviewStep.G2_PERAN)) {
            val pendingSuggestions = state.consultantSuggestions.filter { it.status == ConsultantSuggestionStatus.PENDING }
            pendingSuggestions.forEach { suggestion ->
                StepInterviewConsultantCard(
                    suggestion = suggestion,
                    onAccept = { state.acceptSuggestion(suggestion.id) },
                    onReject = { state.rejectSuggestion(suggestion.id) },
                    onModify = { state.acceptSuggestion(suggestion.id) }
                )
            }
        }

        // Konten Langkah Aktif
        when (state.step) {
            InterviewStep.F0_BISNIS -> StepInterviewF0Bisnis(
                state = state,
                onNext = { state.nextTurn() }
            )
            InterviewStep.F1_TUJUAN -> StepInterviewF1Tujuan(
                state = state,
                onBack = { state.previousTurn() },
                onNext = { state.nextTurn() }
            )
            InterviewStep.F2_SPEK -> StepInterviewF2Spek(
                state = state,
                onBack = { state.previousTurn() },
                onNext = { state.nextTurn() }
            )
            InterviewStep.G1_DIVISI -> StepInterviewG1Divisions(
                state = state,
                onNext = { state.nextTurn() }
            )
            InterviewStep.G2_PERAN -> StepInterviewG2Roles(
                state = state,
                onBack = { state.previousTurn() },
                onNext = { state.nextTurn() }
            )
            InterviewStep.G3_MODUL -> StepInterviewG3Modules(
                state = state,
                availableModules = draft.modules,
                onBack = { state.previousTurn() },
                onNext = { state.nextTurn() }
            )
            InterviewStep.G4_SAMBUNGAN -> StepInterviewG4Handoffs(
                state = state,
                availableModules = draft.modules,
                onBack = { state.previousTurn() },
                onNext = { state.nextTurn() }
            )
            InterviewStep.G5_RINGKASAN, InterviewStep.DONE -> StepInterviewG5Summary(
                state = state,
                onJumpToStep = { state.goToStep(it) },
                onLockAndProceed = { state.complete(onComplete) }
            )
        }
    }
}

package com.eventverse.app.presentation.help

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.domain.pack.ModuleId
import com.eventverse.app.presentation.designsystem.ClayButton
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.ClayTextField
import com.eventverse.app.presentation.theme.WeMadeColors

/** Isi tab "Tanya AI": riwayat sesi, indikator mengetik, dan kotak pertanyaan (TRD-HELP-001 FR-7). */
@Composable
fun HelpChatPanel(viewModel: HelpChatViewModel, currentModule: ModuleId?, modifier: Modifier = Modifier) {
    val state by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    LaunchedEffect(state.messages.size, state.isSending) {
        val last = state.messages.size + (if (state.isSending) 1 else 0) - 1
        if (last >= 0) listState.animateScrollToItem(last)
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth().height(380.dp),
            verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
        ) {
            if (state.messages.isEmpty()) item {
                Text(
                    "Tanyakan apa saja tentang cara memakai aplikasi, misalnya \"gimana cara bikin lead baru?\". " +
                        "Saya akan menunjukkan panduan yang sesuai dengan akses Anda.",
                    fontSize = 13.sp, color = WeMadeColors.OnSurfaceMuted
                )
            }
            items(state.messages, key = { it.id }) { m ->
                HelpMessageBubble(m, onStart = { viewModel.onEvent(HelpChatUiEvent.StartSuggestion(it)) })
            }
            if (state.isSending) item { Text("Asisten sedang mencari panduan…", fontSize = 12.sp, color = WeMadeColors.OnSurfaceMuted) }
        }
        state.error?.let { Text(it, fontSize = 12.sp, color = WeMadeColors.Error) }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
            ClayTextField(
                value = state.draft,
                onValueChange = { viewModel.onEvent(HelpChatUiEvent.UpdateDraft(it)) },
                placeholder = "Tulis pertanyaan…",
                modifier = Modifier.weight(1f)
            )
            ClayButton(
                text = "Kirim",
                onClick = { viewModel.onEvent(HelpChatUiEvent.Send(currentModule)) },
                enabled = state.canSend,
                modifier = Modifier.padding(start = ClaySpacing.Xs)
            )
        }
    }
}

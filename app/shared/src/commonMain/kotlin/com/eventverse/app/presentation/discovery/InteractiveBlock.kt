package com.eventverse.app.presentation.discovery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.eventverse.app.presentation.designsystem.ClayButton
import com.eventverse.app.presentation.designsystem.ClayButtonStyle
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.theme.WeMadeColors

/** Memilih blok menurut jenis state-nya; menampilkan indikator memuat dan banner galat (TRD-PLAT-003, butir A2). */
@Composable
fun InteractiveBlock(state: PlayableState, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
        when (val phase = state.phase) {
            is BlockDataPhase.Loading -> {
                Text(
                    text = "Memuat…",
                    style = MaterialTheme.typography.bodySmall,
                    color = WeMadeColors.OnSurfaceMuted,
                    modifier = Modifier.padding(vertical = ClaySpacing.Xs)
                )
            }
            is BlockDataPhase.Error -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = phase.message,
                        style = MaterialTheme.typography.labelSmall,
                        color = WeMadeColors.Defect,
                        modifier = Modifier.weight(1f)
                    )
                    ClayButton(
                        text = "Coba lagi",
                        style = ClayButtonStyle.Secondary,
                        onClick = { state.retry() }
                    )
                }
            }
            else -> {}
        }

        when (state) {
            is InteractiveKanbanState -> InteractiveKanban(state)
            is InteractiveTableState -> InteractiveTable(state)
            is InteractiveChecklistState -> InteractiveChecklist(state)
            is InteractiveDashboardState -> InteractiveDashboard(state)
            is InteractiveFormState -> InteractiveForm(state)
        }
    }
}

package com.eventverse.app.presentation.discovery

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Memilih blok menurut jenis state-nya; tiap jenis punya file composable sendiri. */
@Composable
fun InteractiveBlock(state: PlayableState, modifier: Modifier = Modifier) {
    when (state) {
        is InteractiveKanbanState -> InteractiveKanban(state, modifier)
        is InteractiveTableState -> InteractiveTable(state, modifier)
        is InteractiveChecklistState -> InteractiveChecklist(state, modifier)
        is InteractiveDashboardState -> InteractiveDashboard(state, modifier)
        is InteractiveFormState -> InteractiveForm(state, modifier)
    }
}

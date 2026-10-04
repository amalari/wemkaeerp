package com.eventverse.app.presentation.discovery

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.eventverse.app.domain.discovery.WidgetKind
import com.eventverse.app.domain.prototype.InteractiveScreen

/** Memilih blok yang bisa dimainkan menurut widget di spec; widget tanpa blok interaktif belum ada di sini. */
@Composable
fun InteractiveBlock(screen: InteractiveScreen, modifier: Modifier = Modifier) {
    when (screen.spec.screens.firstOrNull()?.widget) {
        WidgetKind.KANBAN -> InteractiveKanban(screen, modifier)
        WidgetKind.TABLE -> InteractiveTable(screen, modifier)
        else -> Unit
    }
}

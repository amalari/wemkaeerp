package com.eventverse.app.presentation.discovery

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.zIndex
import com.eventverse.app.domain.prototype.PrototypeRow
import com.eventverse.app.presentation.designsystem.ClayBorder
import com.eventverse.app.presentation.designsystem.ClayShapes
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.clayFlat
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Papan kanban prototype yang bisa dimainkan (TRD-PLAT-003): kartu diseret antar kolom, atau
 * diketuk untuk menu "Pindah ke…" (jalur tanpa drag untuk layar sentuh/aksesibilitas). Aturan
 * pindah datang dari spec; di sini hanya menggambar dan meneruskan gestur.
 */
@Composable
fun InteractiveKanban(state: InteractiveKanbanState, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
            state.config.columns.forEach { column ->
                val cards = state.cards(column)
                val holdsDragged = cards.any { it.id == state.draggedId }
                val hovered = state.hoverColumn == column
                Column(
                    modifier = Modifier
                        .weight(1f)
                        // Kolom yang memegang kartu terseret digambar di atas tetangganya, supaya kartu
                        // tidak "tenggelam" di bawah kolom sebelah saat melintas.
                        .zIndex(if (holdsDragged) 1f else 0f)
                        .onGloballyPositioned { state.registerColumn(column, it.boundsInRoot()) }
                        .clayFlat(
                            shape = ClayShapes.Tile,
                            background = if (hovered) WeMadeColors.PrimaryContainer else WeMadeColors.SurfaceMuted.copy(alpha = 0.14f),
                            outline = if (hovered) WeMadeColors.Primary else WeMadeColors.Outline.copy(alpha = 0.3f),
                            borderWidth = ClayBorder.Medium
                        )
                        .padding(ClaySpacing.Sm),
                    verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
                ) {
                    Text(
                        "$column · ${cards.size}",
                        style = MaterialTheme.typography.labelSmall,
                        color = WeMadeColors.OnSurfaceMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    cards.forEach { card -> KanbanCard(card, state) }
                }
            }
        }
        state.message?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = WeMadeColors.Defect)
        }
        Text(
            "Seret kartu ke kolom lain, atau ketuk untuk memilih kolom.",
            style = MaterialTheme.typography.labelSmall,
            color = WeMadeColors.OnSurfaceMuted
        )
    }
}

@Composable
private fun KanbanCard(card: PrototypeRow, state: InteractiveKanbanState) {
    var origin by remember { mutableStateOf(Offset.Zero) }
    var menuOpen by remember { mutableStateOf(false) }
    val dragging = state.draggedId == card.id
    Box {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { origin = it.positionInRoot() }
                .graphicsLayer {
                    if (dragging) {
                        translationX = state.dragOffset.x
                        translationY = state.dragOffset.y
                    }
                }
                .alpha(if (dragging) 0.9f else 1f)
                .clayFlat(
                    shape = ClayShapes.Chip,
                    background = WeMadeColors.Surface,
                    outline = if (dragging) WeMadeColors.Primary else WeMadeColors.Outline,
                    borderWidth = ClayBorder.Medium
                )
                .pointerHoverIcon(PointerIcon.Hand)
                .pointerInput(card.id) { detectTapGestures(onTap = { menuOpen = true }) }
                .pointerInput(card.id) {
                    detectDragGestures(
                        onDragStart = { grab -> menuOpen = false; state.startDrag(card.id, origin, grab) },
                        onDrag = { change, amount -> change.consume(); state.dragBy(amount) },
                        onDragEnd = { state.endDrag() },
                        onDragCancel = { state.cancelDrag() }
                    )
                }
                .padding(ClaySpacing.Sm),
            verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
        ) {
            Text(
                card[state.config.titleField],
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            state.config.detailFields.forEach { field ->
                Text(
                    card[field],
                    style = MaterialTheme.typography.labelSmall,
                    color = WeMadeColors.OnSurfaceMuted,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            val targets = state.targetsFor(card)
            if (targets.isEmpty()) {
                DropdownMenuItem(text = { Text("Tidak ada tujuan yang diizinkan") }, onClick = { menuOpen = false }, enabled = false)
            }
            targets.forEach { target ->
                DropdownMenuItem(text = { Text("Pindah ke $target") }, onClick = { menuOpen = false; state.move(card.id, target) })
            }
        }
    }
}

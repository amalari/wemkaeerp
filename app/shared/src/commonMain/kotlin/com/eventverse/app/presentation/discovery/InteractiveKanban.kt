package com.eventverse.app.presentation.discovery

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.eventverse.app.domain.prototype.PrototypeRow
import com.eventverse.app.presentation.designsystem.ClayBorder
import com.eventverse.app.presentation.designsystem.ClayButton
import com.eventverse.app.presentation.designsystem.ClayButtonStyle
import com.eventverse.app.presentation.designsystem.ClayCard
import com.eventverse.app.presentation.designsystem.ClayKanbanBoard
import com.eventverse.app.presentation.designsystem.ClayKanbanColumnModel
import com.eventverse.app.presentation.designsystem.ClayShapes
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Papan kanban prototype yang bisa dimainkan (TRD-PLAT-003): kartu diseret antar kolom, atau
 * diketuk untuk menu "Pindah ke…" (jalur tanpa drag untuk layar sentuh/aksesibilitas). Aturan
 * pindah datang dari spec; menggunakan [ClayKanbanBoard] generik design system.
 */
@Composable
fun InteractiveKanban(
    state: InteractiveKanbanState,
    modifier: Modifier = Modifier,
    onDeleteCard: ((PrototypeRow) -> Unit)? = null
) {
    val columns = remember(state.config.columns, state.rows) {
        state.config.columns.map { column ->
            ClayKanbanColumnModel(
                id = column,
                title = column,
                items = state.cards(column),
                tint = WeMadeColors.Primary
            )
        }
    }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
        ClayKanbanBoard(
            columns = columns,
            itemId = { it.id },
            onMove = { card, toColumn -> state.move(card.id, toColumn) },
            canMove = { card, toColumn -> toColumn in state.targetsFor(card) },
            modifier = Modifier.fillMaxWidth(),
            columnWidth = 240.dp
        ) { card, isDragging ->
            KanbanCardContent(
                card = card,
                state = state,
                isDragging = isDragging,
                onDelete = onDeleteCard ?: { state.delete(it.id) }
            )
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
private fun KanbanCardContent(
    card: PrototypeRow,
    state: InteractiveKanbanState,
    isDragging: Boolean,
    onDelete: (PrototypeRow) -> Unit
) {
    var showConfirmDelete by remember { mutableStateOf(false) }

    ClayCard(
        modifier = Modifier.fillMaxWidth(),
        shape = ClayShapes.Chip,
        containerColor = WeMadeColors.Surface,
        outlineColor = if (isDragging) WeMadeColors.Primary else WeMadeColors.Outline,
        borderWidth = ClayBorder.Medium,
        contentPadding = PaddingValues(ClaySpacing.Sm)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = card[state.config.titleField],
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                // A3: Tombol hapus kartu dengan konfirmasi
                Text(
                    text = "×",
                    style = MaterialTheme.typography.titleMedium,
                    color = WeMadeColors.OnSurfaceMuted,
                    modifier = Modifier
                        .padding(start = ClaySpacing.Xs)
                        .clickable { showConfirmDelete = true }
                )
            }
            state.config.detailFields.forEach { field ->
                Text(
                    text = card[field],
                    style = MaterialTheme.typography.labelSmall,
                    color = WeMadeColors.OnSurfaceMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }

    if (showConfirmDelete) {
        AlertDialog(
            onDismissRequest = { showConfirmDelete = false },
            title = { Text("Hapus Kartu", fontWeight = FontWeight.Bold) },
            text = { Text("Yakin ingin menghapus \"${card[state.config.titleField]}\"?") },
            confirmButton = {
                ClayButton(
                    text = "Hapus",
                    style = ClayButtonStyle.Danger,
                    onClick = {
                        showConfirmDelete = false
                        onDelete(card)
                    }
                )
            },
            dismissButton = {
                ClayButton(
                    text = "Batal",
                    style = ClayButtonStyle.Secondary,
                    onClick = { showConfirmDelete = false }
                )
            }
        )
    }
}

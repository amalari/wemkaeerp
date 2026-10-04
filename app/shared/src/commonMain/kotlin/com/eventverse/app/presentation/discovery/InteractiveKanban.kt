package com.eventverse.app.presentation.discovery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.eventverse.app.domain.prototype.PrototypeRow
import com.eventverse.app.presentation.designsystem.ClayKanbanBoard
import com.eventverse.app.presentation.designsystem.ClayKanbanColumnModel
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Papan kanban prototype yang bisa dimainkan (TRD-PLAT-003, butir A5):
 * - Kartu diseret antar kolom atau diketuk untuk dialog detail form ([KanbanConfig.detailForm]).
 * - Kartu kaya mendukung [com.eventverse.app.domain.prototype.CardStyle] via [KanbanCardContent].
 * - Header kolom mendukung warna data tenant [com.eventverse.app.domain.prototype.ColumnMeta.tintHex]
 *   dan peringatan batas WIP ([com.eventverse.app.domain.prototype.ColumnMeta.wipLimit]).
 */
@Composable
fun InteractiveKanban(
    state: InteractiveKanbanState,
    modifier: Modifier = Modifier,
    onDeleteCard: ((PrototypeRow) -> Unit)? = null
) {
    val columns = remember(state.config.columns, state.config.columnMeta, state.rows) {
        state.config.columns.map { column ->
            val meta = state.config.columnMeta[column]
            val items = state.cards(column)
            val wipLimit = meta?.wipLimit
            val exceedsWip = wipLimit != null && items.size > wipLimit
            val tintHex = meta?.tintHex

            val tint = when {
                exceedsWip -> WeMadeColors.Defect
                tintHex != null -> Color(tintHex)
                else -> WeMadeColors.Primary
            }

            val subtitle = if (wipLimit != null) "WIP: ${items.size}/$wipLimit" else null

            ClayKanbanColumnModel(
                id = column,
                title = column,
                items = items,
                tint = tint,
                subtitle = subtitle
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
            columnWidth = 240.dp,
            onCardClick = { card -> state.selectedCardForDetail = card }
        ) { card, isDragging ->
            KanbanCardContent(
                card = card,
                state = state,
                isDragging = isDragging,
                onDelete = onDeleteCard ?: { state.delete(it.id) },
                onCardClick = { state.selectedCardForDetail = card }
            )
        }

        state.message?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = WeMadeColors.Defect)
        }
        Text(
            "Seret kartu ke kolom lain, atau ketuk untuk membuka detail.",
            style = MaterialTheme.typography.labelSmall,
            color = WeMadeColors.OnSurfaceMuted
        )
    }

    state.selectedCardForDetail?.let { detailCard ->
        KanbanDetailDialog(
            card = detailCard,
            state = state,
            onDismiss = { state.selectedCardForDetail = null }
        )
    }
}


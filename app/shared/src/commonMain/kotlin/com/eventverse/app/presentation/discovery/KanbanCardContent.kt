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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.eventverse.app.domain.prototype.CardElement
import com.eventverse.app.domain.prototype.CardStyle
import com.eventverse.app.domain.prototype.PrototypeRow
import com.eventverse.app.presentation.designsystem.ClayBadge
import com.eventverse.app.presentation.designsystem.ClayBorder
import com.eventverse.app.presentation.designsystem.ClayButton
import com.eventverse.app.presentation.designsystem.ClayButtonStyle
import com.eventverse.app.presentation.designsystem.ClayCard
import com.eventverse.app.presentation.designsystem.ClayShapes
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.ClayTag
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Konten kartu kanban kaya (TRD-PLAT-003, butir A5).
 * Mendukung rendering elemen bergaya menurut [CardStyle]:
 * - TITLE (tebal)
 * - TEXT (redup)
 * - BADGE ([ClayBadge])
 * - DATE (teks tanggal)
 * - NUMBER (angka rata kanan)
 * - FLAG ([ClayTag] merah bila bernilai "ya")
 * Bila [KanbanConfig.card] kosong, menggunakan tata letak lama (titleField + detailFields).
 */
@Composable
fun KanbanCardContent(
    card: PrototypeRow,
    state: InteractiveKanbanState,
    isDragging: Boolean,
    onDelete: (PrototypeRow) -> Unit,
    modifier: Modifier = Modifier,
    onCardClick: (() -> Unit)? = null
) {
    var showConfirmDelete by remember { mutableStateOf(false) }
    val cardConfig = state.config.card

    ClayCard(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onCardClick != null) Modifier.clickable { onCardClick() } else Modifier),
        shape = ClayShapes.Chip,
        containerColor = WeMadeColors.Surface,
        outlineColor = if (isDragging) WeMadeColors.Primary else WeMadeColors.Outline,
        borderWidth = ClayBorder.Medium,
        contentPadding = PaddingValues(ClaySpacing.Sm)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)) {
            if (cardConfig.isNotEmpty()) {
                RenderRichCardElements(
                    card = card,
                    state = state,
                    elements = cardConfig,
                    onRequestDelete = { showConfirmDelete = true }
                )
            } else {
                RenderLegacyCardContent(
                    card = card,
                    state = state,
                    onRequestDelete = { showConfirmDelete = true }
                )
            }
        }
    }

    if (showConfirmDelete) {
        AlertDialog(
            onDismissRequest = { showConfirmDelete = false },
            title = { Text("Hapus Kartu", fontWeight = FontWeight.Bold) },
            text = { Text("Yakin ingin menghapus data kartu '${card.id}'?") },
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

@Composable
private fun RenderRichCardElements(
    card: PrototypeRow,
    state: InteractiveKanbanState,
    elements: List<CardElement>,
    onRequestDelete: () -> Unit
) {
    var hasRenderedDelete = false

    elements.forEachIndexed { index, elem ->
        val rawValue = card[elem.field]
        val fieldSpec = state.spec.entity(state.entityId)?.field(elem.field)
        val fieldLabel = fieldSpec?.label ?: elem.field

        when (elem.style) {
            CardStyle.TITLE -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = rawValue.ifEmpty { "—" },
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (!hasRenderedDelete) {
                        hasRenderedDelete = true
                        DeleteCardIcon(onClick = onRequestDelete)
                    }
                }
            }
            CardStyle.TEXT -> {
                if (rawValue.isNotBlank()) {
                    Text(
                        text = rawValue,
                        style = MaterialTheme.typography.labelSmall,
                        color = WeMadeColors.OnSurfaceMuted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            CardStyle.BADGE -> {
                if (rawValue.isNotBlank()) {
                    ClayBadge(
                        text = rawValue,
                        tint = WeMadeColors.Primary
                    )
                }
            }
            CardStyle.DATE -> {
                if (rawValue.isNotBlank()) {
                    Text(
                        text = "📅 $rawValue",
                        style = MaterialTheme.typography.labelSmall,
                        color = WeMadeColors.OnSurfaceMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            CardStyle.NUMBER -> {
                if (rawValue.isNotBlank()) {
                    Text(
                        text = rawValue,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.End,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            CardStyle.FLAG -> {
                val isFlagged = rawValue.equals("ya", ignoreCase = true) ||
                        rawValue.equals("true", ignoreCase = true) ||
                        rawValue == "1"
                if (isFlagged) {
                    ClayTag(
                        text = fieldLabel,
                        tint = WeMadeColors.Defect
                    )
                }
            }
        }
    }

    if (!hasRenderedDelete) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            DeleteCardIcon(onClick = onRequestDelete)
        }
    }
}

@Composable
private fun RenderLegacyCardContent(
    card: PrototypeRow,
    state: InteractiveKanbanState,
    onRequestDelete: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = card[state.config.titleField].ifEmpty { "—" },
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        DeleteCardIcon(onClick = onRequestDelete)
    }

    state.config.detailFields.forEach { field ->
        val text = card[field]
        if (text.isNotBlank()) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = WeMadeColors.OnSurfaceMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun DeleteCardIcon(onClick: () -> Unit) {
    Text(
        text = "×",
        style = MaterialTheme.typography.titleMedium,
        color = WeMadeColors.OnSurfaceMuted,
        modifier = Modifier
            .padding(start = ClaySpacing.Xs)
            .clickable { onClick() }
    )
}

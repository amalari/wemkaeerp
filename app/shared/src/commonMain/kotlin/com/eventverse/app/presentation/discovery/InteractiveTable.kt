package com.eventverse.app.presentation.discovery

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.eventverse.app.domain.prototype.PrototypeRow
import com.eventverse.app.presentation.designsystem.ClayBadge
import com.eventverse.app.presentation.designsystem.ClayButton
import com.eventverse.app.presentation.designsystem.ClayButtonStyle
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.ClayTextField
import com.eventverse.app.presentation.designsystem.IconChevronDown
import com.eventverse.app.presentation.designsystem.IconChevronUp
import com.eventverse.app.presentation.theme.WeMadeColors

private val ColumnWidth = 118.dp

/**
 * Tabel prototype yang bisa dimainkan (TRD-PLAT-003, butir A4):
 * - Cari & urut kolom
 * - Ubah status lewat lencana ENUM
 * - Form pembuatan baris inline ([TableConfig.inlineCreate])
 * - Pengeditan sel inline ([TableConfig.editableFields])
 */
@Composable
fun InteractiveTable(state: InteractiveTableState, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            ClayTextField(
                value = state.query,
                onValueChange = { state.query = it },
                modifier = Modifier.weight(1f),
                placeholder = "Cari di tabel…"
            )
            if (state.config.inlineCreate && !state.isCreatingInline) {
                ClayButton(
                    text = "+ Tambah",
                    style = ClayButtonStyle.Primary,
                    onClick = { state.startInlineCreate() }
                )
            }
        }

        Column(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
                state.config.columns.forEach { column ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xs),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .width(ColumnWidth)
                            .pointerHoverIcon(PointerIcon.Hand)
                            .clickable { state.toggleSort(column) }
                    ) {
                        Text(
                            state.columnLabel(column),
                            style = MaterialTheme.typography.labelSmall,
                            color = WeMadeColors.OnSurfaceMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (state.sortColumn == column) {
                            val markerModifier = Modifier.size(12.dp)
                            if (state.ascending) IconChevronUp(markerModifier) else IconChevronDown(markerModifier)
                        }
                    }
                }
            }
            HorizontalDivider(color = WeMadeColors.Outline.copy(alpha = 0.3f))

            if (state.isCreatingInline) {
                InlineRowEditor(state = state, columnWidth = ColumnWidth)
                HorizontalDivider(color = WeMadeColors.Outline.copy(alpha = 0.2f))
            }

            val rows = state.visibleRows
            rows.forEach { row -> TableRow(row, state) }
            if (rows.isEmpty() && !state.isCreatingInline) {
                Text("Tidak ada baris yang cocok.", style = MaterialTheme.typography.bodySmall, color = WeMadeColors.OnSurfaceMuted)
            }
        }
        state.message?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = WeMadeColors.Defect) }
        if (state.config.statusField != null) {
            Text("Ketuk status untuk mengubahnya.", style = MaterialTheme.typography.labelSmall, color = WeMadeColors.OnSurfaceMuted)
        }
        if (state.config.editableFields.isNotEmpty()) {
            Text("Ketuk sel untuk mengedit nilai.", style = MaterialTheme.typography.labelSmall, color = WeMadeColors.OnSurfaceMuted)
        }
    }
}

@Composable
private fun TableRow(row: PrototypeRow, state: InteractiveTableState) {
    var showConfirmDelete by remember { mutableStateOf(false) }

    Row(
        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        state.config.columns.forEach { column ->
            if (state.isStatus(column)) {
                StatusCell(row, column, state)
            } else {
                TableCell(
                    row = row,
                    column = column,
                    state = state,
                    columnWidth = ColumnWidth
                )
            }
        }
        Text(
            text = "×",
            style = MaterialTheme.typography.titleMedium,
            color = WeMadeColors.OnSurfaceMuted,
            modifier = Modifier
                .padding(horizontal = ClaySpacing.Xs)
                .clickable { showConfirmDelete = true }
        )
    }

    if (showConfirmDelete) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showConfirmDelete = false },
            title = { Text("Hapus Baris", fontWeight = FontWeight.Bold) },
            text = { Text("Yakin ingin menghapus data baris '${row.id}'?") },
            confirmButton = {
                com.eventverse.app.presentation.designsystem.ClayButton(
                    text = "Hapus",
                    style = com.eventverse.app.presentation.designsystem.ClayButtonStyle.Danger,
                    onClick = {
                        showConfirmDelete = false
                        state.delete(row.id)
                    }
                )
            },
            dismissButton = {
                com.eventverse.app.presentation.designsystem.ClayButton(
                    text = "Batal",
                    style = com.eventverse.app.presentation.designsystem.ClayButtonStyle.Secondary,
                    onClick = { showConfirmDelete = false }
                )
            }
        )
    }
}

@Composable
private fun StatusCell(row: PrototypeRow, column: String, state: InteractiveTableState) {
    var open by remember { mutableStateOf(false) }
    Box(modifier = Modifier.width(ColumnWidth)) {
        ClayBadge(
            text = row[column].ifEmpty { "—" },
            tint = WeMadeColors.Primary,
            modifier = Modifier.pointerHoverIcon(PointerIcon.Hand).clickable { open = true }
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            val targets = state.statusTargets(row)
            if (targets.isEmpty()) {
                DropdownMenuItem(text = { Text("Tidak ada tujuan yang diizinkan") }, onClick = { open = false }, enabled = false)
            }
            targets.forEach { target ->
                DropdownMenuItem(text = { Text("Ubah ke $target") }, onClick = { open = false; state.setStatus(row.id, target) })
            }
        }
    }
}

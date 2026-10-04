package com.eventverse.app.presentation.discovery

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import com.eventverse.app.domain.prototype.InteractiveScreen
import com.eventverse.app.domain.prototype.PrototypeRow
import com.eventverse.app.presentation.designsystem.ClayBadge
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.ClayTextField
import com.eventverse.app.presentation.theme.WeMadeColors

private val ColumnWidth = 118.dp

/**
 * Tabel prototype yang bisa dimainkan (TRD-PLAT-003): cari, urut dengan mengetuk judul kolom, dan
 * (bila pack menyatakan kolom status) ubah status di baris. Kolom diberi lebar tetap dan digulir
 * ke samping — sel tidak lagi dipotong jadi "PO-2026…" di bingkai selebar ponsel.
 */
@Composable
fun InteractiveTable(screen: InteractiveScreen, modifier: Modifier = Modifier) {
    val state = remember(screen) { InteractiveTableState(screen) }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
        ClayTextField(
            value = state.query,
            onValueChange = { state.query = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = "Cari di tabel…"
        )
        Column(modifier = Modifier.horizontalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
            Row(horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
                state.config.columns.forEach { column ->
                    val marker = if (state.sortColumn == column) (if (state.ascending) " ▲" else " ▼") else ""
                    Text(
                        column + marker,
                        style = MaterialTheme.typography.labelSmall,
                        color = WeMadeColors.OnSurfaceMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .width(ColumnWidth)
                            .pointerHoverIcon(PointerIcon.Hand)
                            .clickable { state.toggleSort(column) }
                    )
                }
            }
            HorizontalDivider(color = WeMadeColors.Outline.copy(alpha = 0.3f))
            val rows = state.visibleRows
            rows.forEach { row -> TableRow(row, state) }
            if (rows.isEmpty()) {
                Text("Tidak ada baris yang cocok.", style = MaterialTheme.typography.bodySmall, color = WeMadeColors.OnSurfaceMuted)
            }
        }
        state.message?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = WeMadeColors.Defect) }
        if (state.config.statusField != null) {
            Text("Ketuk status untuk mengubahnya.", style = MaterialTheme.typography.labelSmall, color = WeMadeColors.OnSurfaceMuted)
        }
    }
}

@Composable
private fun TableRow(row: PrototypeRow, state: InteractiveTableState) {
    Row(horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
        state.config.columns.forEach { column ->
            if (state.isStatus(column)) {
                StatusCell(row, column, state)
            } else {
                Text(
                    row[column],
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(ColumnWidth)
                )
            }
        }
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

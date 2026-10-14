package com.eventverse.app.presentation.discovery

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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

/**
 * Tabel prototype yang bisa dimainkan (TRD-PLAT-003, butir A4):
 * - Cari & urut kolom
 * - Ubah status lewat lencana ENUM
 * - Form pembuatan baris inline ([TableConfig.inlineCreate])
 * - Pengeditan sel inline ([TableConfig.editableFields])
 * - Responsif memenuhi lebar (fillMaxWidth) dengan scroll horizontal bila kolom padat
 */
@Composable
fun InteractiveTable(
    state: InteractiveTableState,
    modifier: Modifier = Modifier,
    wireframe: Boolean = true
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ClayTextField(
                value = state.query,
                onValueChange = { state.query = it },
                modifier = Modifier.weight(1f),
                placeholder = "Cari di tabel..."
            )
            if (state.config.inlineCreate && !state.isCreatingInline) {
                if (wireframe) {
                    WireframeButton(
                        text = "+ Tambah",
                        onClick = { state.startInlineCreate() },
                        filled = true
                    )
                } else {
                    ClayButton(
                        text = "+ Tambah",
                        style = ClayButtonStyle.Primary,
                        onClick = { state.startInlineCreate() }
                    )
                }
            }
        }

        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val numCols = state.config.columns.size
            val minColWidth = 130.dp
            val deleteWidth = 36.dp
            val totalSpacing = ClaySpacing.Sm * numCols
            val available = maxWidth - deleteWidth - totalSpacing
            val responsiveColWidth = if (numCols > 0 && available > minColWidth * numCols) {
                available / numCols
            } else {
                minColWidth
            }
            val contentWidth = maxOf(maxWidth, (responsiveColWidth * numCols) + totalSpacing + deleteWidth)

            Column(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
            ) {
                Row(
                    modifier = Modifier.width(contentWidth),
                    horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    state.config.columns.forEach { column ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xs),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .width(responsiveColWidth)
                                .pointerHoverIcon(PointerIcon.Hand)
                                .clickable { state.toggleSort(column) }
                        ) {
                            Text(
                                state.columnLabel(column),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (wireframe) FontWeight.Bold else FontWeight.Normal,
                                color = if (wireframe) WeMadeColors.WireframeOnSurface else WeMadeColors.OnSurfaceMuted,
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
                    Spacer(modifier = Modifier.width(deleteWidth))
                }
                HorizontalDivider(
                    modifier = Modifier.width(contentWidth),
                    color = if (wireframe) WeMadeColors.WireframeBorder else WeMadeColors.Outline.copy(alpha = 0.3f),
                    thickness = if (wireframe) 1.5.dp else 1.dp
                )

                if (state.isCreatingInline) {
                    InlineRowEditor(state = state, columnWidth = responsiveColWidth, wireframe = wireframe)
                    HorizontalDivider(
                        modifier = Modifier.width(contentWidth),
                        color = if (wireframe) WeMadeColors.WireframeBorderSubtle else WeMadeColors.Outline.copy(alpha = 0.2f)
                    )
                }

                val rows = state.visibleRows
                rows.forEach { row ->
                    TableRow(
                        row = row,
                        state = state,
                        columnWidth = responsiveColWidth,
                        deleteWidth = deleteWidth,
                        contentWidth = contentWidth,
                        wireframe = wireframe
                    )
                }
                if (rows.isEmpty() && !state.isCreatingInline) {
                    Text("Tidak ada baris yang cocok.", style = MaterialTheme.typography.bodySmall, color = WeMadeColors.OnSurfaceMuted)
                }
            }
        }
        state.message?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = WeMadeColors.Defect) }
        state.transientMessage?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = WeMadeColors.Defect) }
        if (state.config.statusField != null || state.config.editableFields.isNotEmpty()) {
            Row(
                modifier = Modifier.padding(top = ClaySpacing.Xs),
                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (state.config.statusField != null) {
                    Text(
                        "• Ketuk status untuk mengubah",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (wireframe) WeMadeColors.WireframeOnSurfaceMuted else WeMadeColors.OnSurfaceMuted
                    )
                }
                if (state.config.editableFields.isNotEmpty()) {
                    Text(
                        "• Ketuk sel untuk mengedit",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (wireframe) WeMadeColors.WireframeOnSurfaceMuted else WeMadeColors.OnSurfaceMuted
                    )
                }
            }
        }
    }
}

@Composable
private fun TableRow(
    row: PrototypeRow,
    state: InteractiveTableState,
    columnWidth: Dp,
    deleteWidth: Dp,
    contentWidth: Dp,
    wireframe: Boolean = false
) {
    var showConfirmDelete by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.width(contentWidth),
        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        state.config.columns.forEach { column ->
            if (state.isStatus(column)) {
                StatusCell(row, column, state, columnWidth, wireframe = wireframe)
            } else {
                TableCell(
                    row = row,
                    column = column,
                    state = state,
                    columnWidth = columnWidth,
                    wireframe = wireframe
                )
            }
        }
        Box(
            modifier = Modifier.width(deleteWidth),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "×",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (wireframe) FontWeight.Bold else FontWeight.Normal,
                color = if (wireframe) WeMadeColors.WireframeBorder else WeMadeColors.OnSurfaceMuted,
                modifier = Modifier
                    .pointerHoverIcon(PointerIcon.Hand)
                    .clickable { showConfirmDelete = true }
                    .padding(horizontal = ClaySpacing.Xs)
            )
        }
    }

    if (showConfirmDelete) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showConfirmDelete = false },
            title = { Text("Hapus Baris", fontWeight = FontWeight.Bold) },
            text = { Text("Yakin ingin menghapus data baris '${row.id}'?") },
            confirmButton = {
                if (wireframe) {
                    WireframeButton(
                        text = "Hapus",
                        onClick = {
                            showConfirmDelete = false
                            state.delete(row.id)
                        },
                        filled = true
                    )
                } else {
                    ClayButton(
                        text = "Hapus",
                        style = ClayButtonStyle.Danger,
                        onClick = {
                            showConfirmDelete = false
                            state.delete(row.id)
                        }
                    )
                }
            },
            dismissButton = {
                if (wireframe) {
                    WireframeButton(
                        text = "Batal",
                        onClick = { showConfirmDelete = false },
                        filled = false
                    )
                } else {
                    ClayButton(
                        text = "Batal",
                        style = ClayButtonStyle.Secondary,
                        onClick = { showConfirmDelete = false }
                    )
                }
            }
        )
    }
}

@Composable
private fun StatusCell(
    row: PrototypeRow,
    column: String,
    state: InteractiveTableState,
    columnWidth: Dp,
    wireframe: Boolean = false
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier = Modifier.width(columnWidth)) {
        ClayBadge(
            text = row[column].ifEmpty { "-" },
            tint = if (wireframe) WeMadeColors.WireframeBorder else WeMadeColors.Primary,
            containerColor = if (wireframe) WeMadeColors.WireframeBadgeBg else WeMadeColors.Primary.copy(alpha = 0.14f),
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

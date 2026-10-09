package com.eventverse.app.presentation.discovery

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.eventverse.app.domain.prototype.FieldSpec
import com.eventverse.app.domain.prototype.FieldType
import com.eventverse.app.domain.prototype.PrototypeRow
import com.eventverse.app.presentation.common.fieldFileErrorMessage
import com.eventverse.app.presentation.deal.openInBrowser
import com.eventverse.app.presentation.designsystem.ClayFileChip
import com.eventverse.app.presentation.discovery.fields.FieldInput
import com.eventverse.app.presentation.discovery.fields.displayValue
import com.eventverse.app.presentation.theme.WeMadeColors
import kotlinx.coroutines.launch

/**
 * Komponen sel tabel yang dapat diedit langsung (TRD-PLAT-003, butir A4).
 * Bila kolom terdaftar di [TableConfig.editableFields], mengetuk teks akan membuka
 * editor sel inline. Tekan Enter untuk menyimpan, Esc untuk membatalkan.
 * Sel FILE (C8, TRD-FIELD-002): chip nama berkas + aksi unduh presigned (read),
 * unggah/ganti/hapus lewat [FieldInput] bila baris ber-id server.
 */
@Composable
fun TableCell(
    row: PrototypeRow,
    column: String,
    state: InteractiveTableState,
    columnWidth: Dp,
    modifier: Modifier = Modifier
) {
    val isEditing = state.editingCell == (row.id to column)
    val isEditable = state.isCellEditable(column)

    Box(modifier = modifier.width(columnWidth)) {
        if (isEditing) {
            val field = state.fieldSpec(column) ?: FieldSpec(
                key = column,
                type = FieldType.TEXT,
                label = column,
                required = false
            )
            // Diingat per baris — jangan membangun ulang klien ops saat recompose (C8).
            val editFileOps = remember(state, row.id) { state.fileFieldOps(row.id) }

            FieldInput(
                field = field,
                value = state.editingValue,
                onValueChange = { state.editingValue = it },
                showLabel = false,
                compact = true,
                errorMessage = state.cellErrorMessage,
                fileOps = editFileOps,
                modifier = Modifier
                    .width(columnWidth)
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown) {
                            when {
                                event.key == Key.Enter && (field.type != FieldType.LONG_TEXT || !event.isShiftPressed) -> {
                                    state.submitCellEdit(row.id, column)
                                    true
                                }
                                event.key == Key.Escape -> {
                                    state.cancelCellEdit()
                                    true
                                }
                                else -> false
                            }
                        } else false
                    }
            )
        } else {
            val fieldSpec = state.fieldSpec(column)
            val rawValue = row[column]
            if (fieldSpec?.type == FieldType.FILE && rawValue.isNotBlank()) {
                // C8 Track C: chip nama berkas (segmen terakhir ref) + unduh presigned; bukan teks mentah.
                val scope = rememberCoroutineScope()
                val downloadOps = remember(state, row.id) { state.fileFieldOps(row.id) }
                ClayFileChip(
                    fileName = fieldSpec.displayValue(rawValue),
                    onClick = {
                        val ops = downloadOps ?: return@ClayFileChip
                        // Mulai aksi baru → buang galat unduhan sebelumnya agar tidak basi.
                        state.transientMessage = null
                        scope.launch {
                            ops.downloadUrl(column, rawValue) { result ->
                                result.onSuccess { url ->
                                    state.transientMessage = null
                                    openInBrowser(url)
                                }
                                    .onFailure { err -> state.transientMessage = fieldFileErrorMessage(err) }
                            }
                        }
                    },
                    tint = if (state.isCellEditable(column)) WeMadeColors.Primary else WeMadeColors.OnSurfaceMuted
                )
            } else {
                val cellText = (fieldSpec?.displayValue(rawValue) ?: rawValue).ifEmpty { "—" }
                Text(
                    text = cellText,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isEditable) WeMadeColors.Primary else WeMadeColors.OnSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .width(columnWidth)
                        .then(
                            if (isEditable) {
                                Modifier
                                    .pointerHoverIcon(PointerIcon.Hand)
                                    .clickable { state.startCellEdit(row.id, column, row[column]) }
                            } else Modifier
                        )
                )
            }
        }
    }
}

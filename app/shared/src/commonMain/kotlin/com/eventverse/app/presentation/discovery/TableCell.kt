package com.eventverse.app.presentation.discovery

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
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
import com.eventverse.app.presentation.discovery.fields.FieldInput
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Komponen sel tabel yang dapat diedit langsung (TRD-PLAT-003, butir A4).
 * Bila kolom terdaftar di [TableConfig.editableFields], mengetuk teks akan membuka
 * editor sel inline. Tekan Enter untuk menyimpan, Esc untuk membatalkan.
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

            FieldInput(
                field = field,
                value = state.editingValue,
                onValueChange = { state.editingValue = it },
                showLabel = false,
                compact = true,
                errorMessage = state.cellErrorMessage,
                modifier = Modifier
                    .width(columnWidth)
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown) {
                            when (event.key) {
                                Key.Enter -> {
                                    state.submitCellEdit(row.id, column)
                                    true
                                }
                                Key.Escape -> {
                                    state.cancelCellEdit()
                                    true
                                }
                                else -> false
                            }
                        } else false
                    }
            )
        } else {
            val cellText = row[column].ifEmpty { "—" }
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

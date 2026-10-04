package com.eventverse.app.presentation.discovery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.eventverse.app.domain.prototype.FieldSpec
import com.eventverse.app.domain.prototype.FieldType
import com.eventverse.app.presentation.designsystem.ClayButton
import com.eventverse.app.presentation.designsystem.ClayButtonStyle
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.discovery.fields.FieldInput
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Baris isian inline di atas tabel (TRD-PLAT-003, butir A4).
 * Muncul saat [TableConfig.inlineCreate] aktif dan pengguna menekan tombol "+ Tambah".
 * Menampilkan input sesuai [FieldType] untuk setiap kolom yang dapat diisi.
 * Enter = Simpan, Esc = Batal.
 */
@Composable
fun InlineRowEditor(
    state: InteractiveTableState,
    columnWidth: Dp,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.onPreviewKeyEvent { event ->
            if (event.type == KeyEventType.KeyDown) {
                when (event.key) {
                    Key.Enter -> {
                        state.submitInlineCreate()
                        true
                    }
                    Key.Escape -> {
                        state.cancelInlineCreate()
                        true
                    }
                    else -> false
                }
            } else false
        },
        verticalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            state.config.columns.forEach { column ->
                val field = state.fieldSpec(column) ?: FieldSpec(
                    key = column,
                    type = FieldType.TEXT,
                    label = column,
                    required = false
                )
                val value = state.inlineValues[column].orEmpty()

                FieldInput(
                    field = field,
                    value = value,
                    onValueChange = { state.setInlineValue(column, it) },
                    showLabel = false,
                    compact = true,
                    modifier = Modifier.width(columnWidth)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)) {
                ClayButton(
                    text = "Simpan",
                    style = ClayButtonStyle.Primary,
                    onClick = { state.submitInlineCreate() }
                )
                ClayButton(
                    text = "Batal",
                    style = ClayButtonStyle.Secondary,
                    onClick = { state.cancelInlineCreate() }
                )
            }
        }

        state.inlineErrorMessage?.let { err ->
            Text(
                text = err,
                style = MaterialTheme.typography.bodySmall,
                color = WeMadeColors.Defect,
                modifier = Modifier.padding(start = ClaySpacing.Xs)
            )
        }
    }
}

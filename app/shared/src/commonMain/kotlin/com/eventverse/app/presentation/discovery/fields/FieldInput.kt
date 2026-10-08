package com.eventverse.app.presentation.discovery.fields

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.eventverse.app.domain.prototype.FieldSpec
import com.eventverse.app.domain.prototype.FieldType
import com.eventverse.app.presentation.designsystem.ClayCheckbox
import com.eventverse.app.presentation.designsystem.ClayChoiceChip
import com.eventverse.app.presentation.designsystem.ClayDatePicker
import com.eventverse.app.presentation.designsystem.ClayFlowRow
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.ClayTextField
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Komponen input bersama untuk tipe data prototipe (TRD-PLAT-003, butir A3 — Aturan Tiga Kali).
 * Digunakan secara konsisten di Form Blok ([InteractiveForm]), Form Inline Tabel ([InlineRowEditor]),
 * dan Dialog Form Detail Kanban ([KanbanDetailDialog]).
 *
 * Pemetaan [FieldType]:
 * - TEXT -> [ClayTextField] standar
 * - NUMBER -> [ClayTextField] dengan [KeyboardType.Number]
 * - DATE -> [ClayDatePicker] pemilih tanggal berformat TTTT-BB-HH
 * - ENUM -> Pilihan opsi menggunakan [ClayChoiceChip]
 * - BOOL -> [ClayCheckbox] dengan status "ya" / "tidak"
 */
@Composable
fun FieldInput(
    field: FieldSpec,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    showLabel: Boolean = true,
    compact: Boolean = false,
    enabled: Boolean = true,
    errorMessage: String? = null,
    keyboardActions: KeyboardActions = KeyboardActions.Default
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(if (compact) ClaySpacing.Xxs else ClaySpacing.Xs)
    ) {
        if (showLabel) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = field.label,
                    style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
                    color = WeMadeColors.OnSurface
                )
                if (field.required) {
                    Text(
                        text = " *",
                        style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
                        color = WeMadeColors.Defect
                    )
                }
            }
        }

        when (field.type) {
            FieldType.BOOL -> {
                val checked = value == "ya"
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
                ) {
                    ClayCheckbox(
                        checked = checked,
                        onCheckedChange = { onValueChange(if (it) "ya" else "tidak") },
                        enabled = enabled
                    )
                    Text(
                        text = if (checked) "Ya" else "Tidak",
                        style = MaterialTheme.typography.bodySmall,
                        color = WeMadeColors.OnSurfaceMuted
                    )
                }
            }
            FieldType.ENUM -> {
                ClayFlowRow(spacing = ClaySpacing.Xs) {
                    field.options.forEach { opt ->
                        ClayChoiceChip(
                            text = opt,
                            selected = value == opt,
                            onClick = { onValueChange(opt) },
                            enabled = enabled
                        )
                    }
                }
            }
            FieldType.NUMBER -> {
                ClayTextField(
                    value = value,
                    onValueChange = { input ->
                        // Hanya terima karakter angka / desimal
                        if (input.all { it.isDigit() || it == '.' || it == '-' || it == ',' }) {
                            onValueChange(input)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = if (compact) field.label else "Contoh: 100",
                    enabled = enabled,
                    isError = errorMessage != null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    keyboardActions = keyboardActions
                )
            }
            FieldType.DATE -> {
                ClayDatePicker(
                    value = value,
                    onValueChange = onValueChange,
                    label = "",
                    modifier = Modifier.fillMaxWidth(),
                    enabled = enabled,
                    isError = errorMessage != null
                )
            }
            FieldType.TEXT -> {
                ClayTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = if (compact) field.label else "Isi ${field.label.lowercase()}...",
                    enabled = enabled,
                    isError = errorMessage != null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    keyboardActions = keyboardActions
                )
            }
        }

        if (errorMessage != null) {
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = WeMadeColors.Defect
            )
        }
    }
}

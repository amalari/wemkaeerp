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
import com.eventverse.app.presentation.designsystem.ClayTextArea
import com.eventverse.app.presentation.designsystem.ClayTextField
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Komponen input bersama untuk tipe data prototipe (TRD-PLAT-003, butir A3 — Aturan Tiga Kali).
 * Digunakan secara konsisten di Form Blok ([InteractiveForm]), Form Inline Tabel ([InlineRowEditor]),
 * dan Dialog Form Detail Kanban ([KanbanDetailDialog]).
 *
 * Pemetaan [FieldType]:
 * - TEXT -> [ClayTextField] standar
 * - LONG_TEXT -> [ClayTextArea] area teks multi-baris
 * - NUMBER -> [ClayTextField] dengan prefix/suffix format (Rp/kode, %; lihat NumberFormatting.kt); nilai simpan tetap angka polos
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
                val affix = numberAffix(field.format, field.currencyCode)
                ClayTextField(
                    value = value,
                    onValueChange = { input ->
                        // Kirim string simpan (titik desimal, tanpa ribuan/simbol); ketikan tak sah ditolak.
                        normalizeNumberTyping(input, field.format)?.let(onValueChange)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = if (compact) field.label else "Contoh: 100",
                    leadingIcon = if (affix.prefix.isEmpty()) null else {
                        { NumberAffixText(affix.prefix) }
                    },
                    trailingIcon = if (affix.suffix.isEmpty()) null else {
                        { NumberAffixText(affix.suffix) }
                    },
                    enabled = enabled,
                    isError = errorMessage != null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
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
            FieldType.LONG_TEXT -> {
                ClayTextArea(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = if (compact) field.label else "Isi ${field.label.lowercase()}...",
                    enabled = enabled,
                    isError = errorMessage != null,
                    minLines = if (compact) 2 else 3,
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

/** Prefix/suffix format angka (Rp, USD, %) sebagai bagian kontrol masukan. */
@Composable
private fun NumberAffixText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = WeMadeColors.OnSurfaceMuted
    )
}

package com.eventverse.app.presentation.crm.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.eventverse.app.domain.crm.LeadFieldDescriptor
import com.eventverse.app.domain.customfield.CustomFieldId
import com.eventverse.app.domain.customfield.CrmFieldType
import com.eventverse.app.domain.prototype.FieldType
import com.eventverse.app.domain.prototype.MultiSelectValues
import com.eventverse.app.presentation.designsystem.ClayChoiceChip
import com.eventverse.app.presentation.designsystem.ClayDatePicker
import com.eventverse.app.presentation.designsystem.ClayDateTimePicker
import com.eventverse.app.presentation.designsystem.ClayFlowRow
import com.eventverse.app.presentation.designsystem.ClayMultiChoiceChips
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.ClayTextArea
import com.eventverse.app.presentation.designsystem.ClayTimePicker
import com.eventverse.app.presentation.designsystem.isValidClayTime
import com.eventverse.app.presentation.designsystem.ClayTextField
import com.eventverse.app.presentation.theme.WeMadeColors
import com.eventverse.app.shared.json.jsonArrayOf
import com.eventverse.app.shared.json.jsonOf

/**
 * Input field kustom tenant di form lead baru (TRD-HELP-002 K4): Teks, Teks panjang, Angka, Pilihan,
 * dan Tanggal — tanpa waktu lewat `ClayDatePicker` (Irisan 1 Track C), berwaktu lewat
 * `ClayDateTimePicker` (C6, Irisan 2; format simpan `TTTT-BB-HH'T'JJ:MM`). Dirender dari skema
 * tenant — tidak ada nama kolom yang tertulis di kode.
 */
@Composable
internal fun LeadCustomFieldInputs(schema: List<LeadFieldDescriptor>, form: LeadFormState) {
    schema.filter { LeadFormState.supportsInput(it.type) }.forEach { f ->
        val id = CustomFieldId(f.fieldId)
        val value = form.custom[id].orEmpty()
        val label = f.label + (if (f.isRequired) " *" else "") + aiSuffix(form.isAi(f.fieldId))
        when (f.type.kind) {
            FieldType.ENUM -> Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)) {
                Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = WeMadeColors.OnSurface)
                ClayFlowRow(spacing = ClaySpacing.Xs) {
                    f.type.activeOptions.forEach { opt ->
                        ClayChoiceChip(
                            text = opt.label,
                            selected = value == opt.id.value,
                            onClick = { form.update(f.fieldId, if (value == opt.id.value) "" else opt.id.value) }
                        )
                    }
                }
            }
            FieldType.DATE -> if (f.type.withTime) {
                ClayDateTimePicker(
                    value = value,
                    onValueChange = { form.update(f.fieldId, it) },
                    label = label,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                ClayDatePicker(
                    value = value,
                    onValueChange = { form.update(f.fieldId, it) },
                    label = label,
                    modifier = Modifier.fillMaxWidth(),
                    isError = value.isNotBlank() && !isBlankOrIsoDate(value)
                )
            }
            FieldType.LONG_TEXT -> ClayTextArea(
                value = value,
                onValueChange = { form.update(f.fieldId, it) },
                label = label,
                minLines = 3,
                modifier = Modifier.fillMaxWidth()
            )
            FieldType.TEXT, FieldType.NUMBER -> ClayTextField(
                value = value,
                onValueChange = { form.update(f.fieldId, it) },
                label = label,
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            // D7: jam dinding; galat bentuk ditandai input, validasi akhir tetap di server.
            FieldType.TIME -> ClayTimePicker(
                value = value,
                onValueChange = { form.update(f.fieldId, it) },
                label = label,
                modifier = Modifier.fillMaxWidth(),
                isError = value.isNotBlank() && !isValidClayTime(value)
            )
            // MULTI_SELECT: larik id opsi aktif; dikirim sebagai larik JSON (aturan bentuk MultiSelectValues).
            FieldType.MULTI_SELECT -> {
                val selectedIds = (MultiSelectValues.parse(value) ?: emptyList()).toSet()
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)) {
                    Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = WeMadeColors.OnSurface)
                    ClayMultiChoiceChips(
                        options = f.type.activeOptions.map { it.id.value },
                        selected = selectedIds,
                        onToggle = { id ->
                            val next = if (id in selectedIds) selectedIds - id else selectedIds + id
                            form.update(f.fieldId, jsonArrayOf(next.sorted().map { jsonOf(it) }).encode())
                        },
                        labelOf = { id -> f.type.activeOptions.firstOrNull { it.id.value == id }?.label ?: id },
                        maxSelections = f.type.maxSelections
                    )
                }
            }
            // Difilter oleh LeadFormState.supportsInput: belum punya input di dialog lead baru.
            FieldType.BOOL, FieldType.USER_REF, FieldType.RELATION, FieldType.FILE -> Unit
        }
    }
}

internal fun aiSuffix(isAi: Boolean): String = if (isAi) " · diisi AI" else ""

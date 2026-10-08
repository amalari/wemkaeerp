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
import com.eventverse.app.domain.customfield.FieldType
import com.eventverse.app.presentation.designsystem.ClayChoiceChip
import com.eventverse.app.presentation.designsystem.ClayDatePicker
import com.eventverse.app.presentation.designsystem.ClayFlowRow
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.ClayTextField
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Input field kustom tenant di form lead baru (TRD-HELP-002 K4): Teks, Teks panjang, Angka, Pilihan,
 * dan Tanggal tanpa waktu (lewat `ClayDatePicker`, Irisan 1 Track C). Dirender dari skema tenant —
 * tidak ada nama kolom yang tertulis di kode.
 */
@Composable
internal fun LeadCustomFieldInputs(schema: List<LeadFieldDescriptor>, form: LeadFormState) {
    schema.filter { LeadFormState.supportsInput(it.type) }.forEach { f ->
        val id = CustomFieldId(f.fieldId)
        val value = form.custom[id].orEmpty()
        val label = f.label + (if (f.isRequired) " *" else "") + aiSuffix(form.isAi(f.fieldId))
        when (val t = f.type) {
            is FieldType.SingleSelect -> Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)) {
                Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = WeMadeColors.OnSurface)
                ClayFlowRow(spacing = ClaySpacing.Xs) {
                    t.activeOptions.forEach { opt ->
                        ClayChoiceChip(
                            text = opt.label,
                            selected = value == opt.id.value,
                            onClick = { form.update(f.fieldId, if (value == opt.id.value) "" else opt.id.value) }
                        )
                    }
                }
            }
            is FieldType.DateField -> ClayDatePicker(
                value = value,
                onValueChange = { form.update(f.fieldId, it) },
                label = label,
                modifier = Modifier.fillMaxWidth(),
                isError = value.isNotBlank() && !isBlankOrIsoDate(value)
            )
            is FieldType.LongText -> ClayTextField(
                value = value,
                onValueChange = { form.update(f.fieldId, it) },
                label = label,
                singleLine = false,
                modifier = Modifier.fillMaxWidth()
            )
            is FieldType.Text, is FieldType.Number -> ClayTextField(
                value = value,
                onValueChange = { form.update(f.fieldId, it) },
                label = label,
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            // Difilter oleh LeadFormState.supportsInput: belum punya input di dialog lead baru.
            is FieldType.Checkbox, is FieldType.UserRef -> Unit
        }
    }
}

internal fun aiSuffix(isAi: Boolean): String = if (isAi) " · diisi AI" else ""

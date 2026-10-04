package com.eventverse.app.presentation.discovery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.eventverse.app.domain.prototype.FieldSpec
import com.eventverse.app.domain.prototype.FieldType
import com.eventverse.app.presentation.designsystem.ClayBorder
import com.eventverse.app.presentation.designsystem.ClayButton
import com.eventverse.app.presentation.designsystem.ClayButtonStyle
import com.eventverse.app.presentation.designsystem.ClayCard
import com.eventverse.app.presentation.designsystem.ClayCheckbox
import com.eventverse.app.presentation.designsystem.ClayChoiceChip
import com.eventverse.app.presentation.designsystem.ClayFlowRow
import com.eventverse.app.presentation.designsystem.ClayShapes
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.ClayTextField
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Blok Form Interaktif (TRD-PLAT-003, butir A2).
 * Merender field sesuai [FieldType] menggunakan komponen Clay:
 * - TEXT / NUMBER / DATE -> [ClayTextField]
 * - ENUM -> [ClayChoiceChip] dalam [ClayFlowRow]
 * - BOOL -> [ClayCheckbox]
 * Validasi required dan aturan tipe ditegakkan oleh reducer, bukan di Composable.
 */
@Composable
fun InteractiveForm(state: InteractiveFormState, modifier: Modifier = Modifier) {
    ClayCard(
        modifier = modifier.fillMaxWidth(),
        shape = ClayShapes.Card,
        containerColor = WeMadeColors.Surface,
        outlineColor = WeMadeColors.Outline,
        borderWidth = ClayBorder.Medium,
        contentPadding = PaddingValues(ClaySpacing.Lg)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)) {
            state.fields().forEach { field ->
                FormFieldRow(
                    field = field,
                    value = state.formValues[field.key].orEmpty(),
                    onValueChange = { state.setFieldValue(field.key, it) }
                )
            }

            state.message?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = WeMadeColors.Defect)
            }
            state.successMessage?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = WeMadeColors.Success)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                ClayButton(
                    text = state.config.submitLabel,
                    style = ClayButtonStyle.Primary,
                    onClick = { state.submit() }
                )
            }
        }
    }
}

@Composable
private fun FormFieldRow(
    field: FieldSpec,
    value: String,
    onValueChange: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = field.label,
                style = MaterialTheme.typography.labelMedium,
                color = WeMadeColors.OnSurface
            )
            if (field.required) {
                Text(
                    text = " *",
                    style = MaterialTheme.typography.labelMedium,
                    color = WeMadeColors.Defect
                )
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
                        onCheckedChange = { onValueChange(if (it) "ya" else "tidak") }
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
                            onClick = { onValueChange(opt) }
                        )
                    }
                }
            }
            FieldType.NUMBER, FieldType.DATE, FieldType.TEXT -> {
                ClayTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = "Isi ${field.label.lowercase()}..."
                )
            }
        }
    }
}

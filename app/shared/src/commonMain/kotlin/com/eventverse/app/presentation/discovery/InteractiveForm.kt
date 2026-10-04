package com.eventverse.app.presentation.discovery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.eventverse.app.presentation.designsystem.ClayBorder
import com.eventverse.app.presentation.designsystem.ClayButton
import com.eventverse.app.presentation.designsystem.ClayButtonStyle
import com.eventverse.app.presentation.designsystem.ClayCard
import com.eventverse.app.presentation.designsystem.ClayShapes
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.discovery.fields.FieldInput
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Blok Form Interaktif (TRD-PLAT-003, butir A2 & A3).
 * Menggunakan komponen input bersama [FieldInput] sesuai [com.eventverse.app.domain.prototype.FieldType].
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
                FieldInput(
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

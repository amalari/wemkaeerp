package com.eventverse.app.presentation.discovery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.eventverse.app.domain.prototype.FieldSpec
import com.eventverse.app.domain.prototype.FieldType
import com.eventverse.app.domain.prototype.PrototypeRow
import com.eventverse.app.presentation.designsystem.ClayButton
import com.eventverse.app.presentation.designsystem.ClayButtonStyle
import com.eventverse.app.presentation.designsystem.ClayChoiceChip
import com.eventverse.app.presentation.designsystem.ClayFlowRow
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.discovery.fields.FieldInput
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Dialog form detail saat kartu kanban diketuk (TRD-PLAT-003, butir A5).
 * Menampilkan isian [detailForm.fields] menggunakan [FieldInput].
 * Mendukung aksi:
 * - Simpan perubahan nilai field
 * - Pindah kolom (sesuai aturan transisi status)
 * - Hapus kartu
 * - Tutup dialog
 */
@Composable
fun KanbanDetailDialog(
    card: PrototypeRow,
    state: InteractiveKanbanState,
    onDismiss: () -> Unit
) {
    val entity = state.spec.entity(state.entityId)
    val detailFormFields = state.config.detailForm?.fields
        ?: (listOf(state.config.titleField) + state.config.detailFields).distinct()

    val formFields: List<FieldSpec> = detailFormFields.map { key ->
        entity?.field(key) ?: FieldSpec(key = key, label = key, type = FieldType.TEXT)
    }

    val formValues = remember(card) {
        mutableStateMapOf<String, String>().apply {
            formFields.forEach { f ->
                put(f.key, card[f.key])
            }
        }
    }

    var selectedTargetColumn by remember { mutableStateOf<String?>(null) }
    var localError by remember { mutableStateOf<String?>(null) }
    val targets = remember(card) { state.targetsFor(card) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = card[state.config.titleField].ifEmpty { "Detail Kartu" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = WeMadeColors.OnSurface
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
            ) {
                // Form isian field
                formFields.forEach { field ->
                    FieldInput(
                        field = field,
                        value = formValues[field.key].orEmpty(),
                        onValueChange = { formValues[field.key] = it },
                        showLabel = true,
                        compact = false
                    )
                }

                // Pilihan pindah kolom jika ada target yang valid
                if (targets.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)) {
                        Text(
                            text = "Pindah ke Kolom:",
                            style = MaterialTheme.typography.labelMedium,
                            color = WeMadeColors.OnSurfaceMuted
                        )
                        ClayFlowRow(spacing = ClaySpacing.Xs) {
                            targets.forEach { targetCol ->
                                ClayChoiceChip(
                                    text = targetCol,
                                    selected = selectedTargetColumn == targetCol,
                                    onClick = {
                                        selectedTargetColumn = if (selectedTargetColumn == targetCol) null else targetCol
                                    }
                                )
                            }
                        }
                    }
                }

                localError?.let { err ->
                    Text(
                        text = err,
                        style = MaterialTheme.typography.bodySmall,
                        color = WeMadeColors.Defect
                    )
                }
            }
        },
        confirmButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ClayButton(
                    text = "Hapus",
                    style = ClayButtonStyle.Danger,
                    onClick = {
                        state.delete(card.id)
                        onDismiss()
                    }
                )
                ClayButton(
                    text = "Simpan",
                    style = ClayButtonStyle.Primary,
                    onClick = {
                        val changes = formValues.filter { (k, v) -> v != card[k] }
                        if (changes.isNotEmpty()) {
                            state.updateRow(card.id, changes)
                        }
                        selectedTargetColumn?.let { targetCol ->
                            state.move(card.id, targetCol)
                        }
                        onDismiss()
                    }
                )
            }
        },
        dismissButton = {
            ClayButton(
                text = "Tutup",
                style = ClayButtonStyle.Secondary,
                onClick = onDismiss
            )
        }
    )
}

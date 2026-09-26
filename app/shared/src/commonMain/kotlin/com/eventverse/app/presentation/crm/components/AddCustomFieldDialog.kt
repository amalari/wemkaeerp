package com.eventverse.app.presentation.crm.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.eventverse.app.domain.customfield.FieldType
import com.eventverse.app.presentation.designsystem.ClayBadge
import com.eventverse.app.presentation.designsystem.ClayButton
import com.eventverse.app.presentation.designsystem.ClayButtonStyle
import com.eventverse.app.presentation.designsystem.ClayCard
import com.eventverse.app.presentation.designsystem.ClayCheckbox
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.ClayTextField
import com.eventverse.app.presentation.theme.WeMadeColors

private data class FieldTypeOption(val label: String, val build: () -> FieldType)

private val FIELD_TYPE_OPTIONS = listOf(
    FieldTypeOption("Teks") { FieldType.Text },
    FieldTypeOption("Teks Panjang") { FieldType.LongText },
    FieldTypeOption("Angka") { FieldType.Number() },
    FieldTypeOption("Tanggal") { FieldType.DateField() },
    FieldTypeOption("Ceklis") { FieldType.Checkbox }
    // SingleSelect deliberately omitted from this quick-add dialog: it needs an options
    // editor of its own (add/rename/reorder/archive choices), which is Phase 1.5 UI —
    // seeded SingleSelect fields (Kategori Pakaian, Jenis Sablon, Warna Bahan) already exist
    // via V22 and can be edited through a future "Kelola pilihan" screen.
)

/** Adds ONE custom field to the CRM Leads schema — `AccessLevel.MANAGE` only (enforced by the caller). */
@Composable
fun AddCustomFieldDialog(
    onDismiss: () -> Unit,
    onAdd: (label: String, type: FieldType, isRequired: Boolean) -> Unit
) {
    var label by remember { mutableStateOf("") }
    var selectedTypeIndex by remember { mutableStateOf(0) }
    var isRequired by remember { mutableStateOf(false) }
    var typeMenuExpanded by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        ClayCard(modifier = Modifier.width(420.dp)) {
            Text(text = "Tambah Kolom Kustom", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = WeMadeColors.OnSurface)

            Column(
                modifier = Modifier.fillMaxWidth().padding(top = ClaySpacing.Xl),
                verticalArrangement = Arrangement.spacedBy(ClaySpacing.Lg)
            ) {
                ClayTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = "Nama Kolom",
                    placeholder = "Warna Bahan Kain",
                    modifier = Modifier.fillMaxWidth()
                )

                Column {
                    Text(text = "Tipe Kolom", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = WeMadeColors.OnSurface)
                    Row(modifier = Modifier.padding(top = ClaySpacing.Xs)) {
                        ClayBadge(
                            text = FIELD_TYPE_OPTIONS[selectedTypeIndex].label,
                            tint = WeMadeColors.Primary,
                            modifier = Modifier.clickable { typeMenuExpanded = true }
                        )
                    }
                    DropdownMenu(expanded = typeMenuExpanded, onDismissRequest = { typeMenuExpanded = false }) {
                        FIELD_TYPE_OPTIONS.forEachIndexed { index, option ->
                            DropdownMenuItem(
                                text = { Text(option.label) },
                                onClick = { selectedTypeIndex = index; typeMenuExpanded = false }
                            )
                        }
                    }
                }

                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    ClayCheckbox(checked = isRequired, onCheckedChange = { isRequired = it })
                    Text(
                        text = "Wajib diisi",
                        fontSize = 12.sp,
                        color = WeMadeColors.OnSurface,
                        modifier = Modifier.padding(start = ClaySpacing.Md)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = ClaySpacing.Xl),
                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
            ) {
                ClayButton(text = "Batal", onClick = onDismiss, style = ClayButtonStyle.Secondary, modifier = Modifier.weight(1f))
                ClayButton(
                    text = "Tambah",
                    onClick = { onAdd(label, FIELD_TYPE_OPTIONS[selectedTypeIndex].build(), isRequired) },
                    enabled = label.isNotBlank(),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

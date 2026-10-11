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
import com.eventverse.app.domain.customfield.CrmFieldType
import com.eventverse.app.domain.prototype.FieldType
import com.eventverse.app.domain.prototype.relationTargetFormatError
import com.eventverse.app.presentation.designsystem.ClayBadge
import com.eventverse.app.presentation.designsystem.ClayButton
import com.eventverse.app.presentation.designsystem.ClayButtonStyle
import com.eventverse.app.presentation.designsystem.ClayCard
import com.eventverse.app.presentation.designsystem.ClayCheckbox
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.ClayTextField
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Satu pilihan tipe kolom di dialog. [needsTarget] true untuk [FieldType.RELATION] (C7,
 * TRD-FIELD-001): target resource wajib diisi, jadi tipenya dibangun terpisah dari [build]
 * memakai input `targetResource` di dialog — bukan placeholder palsu. [selectKind] non-null
 * untuk ENUM/MULTI_SELECT (follow-up #1 `PLAN-unify-field-vocabulary.md` §3a): tipenya dibangun
 * dari editor opsi berwarna [SelectOptionsEditor], bukan [build] statis.
 */
private data class FieldTypeOption(
    val label: String,
    val needsTarget: Boolean = false,
    val selectKind: FieldType? = null,
    val build: (() -> CrmFieldType)? = null
) {
    val needsOptions: Boolean get() = selectKind != null
}

private val FIELD_TYPE_OPTIONS = listOf(
    FieldTypeOption("Teks") { CrmFieldType(FieldType.TEXT) },
    FieldTypeOption("Teks Panjang") { CrmFieldType(FieldType.LONG_TEXT) },
    FieldTypeOption("Angka") { CrmFieldType(FieldType.NUMBER) },
    FieldTypeOption("Tanggal") { CrmFieldType(FieldType.DATE) },
    // Config {"withTime": true} dikirim otomatis: CrmApiClient menyusunnya lewat
    // CustomAttributesCodec.encodeConfig dari objek CrmFieldType yang dibangun di sini.
    FieldTypeOption("Tanggal berwaktu") { CrmFieldType(FieldType.DATE, withTime = true) },
    // D7: jam dinding murni — diwarisi dari kosakata bersama, kontrolnya ClayTimePicker.
    FieldTypeOption("Jam") { CrmFieldType(FieldType.TIME) },
    FieldTypeOption("Ceklis") { CrmFieldType(FieldType.BOOL) },
    // Follow-up #1 §3a: opsi (label + warna) diedit langsung di dialog; id opsi di-slug saat
    // disimpan. MULTI_SELECT dibuat tanpa `maxSelections` (null = tidak dibatasi).
    FieldTypeOption("Pilihan tunggal", selectKind = FieldType.ENUM),
    FieldTypeOption("Pilihan ganda", selectKind = FieldType.MULTI_SELECT),
    FieldTypeOption("Rujukan ke Record", needsTarget = true)
)

/** Adds ONE custom field to the CRM Leads schema — `AccessLevel.MANAGE` only (enforced by the caller). */
@Composable
fun AddCustomFieldDialog(
    onDismiss: () -> Unit,
    onAdd: (label: String, type: CrmFieldType, isRequired: Boolean) -> Unit
) {
    var label by remember { mutableStateOf("") }
    var selectedTypeIndex by remember { mutableStateOf(0) }
    var targetResource by remember { mutableStateOf("") }
    var isRequired by remember { mutableStateOf(false) }
    var typeMenuExpanded by remember { mutableStateOf(false) }
    var optionDrafts by remember { mutableStateOf(listOf(nextDraftColor(emptyList()))) }

    val selectedOption = FIELD_TYPE_OPTIONS[selectedTypeIndex]
    // Bentuk target dikunci aturan core yang sama dengan validator spec/server (Kontrak 4:
    // parser tunggal, tanpa fallback senyap) — jadi dialog tidak bisa menyimpan bentuk yang pasti 400.
    val targetValid = !selectedOption.needsTarget ||
        relationTargetFormatError(targetResource.trim()) == null
    // Kolom pilihan minimal punya satu opsi berlabel; baris kosong tidak pernah tersimpan.
    val optionsValid = !selectedOption.needsOptions || hasActiveDraft(optionDrafts)
    val canAdd = label.isNotBlank() && targetValid && optionsValid

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
                            text = selectedOption.label,
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

                if (selectedOption.needsTarget) {
                    ClayTextField(
                        value = targetResource,
                        onValueChange = { targetResource = it },
                        label = "Resource Target",
                        placeholder = "mis. crm_sales atau crm_sales:leads",
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "Isi kode modul target (mis. crm_sales) atau module:entity (mis. crm_sales:leads).",
                        fontSize = 10.sp,
                        color = WeMadeColors.OnSurfaceMuted
                    )
                }

                if (selectedOption.needsOptions) {
                    Column {
                        Text(
                            text = "Pilihan",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = WeMadeColors.OnSurface
                        )
                        SelectOptionsEditor(
                            options = optionDrafts,
                            onOptionsChange = { optionDrafts = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = ClaySpacing.Xs)
                        )
                        Text(
                            text = "Baris tanpa label diabaikan saat disimpan.",
                            fontSize = 10.sp,
                            color = WeMadeColors.OnSurfaceMuted,
                            modifier = Modifier.padding(top = ClaySpacing.Xs)
                        )
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
                    onClick = {
                        val type = when {
                            selectedOption.needsTarget ->
                                CrmFieldType(FieldType.RELATION, targetResource = targetResource.trim())
                            selectedOption.selectKind != null ->
                                CrmFieldType(selectedOption.selectKind, options = buildSelectOptions(optionDrafts))
                            else -> selectedOption.build?.invoke()
                        }
                        if (type != null) onAdd(label, type, isRequired)
                    },
                    enabled = canAdd,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

package com.eventverse.app.presentation.crm.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.sp
import com.eventverse.app.domain.crm.LeadFieldDescriptor
import com.eventverse.app.domain.customfield.CustomAttributes
import com.eventverse.app.domain.customfield.FieldType
import com.eventverse.app.domain.orgchart.OrgNode
import com.eventverse.app.presentation.designsystem.ClayBadge
import com.eventverse.app.presentation.designsystem.ClayCheckbox
import com.eventverse.app.presentation.designsystem.ClayDatePicker
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.ClayTextField
import com.eventverse.app.presentation.theme.WeMadeColors
import com.eventverse.app.shared.json.JsonValue
import androidx.compose.ui.graphics.Color as ComposeColor

private const val DATE_TIME_TEXT_HINT = "YYYY-MM-DD"

/**
 * One row of the lead inspector: a [LeadFieldDescriptor.label] plus an editor matching its
 * [FieldType] — text, number, single-select (a coloured pill dropdown), date, checkbox, or
 * a person picker for `UserRef`. Read-only mode (no [onCommit]) renders the same layout
 * without an interactive control, so the visual shape never jumps when access level changes.
 *
 * This is the "renderer" the plan calls for, kept feature-local rather than in
 * `designsystem/` because it takes a [LeadFieldDescriptor] and [FieldType] directly — a
 * domain-blind version would need `presentation/designsystem/ClayDynamicField` with plain
 * `String`/enum parameters, which is deferred until a second module needs this renderer too
 * (Rule of Three: one caller today, not three).
 */
@Composable
fun LeadCustomField(
    descriptor: LeadFieldDescriptor,
    cell: JsonValue.Obj?,
    editable: Boolean,
    employees: List<OrgNode> = emptyList(),
    onDelete: (() -> Unit)? = null,
    onCommit: ((JsonValue.Obj?) -> Unit)? = null
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = ClaySpacing.Sm)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xs),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Text(
                    text = descriptor.label,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = WeMadeColors.OnSurfaceMuted
                )
                if (descriptor.isRequired) {
                    Text(text = "*", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = WeMadeColors.Error)
                }
            }
            if (onDelete != null && descriptor.isDeletable) {
                Text(
                    text = "Hapus",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = WeMadeColors.Error,
                    modifier = Modifier.clickable(onClick = onDelete)
                )
            }
        }

        Column(modifier = Modifier.padding(top = ClaySpacing.Xs)) {
            // Kontrol dipilih lewat pemeta murni agar paritas tipe->kontrol bisa dites (LeadFieldControl).
            val datePicker = leadFieldControl(descriptor.type) == LeadFieldControl.DATE_PICKER
            when (val type = descriptor.type) {
                is FieldType.Text, is FieldType.LongText -> TextEditor(cell, editable, onCommit) { CustomAttributes.textCell(it) }
                is FieldType.Number -> TextEditor(cell, editable, onCommit) { CustomAttributes.numberCell(it) }
                is FieldType.Checkbox -> CheckboxEditor(cell, editable, onCommit)
                is FieldType.DateField -> if (datePicker) {
                    TextEditor(
                        cell, editable, onCommit,
                        input = { text, onChange ->
                            ClayDatePicker(
                                value = text,
                                onValueChange = onChange,
                                // Label sudah dirender pada header baris; dikosongkan agar tidak dobel.
                                label = "",
                                modifier = Modifier.fillMaxWidth(),
                                isError = text.isNotBlank() && !isBlankOrIsoDate(text)
                            )
                        }
                    ) { CustomAttributes.textCell(it) }
                } else {
                    // withTime: ClayDatePicker belum mendukung waktu; perilaku kolom teks lama dipertahankan.
                    TextEditor(cell, editable, onCommit, placeholder = DATE_TIME_TEXT_HINT) {
                        CustomAttributes.textCell(it)
                    }
                }
                is FieldType.SingleSelect -> SelectEditor(type, cell, editable, onCommit)
                is FieldType.UserRef -> UserRefEditor(cell, employees, editable, onCommit)
            }
        }
    }
}

@Composable
private fun TextEditor(
    cell: JsonValue.Obj?,
    editable: Boolean,
    onCommit: ((JsonValue.Obj?) -> Unit)?,
    placeholder: String? = null,
    input: (@Composable (text: String, onChange: (String) -> Unit) -> Unit)? = null,
    buildCell: (String) -> JsonValue.Obj
) {
    val initialText = remember(cell) { cell?.let { it.entries["v"] }?.let(::rawText) ?: "" }
    var text by remember(cell) { mutableStateOf(initialText) }

    if (editable && onCommit != null) {
        if (input != null) {
            input(text) { text = it }
        } else {
            ClayTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = placeholder,
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
        // Hanya commit jika user benar-benar mengubah text dari nilai awalnya (initialText),
        // dengan jeda debounce 600ms agar tidak membanjiri server dan tidak mentrigger patch saat baru membuka lead.
        androidx.compose.runtime.LaunchedEffect(text) {
            if (text == initialText) return@LaunchedEffect
            kotlinx.coroutines.delay(600)
            onCommit(text.takeIf { it.isNotBlank() }?.let(buildCell))
        }
    } else {
        Text(text = text.ifBlank { "—" }, fontSize = 13.sp, color = WeMadeColors.OnSurface)
    }
}

private fun rawText(value: JsonValue): String = when (value) {
    is JsonValue.Str -> value.value
    is JsonValue.Num -> value.raw
    is JsonValue.Bool -> value.value.toString()
    else -> ""
}

@Composable
private fun CheckboxEditor(cell: JsonValue.Obj?, editable: Boolean, onCommit: ((JsonValue.Obj?) -> Unit)?) {
    val checked = cell?.boolean("v") ?: false
    if (editable && onCommit != null) {
        ClayCheckbox(checked = checked, onCheckedChange = { onCommit(CustomAttributes.checkboxCell(it)) })
    } else {
        Text(text = if (checked) "Ya" else "Tidak", fontSize = 13.sp, color = WeMadeColors.OnSurface)
    }
}

@Composable
private fun SelectEditor(
    type: FieldType.SingleSelect,
    cell: JsonValue.Obj?,
    editable: Boolean,
    onCommit: ((JsonValue.Obj?) -> Unit)?
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedId = cell?.string("v")
    val selectedOption = type.options.firstOrNull { it.id.value == selectedId }

    if (editable && onCommit != null) {
        Row {
            ClayBadge(
                text = selectedOption?.label ?: "Pilih…",
                tint = selectedOption?.let { ComposeColor(parseHex(it.colorHex)) } ?: WeMadeColors.OnSurfaceMuted,
                modifier = Modifier.clickable { expanded = true }
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            type.activeOptions.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = {
                        expanded = false
                        onCommit(CustomAttributes.selectCell(option.id))
                    }
                )
            }
        }
    } else {
        ClayBadge(
            text = selectedOption?.label ?: "—",
            tint = selectedOption?.let { ComposeColor(parseHex(it.colorHex)) } ?: WeMadeColors.OnSurfaceMuted
        )
    }
}

@Composable
private fun UserRefEditor(
    cell: JsonValue.Obj?,
    employees: List<OrgNode>,
    editable: Boolean,
    onCommit: ((JsonValue.Obj?) -> Unit)?
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedId = cell?.string("v")
    val selected = employees.firstOrNull { it.id.value == selectedId }

    if (editable && onCommit != null) {
        Row {
            ClayBadge(
                text = selected?.name ?: "Belum ditugaskan",
                tint = WeMadeColors.Primary,
                modifier = Modifier.clickable { expanded = true }
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("Belum ditugaskan") }, onClick = { expanded = false; onCommit(null) })
            employees.forEach { employee ->
                DropdownMenuItem(
                    text = { Text(employee.name) },
                    onClick = {
                        expanded = false
                        onCommit(CustomAttributes.textCell(employee.id.value))
                    }
                )
            }
        }
    } else {
        Text(text = selected?.name ?: "—", fontSize = 13.sp, color = WeMadeColors.OnSurface)
    }
}

private fun parseHex(hex: String): Long {
    val cleaned = hex.removePrefix("#")
    return ("FF$cleaned").toLong(16)
}

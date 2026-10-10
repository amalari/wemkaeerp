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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.eventverse.app.domain.crm.LeadFieldDescriptor
import com.eventverse.app.domain.crm.LeadId
import com.eventverse.app.domain.customfield.CustomAttributes
import com.eventverse.app.domain.customfield.FieldType
import com.eventverse.app.domain.orgchart.OrgNode
import com.eventverse.app.infrastructure.api.StoredTenantSlugProvider
import com.eventverse.app.presentation.common.FIELD_FILE_ACCEPT
import com.eventverse.app.presentation.common.fieldFileClientSizeError
import com.eventverse.app.presentation.common.fieldFileErrorMessage
import com.eventverse.app.presentation.common.fieldFileSizeHint
import com.eventverse.app.presentation.common.fileRefDisplayName
import com.eventverse.app.presentation.deal.openInBrowser
import com.eventverse.app.presentation.deal.pickLocalFile
import com.eventverse.app.presentation.designsystem.ClayBadge
import com.eventverse.app.presentation.designsystem.ClayCheckbox
import com.eventverse.app.presentation.designsystem.ClayDatePicker
import com.eventverse.app.presentation.designsystem.ClayDateTimePicker
import com.eventverse.app.presentation.designsystem.ClayFileField
import com.eventverse.app.presentation.designsystem.ClayFileFieldState
import com.eventverse.app.presentation.designsystem.ClayRelationPicker
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.ClayTextField
import com.eventverse.app.presentation.designsystem.displayIsoDateTime
import com.eventverse.app.presentation.relation.RelationFieldUi
import com.eventverse.app.presentation.relation.cachedRelationLabel
import com.eventverse.app.presentation.relation.relationDisplay
import com.eventverse.app.presentation.relation.relationFieldControllerForResource
import com.eventverse.app.presentation.theme.WeMadeColors
import com.eventverse.app.shared.json.JsonValue
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.Color as ComposeColor

/**
 * Aksi jaringan berkas untuk field `FILE` (C8, TRD-FIELD-002 §4.4) — dihubungkan dari
 * [com.eventverse.app.presentation.crm.CrmViewModel] melalui [CrmUiEvent]. Null di seluruh
 * rantai UI berarti tampilan baca-saja; pola callback-nya sama dengan unggah bukti fulfilment.
 */
class LeadFieldFileActions(
    val upload: (
        leadId: LeadId,
        fieldId: String,
        fileName: String,
        contentType: String,
        bytes: ByteArray,
        onDone: (Result<String>) -> Unit
    ) -> Unit,
    val download: (leadId: LeadId, fieldId: String, onDone: (Result<String>) -> Unit) -> Unit
)

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
    onCommit: ((JsonValue.Obj?) -> Unit)? = null,
    leadId: LeadId? = null,
    fieldFileActions: LeadFieldFileActions? = null
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
            when (val type = descriptor.type) {
                is FieldType.Text -> TextEditor(cell, editable, onCommit) { CustomAttributes.textCell(it) }
                is FieldType.LongText -> TextEditor(cell, editable, onCommit, singleLine = false, minLines = 3) { CustomAttributes.textCell(it) }
                is FieldType.Number -> TextEditor(cell, editable, onCommit) { CustomAttributes.numberCell(it) }
                is FieldType.Checkbox -> CheckboxEditor(cell, editable, onCommit)
                is FieldType.DateField -> if (type.withTime) {
                    // C6 (Irisan 2): ClayDateTimePicker, format simpan `TTTT-BB-HH'T'JJ:MM` — sama dengan prototype.
                    TextEditor(
                        cell, editable, onCommit,
                        input = { text, onChange ->
                            ClayDateTimePicker(
                                value = text,
                                onValueChange = onChange,
                                // Label sudah dirender pada header baris; dikosongkan agar tidak dobel.
                                label = "",
                                modifier = Modifier.fillMaxWidth()
                            )
                        },
                        // Nilai lama `TTTT-BB-HH` tampil apa adanya (pola prototype), bukan crash.
                        displayText = ::displayIsoDateTime
                    ) { CustomAttributes.textCell(it) }
                } else {
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
                }
                is FieldType.SingleSelect -> SelectEditor(type, cell, editable, onCommit)
                is FieldType.UserRef -> UserRefEditor(cell, employees, editable, onCommit)
                // C7 (TRD-FIELD-001 Track C): pemilih rujukan (ClayRelationPicker) saat editable;
                // baca-saja menampilkan label/fallback id/"tidak ditemukan". Penulisan tetap
                // divalidasi server (RelationTargetResolver). Tidak dipalsukan jadi kolom teks.
                is FieldType.Relation -> RelationEditor(
                    type = type,
                    cell = cell,
                    editable = editable,
                    onCommit = onCommit
                )
                // C8 (TRD-FIELD-002 Track C): ClayFileField — unggah/ganti/hapus bila aksi tersedia.
                is FieldType.File -> FileEditor(
                    fieldId = descriptor.fieldId,
                    leadId = leadId,
                    cell = cell,
                    editable = editable,
                    actions = fieldFileActions,
                    onCommit = onCommit
                )
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
    singleLine: Boolean = true,
    minLines: Int = 1,
    input: (@Composable (text: String, onChange: (String) -> Unit) -> Unit)? = null,
    /** Penampil nilai saat baca-saja; default apa adanya (mis. tanggal berwaktu lewat `displayIsoDateTime`). */
    displayText: (String) -> String = { it },
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
                singleLine = singleLine,
                minLines = minLines,
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
        Text(text = displayText(text).ifBlank { "—" }, fontSize = 13.sp, color = WeMadeColors.OnSurface)
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

/**
 * Editor field `FILE` (C8, TRD-FIELD-002 Track C) di atas [ClayFileField]. Referensi `fields/...`
 * adalah satu-satunya nilai sel; unggah sukses langsung di-commit ke sel (pola CommitField biasa).
 * v1 kontrak §4.4: unggah hanya untuk lead yang sudah ada — dialog pembuatan lead tanpa aksi ini
 * menampilkan tampilan kosong, bukan pemilih palsu.
 */
@Composable
private fun FileEditor(
    fieldId: String,
    leadId: LeadId?,
    cell: JsonValue.Obj?,
    editable: Boolean,
    actions: LeadFieldFileActions?,
    onCommit: ((JsonValue.Obj?) -> Unit)?
) {
    val ref = cell?.string("v").orEmpty()
    val scope = rememberCoroutineScope()
    var uploadState by remember(cell) { mutableStateOf<ClayFileFieldState>(ClayFileFieldState.Idle) }
    val canUpload = editable && onCommit != null && actions != null && leadId != null &&
        uploadState !is ClayFileFieldState.Uploading

    if (ref.isBlank() && !canUpload) {
        Text(
            text = if (ref.isBlank()) "—" else fileRefDisplayName(ref),
            fontSize = 13.sp,
            color = if (ref.isBlank()) WeMadeColors.OnSurfaceMuted else WeMadeColors.OnSurface
        )
        return
    }

    ClayFileField(
        fileName = if (ref.isBlank()) "" else fileRefDisplayName(ref),
        sizeLabel = fieldFileSizeHint(),
        state = uploadState,
        onPick = {
            if (canUpload) {
                val boundActions = actions
                val boundLeadId = leadId
                if (boundActions != null && boundLeadId != null) {
                    scope.launch {
                        val picked = pickLocalFile(FIELD_FILE_ACCEPT) ?: return@launch
                        val sizeError = fieldFileClientSizeError(picked.bytes.size.toLong())
                        if (sizeError != null) {
                            uploadState = ClayFileFieldState.Error(sizeError)
                            return@launch
                        }
                        uploadState = ClayFileFieldState.Uploading(progress = null)
                        boundActions.upload(
                            boundLeadId, fieldId, picked.fileName, picked.mimeType, picked.bytes
                        ) { result ->
                            result.fold(
                                onSuccess = { newRef ->
                                    uploadState = ClayFileFieldState.Ready
                                    onCommit?.invoke(CustomAttributes.textCell(newRef))
                                },
                                onFailure = { err ->
                                    uploadState = ClayFileFieldState.Error(fieldFileErrorMessage(err))
                                }
                            )
                        }
                    }
                }
            }
        },
        onDownload = if (ref.isNotBlank() && actions != null && leadId != null) {
            {
                val boundActions = actions
                val boundLeadId = leadId
                scope.launch {
                    boundActions.download(boundLeadId, fieldId) { result ->
                        result.onSuccess { url -> openInBrowser(url) }
                            .onFailure { err -> uploadState = ClayFileFieldState.Error(fieldFileErrorMessage(err)) }
                    }
                }
            }
        } else null,
        onRemove = if (ref.isNotBlank() && canUpload) {
            {
                uploadState = ClayFileFieldState.Idle
                onCommit?.invoke(null)
            }
        } else null,
        enabled = canUpload,
        isError = uploadState is ClayFileFieldState.Error,
        label = ""
    )
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

/**
 * Editor field `Relation` (C7, TRD-FIELD-001 Track C) di atas [ClayRelationPicker]. Opsi dimuat
 * dari route `GET /api/tenant/relation-options` (Track B) lewat [relationFieldControllerForResource].
 * Bila penyuplai opsi tak tersedia (mis. sesi tanpa tenant), tampil baca-saja label/fallback id —
 * kontrol yang belum bisa jangan dipalsukan jadi kolom teks (field-component-rules Kontrak 8).
 */
@Composable
private fun RelationEditor(
    type: FieldType.Relation,
    cell: JsonValue.Obj?,
    editable: Boolean,
    onCommit: ((JsonValue.Obj?) -> Unit)?
) {
    val selectedId = cell?.string("v")?.trim()?.takeIf { it.isNotEmpty() }
    val relationUi = rememberLeadRelationUi(type.targetResource)

    if (editable && onCommit != null && relationUi != null) {
        ClayRelationPicker(
            query = relationUi.query,
            onQueryChange = relationUi::onQueryChange,
            options = relationUi.options,
            selectedId = selectedId,
            onSelect = { option ->
                relationUi.onSelect(option)
                onCommit(option?.let { CustomAttributes.textCell(it.id) })
            },
            label = "",
            selectedLabel = selectedId?.let { relationUi.labelFor(it) },
            isLoading = relationUi.isLoading
        )
        relationUi.error?.let { msg ->
            Text(text = msg, fontSize = 11.sp, color = WeMadeColors.Error)
        }
    } else {
        val display = relationDisplay(selectedId.orEmpty(), cachedRelationLabel { id -> relationUi?.labelFor(id) })
        Text(
            text = display.text,
            fontSize = 13.sp,
            color = if (display.missing) WeMadeColors.OnSurfaceMuted else WeMadeColors.OnSurface
        )
    }
}

@Composable
private fun rememberLeadRelationUi(resource: String): RelationFieldUi? {
    val scope = rememberCoroutineScope()
    val tenantSlug = remember { StoredTenantSlugProvider.currentTenantSlug() }
    val controller = remember(resource, tenantSlug) {
        relationFieldControllerForResource(tenantSlug, resource, scope)
    }
    LaunchedEffect(controller) { controller?.prime() }
    return controller
}

private fun parseHex(hex: String): Long {
    val cleaned = hex.removePrefix("#")
    return ("FF$cleaned").toLong(16)
}

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.eventverse.app.domain.prototype.FieldSpec
import com.eventverse.app.domain.prototype.FieldType
import com.eventverse.app.presentation.common.FIELD_FILE_ACCEPT
import com.eventverse.app.presentation.common.fieldFileClientSizeError
import com.eventverse.app.presentation.common.fieldFileErrorMessage
import com.eventverse.app.presentation.common.fieldFileSizeHint
import com.eventverse.app.presentation.common.fileRefDisplayName
import com.eventverse.app.presentation.deal.openInBrowser
import com.eventverse.app.presentation.deal.pickLocalFile
import com.eventverse.app.presentation.designsystem.ClayCheckbox
import com.eventverse.app.presentation.designsystem.ClayChoiceChip
import com.eventverse.app.presentation.designsystem.ClayDatePicker
import com.eventverse.app.presentation.designsystem.ClayDateTimePicker
import com.eventverse.app.presentation.designsystem.ClayFileField
import com.eventverse.app.presentation.designsystem.ClayFileFieldState
import com.eventverse.app.presentation.designsystem.ClayFlowRow
import com.eventverse.app.presentation.designsystem.ClayRelationPicker
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.ClayTextArea
import com.eventverse.app.presentation.designsystem.ClayTextField
import com.eventverse.app.presentation.relation.RelationFieldUi
import com.eventverse.app.presentation.relation.relationDisplay
import com.eventverse.app.presentation.theme.WeMadeColors
import kotlinx.coroutines.launch

/**
 * Komponen input bersama untuk tipe data prototipe (TRD-PLAT-003, butir A3 — Aturan Tiga Kali).
 * Digunakan secara konsisten di Form Blok ([InteractiveForm]), Form Inline Tabel ([InlineRowEditor]),
 * dan Dialog Form Detail Kanban ([KanbanDetailDialog]).
 *
 * Pemetaan [FieldType]:
 * - TEXT -> [ClayTextField]; bila [FieldSpec.validation] EMAIL/PHONE: keyboard sesuai + galat bentuk (tanpa normalisasi)
 * - LONG_TEXT -> [ClayTextArea] area teks multi-baris
 * - NUMBER -> [ClayTextField] dengan prefix/suffix format (Rp/kode, %; lihat NumberFormatting.kt); nilai simpan tetap angka polos
 * - DATE -> [ClayDatePicker] (TTTT-BB-HH); dengan [FieldSpec.withTime] -> [ClayDateTimePicker] (TTTT-BB-HHTJJ:MM)
 * - ENUM -> Pilihan opsi menggunakan [ClayChoiceChip]
 * - BOOL -> [ClayCheckbox] dengan status "ya" / "tidak"
 * - RELATION -> [ClayRelationPicker] (C7/TRD-FIELD-001 Track C) bila host menyuplai [relation];
 *   tanpa penyuplai (mis. demo memori) tampil baca-saja id/label — dilarang memalsukan rujukan
 *   jadi kolom teks bebas
 * - FILE -> [ClayFileField] (C8/TRD-FIELD-002 Track C): unggah pertama/ganti/hapus lewat
 *   [FileFieldOps] bila record sudah punya id server; byte tidak pernah lewat sel, hanya key
 *   `fields/...`. Tanpa ops = chip baca-saja / penjelasan bahwa unggah menyusul setelah data ada.
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
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    fileOps: FileFieldOps? = null,
    relation: RelationFieldUi? = null
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

        // Galat dari pemanggil menang; bila tidak ada, galat bentuk (email/telepon) dari aturan core `accepts`.
        val shownError = errorMessage ?: field.validationMessageFor(value)

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
                if (field.withTime) {
                    ClayDateTimePicker(
                        value = value,
                        onValueChange = onValueChange,
                        label = "",
                        modifier = Modifier.fillMaxWidth(),
                        enabled = enabled,
                        isError = errorMessage != null
                    )
                } else {
                    ClayDatePicker(
                        value = value,
                        onValueChange = onValueChange,
                        label = "",
                        modifier = Modifier.fillMaxWidth(),
                        enabled = enabled,
                        isError = errorMessage != null
                    )
                }
            }
            FieldType.TEXT -> {
                ClayTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = if (compact) field.label else "Isi ${field.label.lowercase()}...",
                    enabled = enabled,
                    isError = shownError != null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = keyboardTypeFor(field.validation)),
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
            // C7 (TRD-FIELD-001 Track C): nilai RELATION = id baris target yang diverifikasi server.
            // Dengan penyuplai opsi, render ClayRelationPicker; tanpa itu (demo memori / belum ada
            // server) tampil baca-saja label/id — bukan kolom teks bebas.
            FieldType.RELATION -> {
                if (relation != null && enabled) {
                    ClayRelationPicker(
                        query = relation.query,
                        onQueryChange = relation::onQueryChange,
                        options = relation.options,
                        selectedId = value.trim().ifEmpty { null },
                        onSelect = { option ->
                            onValueChange(option?.id ?: "")
                            relation.onSelect(option)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = enabled,
                        isError = shownError != null,
                        label = "",
                        selectedLabel = relation.labelFor(value.trim()),
                        isLoading = relation.isLoading
                    )
                    relation.error?.let { msg ->
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.bodySmall,
                            color = WeMadeColors.Error
                        )
                    }
                } else {
                    val display = relationDisplay(value) { id -> relation?.labelFor(id) }
                    Text(
                        text = display.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (display.missing) WeMadeColors.OnSurfaceMuted else WeMadeColors.OnSurface
                    )
                }
            }
            // C8 (TRD-FIELD-002 Track C): nilai FILE = key `fields/...` (byte di ObjectStorage).
            // Unggah/ganti/hapus hanya bila [fileOps] tersedia (record sudah ber-id server);
            // unduh lewat URL presigned. Batas ukuran dijaga di klien + pesan server dipetakan.
            FieldType.FILE -> FileFieldInput(
                field = field,
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                isError = errorMessage != null,
                fileOps = fileOps
            )
        }

        if (shownError != null) {
            Text(
                text = shownError,
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

/**
 * Cabang kontrol field `FILE` (C8, TRD-FIELD-002 Track C) di atas [ClayFileField].
 *
 * Alur unggah mengikuti pola yang sudah terbukti (bukti foto/mockup deal & fulfilment):
 * picker platform (`pickLocalFile`) → jaga batas 10 MB di klien → unggah byte via [FileFieldOps]
 * → sukses = sel diisi referensi `fields/...` dari server. Progres v1 determinate belum tersedia
 * dari transport, jadi bar aktifitasnya bergerak (lihat `ClayFileFieldState.Uploading`).
 */
@Composable
private fun FileFieldInput(
    field: FieldSpec,
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    isError: Boolean,
    fileOps: FileFieldOps?
) {
    val scope = rememberCoroutineScope()
    var uploadState by remember(field.key) { mutableStateOf<ClayFileFieldState>(ClayFileFieldState.Idle) }
    val ref = value.trim()
    val canUpload = enabled && fileOps != null && uploadState !is ClayFileFieldState.Uploading

    if (ref.isBlank() && fileOps == null) {
        // Kontrak §4.4 mengunci recordId di path endpoint: record baru belum bisa menerima unggah.
        // Penjelasan jujur, bukan pemilih yang tidak akan pernah berhasil (Kontrak 8).
        Text(
            text = "Lampiran dapat diunggah setelah data tersimpan.",
            style = MaterialTheme.typography.labelSmall,
            color = WeMadeColors.OnSurfaceMuted
        )
        return
    }

    ClayFileField(
        fileName = if (ref.isBlank()) "" else fileRefDisplayName(ref),
        sizeLabel = fieldFileSizeHint(),
        state = uploadState,
        onPick = {
            if (canUpload) {
                scope.launch {
                    val ops = fileOps ?: return@launch
                    val picked = pickLocalFile(FIELD_FILE_ACCEPT) ?: return@launch
                    val sizeError = fieldFileClientSizeError(picked.bytes.size.toLong())
                    if (sizeError != null) {
                        uploadState = ClayFileFieldState.Error(sizeError)
                        return@launch
                    }
                    uploadState = ClayFileFieldState.Uploading(progress = null)
                    ops.upload(field.key, picked.fileName, picked.mimeType, picked.bytes) { result ->
                        result.fold(
                            onSuccess = { newRef ->
                                uploadState = ClayFileFieldState.Ready
                                onValueChange(newRef)
                            },
                            onFailure = { err ->
                                uploadState = ClayFileFieldState.Error(fieldFileErrorMessage(err))
                            }
                        )
                    }
                }
            }
        },
        onDownload = if (ref.isNotBlank() && fileOps != null) {
            {
                scope.launch {
                    val ops = fileOps
                    if (ops != null) {
                        ops.downloadUrl(field.key, ref) { result ->
                            result.onSuccess { url -> openInBrowser(url) }
                                .onFailure { err ->
                                    uploadState = ClayFileFieldState.Error(fieldFileErrorMessage(err))
                                }
                        }
                    }
                }
            }
        } else null,
        onRemove = if (ref.isNotBlank() && fileOps != null && enabled) {
            {
                uploadState = ClayFileFieldState.Idle
                onValueChange("")
            }
        } else null,
        enabled = canUpload,
        isError = isError || uploadState is ClayFileFieldState.Error
    )
}

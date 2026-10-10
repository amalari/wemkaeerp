package com.eventverse.app.presentation.designsystem

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Satu opsi rujukan yang bisa dipilih — tipe UI lokal, **tanpa** ketergantungan domain
 * (design-system-rules Kontrak 6). Fitur yang menerjemahkan record domain ke bentuk ini.
 */
data class RelationOption(val id: String, val label: String) {
    init {
        require(id.isNotBlank()) { "RelationOption.id cannot be blank" }
    }
}

/** Label saat kueri tidak menemukan opsi apa pun. */
const val RELATION_NOT_FOUND_LABEL = "Tidak ditemukan"

/**
 * Penyaring murni (tanpa Compose) yang dipakai [ClayRelationPicker] — diekstrak supaya
 * perilaku pencarian bisa dites tanpa rendering.
 */
fun filterRelationOptions(options: List<RelationOption>, query: String): List<RelationOption> {
    val q = query.trim()
    if (q.isEmpty()) return options
    return options.filter {
        it.label.contains(q, ignoreCase = true) || it.id.contains(q, ignoreCase = true)
    }
}

/**
 * Pemilih rujukan bersama (C7, TRD-FIELD-001 FR-7) untuk tipe `RELATION` prototype dan
 * `Relation` CRM. **Buta domain**: menerima `String`/lambda dan [RelationOption], bukan
 * `FieldSpec`/`LeadFieldDescriptor`.
 *
 * Alur: kotak cari ([ClayTextField]) memanggil [onQueryChange]; host memuat opsi dari route
 * `GET /api/tenant/relation-options` dan mengembalikannya lewat [options]. Memilih opsi memanggil
 * [onSelect]; memilih ulang untuk mengosongkan (simbol ✕) mengirim `null`.
 *
 * Seluruh styling memakai token Clay; nol literal warna, nol `Modifier.shadow()`.
 */
@Composable
fun ClayRelationPicker(
    query: String,
    onQueryChange: (String) -> Unit,
    options: List<RelationOption>,
    selectedId: String?,
    onSelect: (RelationOption?) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isError: Boolean = false,
    label: String? = null,
    selectedLabel: String? = null,
    isLoading: Boolean = false,
    placeholder: String = "Cari rujukan..."
) {
    val trimmedSelected = selectedId?.trim()?.takeIf { it.isNotEmpty() }
    val visible = filterRelationOptions(options, query)

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)) {
        if (label != null) {
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = WeMadeColors.OnSurface
            )
        }

        if (trimmedSelected != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .clayFlat(
                            shape = ClayShapes.Pill,
                            background = WeMadeColors.PrimaryContainer,
                            outline = WeMadeColors.Primary,
                            borderWidth = ClayBorder.Hairline
                        )
                        .padding(horizontal = ClaySpacing.Md, vertical = ClaySpacing.Xs),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = selectedLabel ?: trimmedSelected,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = WeMadeColors.PrimaryDark,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (enabled) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clickable { onSelect(null) },
                        contentAlignment = Alignment.Center
                    ) {
                        IconClose(Modifier.size(12.dp), color = WeMadeColors.OnSurfaceMuted)
                    }
                }
            }
        }

        ClayTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = placeholder,
            enabled = enabled,
            isError = isError,
            singleLine = true,
            leadingIcon = { IconSearch(Modifier.size(14.dp), color = WeMadeColors.OnSurfaceMuted) }
        )

        if (enabled && (visible.isNotEmpty() || query.isNotBlank())) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clayFlat(
                        shape = ClayShapes.Chip,
                        background = WeMadeColors.Surface,
                        outline = WeMadeColors.Outline,
                        borderWidth = ClayBorder.Hairline
                    )
                    .padding(vertical = ClaySpacing.Xs)
            ) {
                when {
                    isLoading -> ListHint("Mencari...")
                    visible.isEmpty() -> ListHint(RELATION_NOT_FOUND_LABEL)
                    else -> visible.take(8).forEach { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(option) }
                                .padding(horizontal = ClaySpacing.Md, vertical = ClaySpacing.Sm),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
                        ) {
                            Text(
                                text = option.label,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = WeMadeColors.OnSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            if (option.id == trimmedSelected) {
                                IconCheck(Modifier.size(13.dp), color = WeMadeColors.Success)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ListHint(text: String) {
    Text(
        text = text,
        fontSize = 11.sp,
        color = WeMadeColors.OnSurfaceMuted,
        modifier = Modifier.padding(horizontal = ClaySpacing.Md, vertical = ClaySpacing.Xs)
    )
}

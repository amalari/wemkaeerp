package com.eventverse.app.presentation.discovery.studio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.eventverse.app.presentation.designsystem.ClayButton
import com.eventverse.app.presentation.designsystem.ClayButtonStyle
import com.eventverse.app.presentation.designsystem.ClayCard
import com.eventverse.app.presentation.designsystem.ClayOffset
import com.eventverse.app.presentation.designsystem.ClayShapes
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.ClayTag
import com.eventverse.app.presentation.designsystem.ClayTextField
import com.eventverse.app.presentation.designsystem.IconSearch
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Daftar pola yang sudah tersimpan — sisi kiri Studio.
 *
 * Pemilihan dibedakan lewat **warna outline** (`ClayCard.selected`), bukan ketebalan, mengikuti
 * Kontrak 8 design system; menebalkan kartu terpilih membuat tata letak bergeser setiap kali
 * pengguna berpindah pola.
 */
@Composable
fun PrototypePatternGallery(
    patterns: List<PrototypePatternUi>,
    selectedId: String?,
    query: String,
    onQueryChange: (String) -> Unit,
    onSelect: (PrototypePatternUi) -> Unit,
    onNew: () -> Unit,
    loaded: Boolean,
    modifier: Modifier = Modifier
) {
    val visible = patterns.filter { pattern ->
        query.isBlank() ||
            pattern.name.contains(query, ignoreCase = true) ||
            pattern.widgetLabel.contains(query, ignoreCase = true) ||
            pattern.packLabel.contains(query, ignoreCase = true)
    }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)) {
        ClayTextField(
            value = query,
            onValueChange = onQueryChange,
            label = "Cari pola",
            placeholder = "nama, widget, atau pack",
            leadingIcon = { IconSearch(modifier = Modifier.size(14.dp)) }
        )
        ClayButton(text = "+ Pola baru", onClick = onNew, style = ClayButtonStyle.Secondary, modifier = Modifier.fillMaxWidth())
        when {
            !loaded -> Text(
                "Memuat pola...",
                style = MaterialTheme.typography.labelSmall,
                color = WeMadeColors.OnSurfaceMuted
            )
            patterns.isEmpty() -> Text(
                "Belum ada pola tersimpan. Pola pertama biasanya layar yang paling sering diminta prospek.",
                style = MaterialTheme.typography.labelSmall,
                color = WeMadeColors.OnSurfaceMuted
            )
            visible.isEmpty() -> Text(
                "Tidak ada pola yang cocok dengan pencarian \"$query\".",
                style = MaterialTheme.typography.labelSmall,
                color = WeMadeColors.OnSurfaceMuted
            )
            else -> visible.forEach { pattern ->
                PatternRow(
                    pattern = pattern,
                    selected = pattern.id == selectedId,
                    onClick = { onSelect(pattern) }
                )
            }
        }
    }
}

@Composable
private fun PatternRow(pattern: PrototypePatternUi, selected: Boolean, onClick: () -> Unit) {
    ClayCard(
        modifier = Modifier.fillMaxWidth(),
        shape = ClayShapes.Tile,
        offset = ClayOffset.Small,
        selected = selected,
        // Bahasa visual memakai bahasa yang sama untuk "terpilih": warna outline (Kontrak 8).
        outlineColor = if (selected) WeMadeColors.Primary else WeMadeColors.Outline,
        contentPadding = PaddingValues(ClaySpacing.Lg),
        onClick = onClick
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
            Text(
                text = pattern.name.ifBlank { "(tanpa nama)" },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)) {
            ClayTag(text = pattern.widgetLabel, tint = WeMadeColors.Primary)
            ClayTag(
                text = pattern.packLabel,
                tint = if (pattern.packCode == null) WeMadeColors.OnSurfaceMuted else WeMadeColors.Accent
            )
            ClayTag(text = "${pattern.rows.size} baris", tint = WeMadeColors.OnSurfaceMuted)
        }
    }
}

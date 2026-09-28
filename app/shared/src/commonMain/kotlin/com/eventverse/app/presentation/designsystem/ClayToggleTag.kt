package com.eventverse.app.presentation.designsystem

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Tag yang bisa dilepas dan dipasang lagi — `[Label ×]` saat aktif, `[+ Label]` hantu saat lepas.
 *
 * Satu komponen untuk dua keadaan (bukan tag + tombol "tambah" terpisah) supaya posisi tag tidak
 * melompat saat di-×: yang berubah hanya rupanya, jadi orang yang salah klik bisa langsung
 * mengembalikannya di titik yang sama.
 *
 * Keadaan dibedakan lewat warna dan isian, bukan ketebalan outline (Kontrak 8).
 *
 * @param enabled `false` = read-only: tampil sesuai keadaan, tanpa × / + dan tanpa klik.
 */
@Composable
fun ClayToggleTag(
    text: String,
    tint: Color,
    active: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val contentColor = if (active) tint else WeMadeColors.OnSurfaceMuted
    Row(
        modifier = modifier
            .clip(ClayShapes.Chip)
            .clayFlat(
                shape = ClayShapes.Chip,
                background = if (active) tint.copy(alpha = 0.12f) else WeMadeColors.Surface,
                outline = if (active) tint.copy(alpha = 0.45f) else WeMadeColors.Border,
                borderWidth = ClayBorder.Hairline
            )
            .then(if (enabled) Modifier.clickable(onClick = onToggle) else Modifier)
            .padding(horizontal = 7.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (!active && enabled) {
            Text(text = "+", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = contentColor)
        }
        Text(
            text = text,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = contentColor,
            maxLines = 1
        )
        if (active && enabled) {
            IconClose(modifier = Modifier.size(9.dp), color = contentColor)
        }
    }
}

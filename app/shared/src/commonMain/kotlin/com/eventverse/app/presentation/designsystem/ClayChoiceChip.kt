package com.eventverse.app.presentation.designsystem

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Pil pilihan tunggal: filter, pemilih satuan, pemilih tab kecil.
 *
 * Sebelum komponen ini ada, pola yang sama ditulis privat di `DealsPane` (`StageFilterChip`),
 * `TechPackListPanel` (`StatusFilterChip`), `ProductionWorkspaceScreen` (`FilterChip`), dan
 * `SetPriceDialog` — empat salinan, melewati Aturan Tiga Kali. Pemakaian baru memakai ini;
 * keempat salinan lama boleh dicicil pindah.
 *
 * Terpilih dibedakan lewat isian dan kedalaman, ketebalan outline tetap (Kontrak 8).
 */
@Composable
fun ClayChoiceChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = WeMadeColors.Primary,
    enabled: Boolean = true,
    fontSize: TextUnit = 11.sp
) {
    Box(
        modifier = modifier
            .clickable(enabled = enabled, onClick = onClick)
            .claySurface(
                shape = ClayShapes.Pill,
                background = if (selected) tint else WeMadeColors.SurfaceMuted,
                outline = if (selected) WeMadeColors.Outline else WeMadeColors.OutlineSoft,
                offset = if (selected) ClayOffset.Small else ClayOffset.Flat,
                borderWidth = ClayBorder.Medium
            )
            .padding(horizontal = ClaySpacing.Md, vertical = ClaySpacing.Xs)
    ) {
        Text(
            text = text,
            fontSize = fontSize,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = when {
                !enabled -> WeMadeColors.OnSurfaceDisabled
                selected -> WeMadeColors.Surface
                else -> WeMadeColors.OnSurfaceMuted
            },
            maxLines = 1
        )
    }
}

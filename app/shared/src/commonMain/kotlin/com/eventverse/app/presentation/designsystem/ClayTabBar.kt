package com.eventverse.app.presentation.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.eventverse.app.presentation.theme.WeMadeColors

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Bilah tab segmen: satu tab terpilih, sisanya redup. Dirakit dari [ClayChoiceChip] sehingga bahasa
 * visualnya sama dengan chip pilihan lain; buta domain — hanya menerima label, indeks, dan lambda.
 *
 * [badges] opsional (mis. jumlah item) ditempel ke label: `"Modul · 9"`.
 */
@Composable
fun ClayTabBar(
    tabs: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    badges: List<String?> = emptyList(),
    tint: Color = WeMadeColors.Primary
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        tabs.forEachIndexed { index, label ->
            val badge = badges.getOrNull(index)
            ClayChoiceChip(
                text = if (badge != null) "$label ($badge)" else label,
                selected = index == selectedIndex,
                onClick = { onSelect(index) },
                tint = tint
            )
        }
    }
}

/**
 * Bilah tab segmen menyatu (Segmented Control): satu kontainer kapsul dengan segmen aktif
 * yang terangkat jelas. Menghindari "pill overload" dari tombol tab yang berhamburan.
 */
@Composable
fun ClaySegmentedBar(
    tabs: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    badges: List<String?> = emptyList(),
    tint: Color = WeMadeColors.Primary
) {
    Box(
        modifier = modifier
            .claySurface(
                shape = ClayShapes.Pill,
                background = WeMadeColors.SurfaceMuted,
                outline = WeMadeColors.Outline,
                offset = ClayOffset.Flat,
                borderWidth = ClayBorder.Hairline
            )
            .padding(2.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEachIndexed { index, label ->
                val isSelected = index == selectedIndex
                val badge = badges.getOrNull(index)
                Box(
                    modifier = Modifier
                        .clickable { onSelect(index) }
                        .claySurface(
                            shape = ClayShapes.Pill,
                            background = if (isSelected) tint else WeMadeColors.SurfaceMuted,
                            outline = if (isSelected) WeMadeColors.Outline else WeMadeColors.SurfaceMuted,
                            offset = if (isSelected) ClayOffset.Small else ClayOffset.Flat,
                            borderWidth = if (isSelected) ClayBorder.Hairline else 0.dp
                        )
                        .padding(horizontal = ClaySpacing.Md, vertical = ClaySpacing.Xs)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xxs)
                    ) {
                        Text(
                            text = label,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) WeMadeColors.Surface else WeMadeColors.OnSurfaceMuted,
                            maxLines = 1
                        )
                        if (badge != null) {
                            Text(
                                text = "($badge)",
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) WeMadeColors.Surface else WeMadeColors.OnSurfaceDisabled,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

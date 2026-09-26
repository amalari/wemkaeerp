package com.eventverse.app.presentation.techpack.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.domain.contracts.SizeYieldFactor
import com.eventverse.app.domain.techpack.TechPack
import com.eventverse.app.presentation.designsystem.*
import com.eventverse.app.presentation.techpack.TechPackUiEvent
import com.eventverse.app.presentation.techpack.toFormattedString
import com.eventverse.app.presentation.techpack.toPercentageString
import com.eventverse.app.presentation.theme.WeMadeColors

@Composable
fun SizeYieldTab(
    techPack: TechPack,
    canManage: Boolean,
    onEvent: (TechPackUiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
    ) {
        // Toolbar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Faktor Skala Ukuran & Yield Bahan",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = WeMadeColors.OnSurface
                )
                ClayBadge(
                    text = "${techPack.sizeYieldFactors.size} Ukuran",
                    tint = WeMadeColors.Primary
                )
                if (techPack.totalOrderedQuantity > 0L) {
                    ClayBadge(
                        text = "Total Order: ${techPack.totalOrderedQuantity} Pcs",
                        tint = WeMadeColors.Teal
                    )
                }
            }

            if (canManage && techPack.isEditable) {
                ClayButton(
                    text = "Kelola Skala Ukuran",
                    onClick = { onEvent(TechPackUiEvent.OpenSizeYieldDialog) },
                    style = ClayButtonStyle.Primary
                )
            }
        }

        // Informational Note
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .claySurface(
                    shape = ClayShapes.Chip,
                    background = WeMadeColors.SurfaceMuted,
                    outline = WeMadeColors.OutlineSoft,
                    offset = ClayOffset.Flat,
                    borderWidth = ClayBorder.Hairline
                )
                .padding(ClaySpacing.Md)
        ) {
            Text(
                text = "Faktor skala mengalikan konsumsi bahan per-garment secara proporsional terhadap ukuran baju (misal ukuran S = 0.9x, XL = 1.25x). Kuantitas order pada masing-masing ukuran dipakai untuk ledakan kebutuhan bahan massal.",
                fontSize = 12.sp,
                color = WeMadeColors.OnSurfaceMuted
            )
        }

        if (techPack.sizeYieldFactors.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .claySurface(
                        shape = ClayShapes.Card,
                        background = WeMadeColors.Surface,
                        outline = WeMadeColors.OutlineSoft,
                        offset = ClayOffset.Small,
                        borderWidth = ClayBorder.Hairline
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)
                ) {
                    Text(
                        text = "Belum Ada Faktor Ukuran (Default 1.0x / All Size)",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = WeMadeColors.OnSurfaceMuted
                    )
                    Text(
                        text = "Klik 'Kelola Skala Ukuran' untuk mengatur variasi ukuran S, M, L, XL, XXL",
                        fontSize = 12.sp,
                        color = WeMadeColors.OnSurfaceMuted
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
            ) {
                items(techPack.sizeYieldFactors, key = { it.sizeLabel }) { factor ->
                    SizeYieldFactorCard(factor = factor)
                }
            }
        }
    }
}

@Composable
private fun SizeYieldFactorCard(factor: SizeYieldFactor) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .claySurface(
                shape = ClayShapes.Tile,
                background = WeMadeColors.Surface,
                outline = WeMadeColors.Outline,
                offset = ClayOffset.Small,
                borderWidth = ClayBorder.Medium
            )
            .padding(ClaySpacing.Md)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .claySurface(
                            shape = ClayShapes.Tile,
                            background = WeMadeColors.PrimaryContainer,
                            outline = WeMadeColors.Outline,
                            offset = ClayOffset.Flat,
                            borderWidth = ClayBorder.Hairline
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = factor.sizeLabel,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = WeMadeColors.Primary
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Skala Multiplier: ${factor.scale.toFormattedString()}x (${factor.scale.toPercentageString()})",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = WeMadeColors.OnSurface
                    )
                    Text(
                        text = "Kuantitas Pesanan: ${factor.orderedQuantity} pcs",
                        fontSize = 12.sp,
                        color = WeMadeColors.OnSurfaceMuted
                    )
                }
            }

            ClayBadge(
                text = "${factor.scale.toPercentageString()}",
                tint = if (factor.scale.numerator >= factor.scale.denominator) WeMadeColors.Primary else WeMadeColors.Teal
            )
        }
    }
}

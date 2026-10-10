package com.eventverse.app.presentation.techpack.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.domain.pipeline.StockOwnershipSemantics
import com.eventverse.app.domain.techpack.BomCostPreview
import com.eventverse.app.domain.techpack.BomLineCost
import com.eventverse.app.domain.techpack.TechPack
import com.eventverse.app.presentation.designsystem.*
import com.eventverse.app.presentation.techpack.TechPackUiEvent
import com.eventverse.app.presentation.techpack.format
import com.eventverse.app.presentation.theme.WeMadeColors

@Composable
fun MaterialCostPreviewCard(
    techPack: TechPack,
    costPreview: BomCostPreview?,
    orderQuantity: Long,
    isLoading: Boolean,
    onEvent: (TechPackUiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
    ) {
        // Disclaimer Banner
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .claySurface(
                    shape = ClayShapes.Chip,
                    background = WeMadeColors.PrimaryContainer,
                    outline = WeMadeColors.Info,
                    offset = ClayOffset.Small,
                    borderWidth = ClayBorder.Hairline
                )
                .padding(ClaySpacing.Md)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Estimasi Biaya Bahan Baku Murni (Bukan HPP Akhir)",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = WeMadeColors.Info
                )
                Text(
                    text = "Kalkulasi ini hanya menghitung konsumsi bahan baku dan komponen fisik dari Master Data. Biaya overhead pabrik, tarif tenaga kerja (SAM x tarif divisi), dan margin laba dihitung secara terpusat di modul Costing HPP.",
                    fontSize = 11.sp,
                    color = WeMadeColors.OnSurface
                )
            }
        }

        // Order Quantity selector
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
                    text = "Simulasi Kuantitas Produksi:",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = WeMadeColors.OnSurface
                )

                val presetQtys = listOf(50L, 100L, 300L, 500L, 1000L)
                presetQtys.forEach { qty ->
                    val isSelected = orderQuantity == qty
                    ClayButton(
                        text = "$qty pcs",
                        onClick = { onEvent(TechPackUiEvent.UpdateOrderQuantity(qty)) },
                        style = if (isSelected) ClayButtonStyle.Primary else ClayButtonStyle.Secondary
                    )
                }
            }

            ClayButton(
                text = "Hitung Ulang",
                onClick = { onEvent(TechPackUiEvent.LoadCostPreview) },
                style = ClayButtonStyle.Secondary
            )
        }

        if (isLoading) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Menghitung estimasi biaya bahan...",
                    fontSize = 14.sp,
                    color = WeMadeColors.OnSurfaceMuted
                )
            }
        } else if (costPreview == null) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Klik 'Hitung Ulang' untuk melihat rincian biaya bahan",
                    fontSize = 13.sp,
                    color = WeMadeColors.OnSurfaceMuted
                )
            }
        } else {
            // Summary Cards Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
            ) {
                // Total Material Cost Card
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .claySurface(
                            shape = ClayShapes.Card,
                            background = WeMadeColors.PrimaryContainer,
                            outline = WeMadeColors.Primary,
                            offset = ClayOffset.Rest,
                            borderWidth = ClayBorder.Medium
                        )
                        .padding(ClaySpacing.Md)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "TOTAL BIAYA BAHAN (${costPreview.orderQuantity} PCS)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = WeMadeColors.Primary
                        )
                        Text(
                            text = costPreview.materialCostTotal.format(),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = WeMadeColors.OnSurface
                        )
                        Text(
                            text = "Aset finansial milik pabrik",
                            fontSize = 11.sp,
                            color = WeMadeColors.OnSurfaceMuted
                        )
                    }
                }

                // Cost per garment Card
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .claySurface(
                            shape = ClayShapes.Card,
                            background = WeMadeColors.TealBg,
                            outline = WeMadeColors.Teal,
                            offset = ClayOffset.Rest,
                            borderWidth = ClayBorder.Medium
                        )
                        .padding(ClaySpacing.Md)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "BIAYA BAHAN PER GARMENT",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = WeMadeColors.Teal
                        )
                        Text(
                            text = costPreview.materialCostPerGarment.format(),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = WeMadeColors.OnSurface
                        )
                        Text(
                            text = "Termasuk waste allowance",
                            fontSize = 11.sp,
                            color = WeMadeColors.OnSurfaceMuted
                        )
                    }
                }

                // Consigned Client Material Disclosure Card
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .claySurface(
                            shape = ClayShapes.Card,
                            background = WeMadeColors.WarningBg,
                            outline = WeMadeColors.Warning,
                            offset = ClayOffset.Rest,
                            borderWidth = ClayBorder.Medium
                        )
                        .padding(ClaySpacing.Md)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "TITIPAN KLIEN / KONSINYASI",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = WeMadeColors.Warning
                        )
                        Text(
                            text = "Rp 0 Masuk HPP",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = WeMadeColors.OnSurface
                        )
                        Text(
                            text = "Nilai nosional: ${costPreview.consignedNotionalValue.format()}",
                            fontSize = 11.sp,
                            color = WeMadeColors.OnSurfaceMuted
                        )
                    }
                }
            }

            if (!costPreview.isComplete) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .claySurface(
                            shape = ClayShapes.Chip,
                            background = WeMadeColors.WarningBg,
                            outline = WeMadeColors.Warning,
                            offset = ClayOffset.Small,
                            borderWidth = ClayBorder.Hairline
                        )
                        .padding(ClaySpacing.Sm)
                ) {
                    Text(
                        text = "Terdapat bahan baku yang belum memiliki tarif standar aktif di Master Data. Total biaya di atas belum mencakup seluruh baris BOM.",
                        fontSize = 11.sp,
                        color = WeMadeColors.Warning,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Line items breakdown
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
            ) {
                items(costPreview.lines, key = { it.lineId }) { lineCost ->
                    BomLineCostCard(lineCost = lineCost)
                }
            }
        }
    }
}

@Composable
private fun BomLineCostCard(lineCost: BomLineCost) {
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
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = lineCost.material.displayLabel,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = WeMadeColors.OnSurface
                    )
                    ClayBadge(
                        text = lineCost.category.displayName,
                        tint = WeMadeColors.Secondary
                    )
                    OwnershipBadge(ownership = lineCost.ownership)
                }

                Text(
                    text = "Total Kebutuhan: ${lineCost.grossQuantityTotal.format()} (${lineCost.grossQuantityPerGarment.format()} / pcs)",
                    fontSize = 11.sp,
                    color = WeMadeColors.OnSurfaceMuted
                )

                val resolved = lineCost.resolvedPrice
                if (resolved != null) {
                    Text(
                        text = "Tarif Acuan: ${resolved.unitPrice.amount.format()} / ${resolved.unitPrice.per.format()}",
                        fontSize = 11.sp,
                        color = WeMadeColors.Teal,
                        fontWeight = FontWeight.Medium
                    )
                } else if (lineCost.ownership == StockOwnershipSemantics.CONSIGNED_CLIENT_MATERIAL) {
                    Text(
                        text = "Bahan Titipan Klien: Rp 0 di neraca pabrik",
                        fontSize = 11.sp,
                        color = WeMadeColors.Warning,
                        fontWeight = FontWeight.Medium
                    )
                } else {
                    Text(
                        text = "Belum ada tarif acuan di Master Data",
                        fontSize = 11.sp,
                        color = WeMadeColors.Error,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = lineCost.costTotal.format(),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = WeMadeColors.OnSurface
                )
                Text(
                    text = "${lineCost.costPerGarment.format()} / pcs",
                    fontSize = 11.sp,
                    color = WeMadeColors.OnSurfaceMuted
                )
            }
        }
    }
}

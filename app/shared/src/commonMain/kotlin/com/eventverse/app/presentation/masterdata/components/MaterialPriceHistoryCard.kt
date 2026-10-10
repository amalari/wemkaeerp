package com.eventverse.app.presentation.masterdata.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.domain.masterdata.MaterialPrice
import com.eventverse.app.presentation.designsystem.*
import com.eventverse.app.presentation.theme.WeMadeColors
import kotlinx.datetime.Clock

@Composable
fun MaterialPriceHistoryCard(
    prices: List<MaterialPrice>,
    isLoading: Boolean,
    canManage: Boolean,
    onOpenSetPrice: () -> Unit,
    modifier: Modifier = Modifier
) {
    val now = Clock.System.now()
    val activePrice = prices
        .filter { it.effectiveFrom <= now }
        .maxByOrNull { it.effectiveFrom.toEpochMilliseconds() }

    ClayCard(
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(ClaySpacing.Md)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Riwayat Tarif Acuan HPP",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = WeMadeColors.OnSurface
                    )
                    Text(
                        text = "Tarif acuan point-in-time untuk BOM & kalkulasi HPP",
                        fontSize = 12.sp,
                        color = WeMadeColors.OnSurfaceMuted
                    )
                }

                if (canManage) {
                    ClayButton(
                        text = "+ Tarif Baru",
                        onClick = onOpenSetPrice,
                        style = ClayButtonStyle.Secondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(ClaySpacing.Md))

            if (isLoading) {
                Text(
                    text = "Memuat riwayat tarif...",
                    fontSize = 13.sp,
                    color = WeMadeColors.OnSurfaceMuted,
                    modifier = Modifier.padding(vertical = ClaySpacing.Sm)
                )
            } else if (prices.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clayFlat(
                            shape = ClayShapes.Card,
                            background = WeMadeColors.SurfaceMuted,
                            outline = WeMadeColors.Border,
                            borderWidth = ClayBorder.Medium
                        )
                        .padding(ClaySpacing.Md),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Belum ada tarif acuan untuk bahan ini. Klik '+ Tarif Baru' untuk menetapkan.",
                        fontSize = 13.sp,
                        color = WeMadeColors.OnSurfaceMuted
                    )
                }
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
                ) {
                    prices.reversed().forEach { price ->
                        val isActive = price == activePrice
                        val isFuture = price.effectiveFrom > now

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .claySurface(
                                    shape = ClayShapes.Card,
                                    background = if (isActive) WeMadeColors.SuccessBg else WeMadeColors.Surface,
                                    outline = if (isActive) WeMadeColors.Success else WeMadeColors.Outline,
                                    offset = ClayOffset.Small,
                                    borderWidth = if (isActive) ClayBorder.Thick else ClayBorder.Medium
                                )
                                .padding(ClaySpacing.Md)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xs),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${price.unitPrice.amount.formatted()} / ${price.unitPrice.per.formatted()}",
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = WeMadeColors.OnSurface
                                        )

                                        if (isActive) {
                                            ClayBadge(
                                                text = "AKTIF SAAT INI",
                                                tint = WeMadeColors.Success
                                            )
                                        } else if (isFuture) {
                                            ClayBadge(
                                                text = "BERLAKU DI MASA DEPAN",
                                                tint = WeMadeColors.Warning
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(2.dp))

                                    Text(
                                        text = "Berlaku sejak: ${price.effectiveFrom.toString().substringBefore('T')}",
                                        fontSize = 12.sp,
                                        color = WeMadeColors.OnSurfaceMuted
                                    )

                                    if (price.note.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "Catatan: ${price.note}",
                                            fontSize = 12.sp,
                                            color = WeMadeColors.OnSurface
                                        )
                                    }
                                }

                                ClayBadge(
                                    text = price.source.name,
                                    tint = WeMadeColors.OnSurfaceMuted
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

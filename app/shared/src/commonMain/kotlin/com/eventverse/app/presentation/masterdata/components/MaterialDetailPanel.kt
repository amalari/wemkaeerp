package com.eventverse.app.presentation.masterdata.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.domain.masterdata.MaterialItem
import com.eventverse.app.domain.masterdata.MaterialPrice
import com.eventverse.app.domain.pipeline.StockOwnershipSemantics
import com.eventverse.app.presentation.designsystem.*
import com.eventverse.app.presentation.theme.WeMadeColors

@Composable
fun MaterialDetailPanel(
    material: MaterialItem?,
    prices: List<MaterialPrice>,
    isPriceLoading: Boolean,
    canManage: Boolean,
    onOpenEdit: () -> Unit,
    onArchive: () -> Unit,
    onOpenSetPrice: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (material == null) {
        ClayCard(
            modifier = modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(ClaySpacing.Lg),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Pilih salah satu material dari daftar katalog di sebelah kiri.",
                    fontSize = 14.sp,
                    color = WeMadeColors.OnSurfaceMuted
                )
            }
        }
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
    ) {
        // Material Header Card
        ClayCard(
            modifier = Modifier.fillMaxWidth()
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
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ClayBadge(
                            text = material.code.value,
                            tint = WeMadeColors.Primary
                        )
                        ClayBadge(
                            text = material.category.displayName,
                            tint = WeMadeColors.OnSurfaceMuted
                        )
                        if (material.defaultOwnership == StockOwnershipSemantics.CONSIGNED_CLIENT_MATERIAL) {
                            ClayBadge(
                                text = "Konsinyasi Klien",
                                tint = WeMadeColors.Warning
                            )
                        } else {
                            ClayBadge(
                                text = "Milik Pabrik",
                                tint = WeMadeColors.Success
                            )
                        }
                    }

                    if (canManage) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
                        ) {
                            ClayButton(
                                text = "Ubah",
                                onClick = onOpenEdit,
                                style = ClayButtonStyle.Secondary
                            )
                            ClayButton(
                                text = "Arsipkan",
                                onClick = onArchive,
                                style = ClayButtonStyle.Danger
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(ClaySpacing.Sm))

                Text(
                    text = material.name,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = WeMadeColors.OnSurface
                )

                if (material.description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(ClaySpacing.Xs))
                    Text(
                        text = material.description,
                        fontSize = 13.sp,
                        color = WeMadeColors.OnSurfaceMuted
                    )
                }
            }
        }

        // Specifications & Packaging Units Card
        ClayCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(ClaySpacing.Md)
            ) {
                Text(
                    text = "Spesifikasi Satuan & Konversi Kemasan",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = WeMadeColors.OnSurface
                )
                Text(
                    text = "Satuan dasar fisik dan faktor konversi kemasan (cone/roll/box)",
                    fontSize = 12.sp,
                    color = WeMadeColors.OnSurfaceMuted
                )

                Spacer(modifier = Modifier.height(ClaySpacing.Md))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Satuan Dasar Acuan (Base UoM):",
                        fontSize = 13.sp,
                        color = WeMadeColors.OnSurface
                    )
                    ClayBadge(
                        text = "${material.baseUom.displayName} (${material.baseUom.code})",
                        tint = WeMadeColors.Primary
                    )
                }

                Spacer(modifier = Modifier.height(ClaySpacing.Sm))

                Text(
                    text = "Konversi Satuan Kemasan Alternatif:",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = WeMadeColors.OnSurface
                )
                Spacer(modifier = Modifier.height(ClaySpacing.Xs))

                if (material.alternateUoms.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clayFlat(
                                shape = ClayShapes.Card,
                                background = WeMadeColors.SurfaceMuted,
                                outline = WeMadeColors.Border,
                                borderWidth = ClayBorder.Medium
                            )
                            .padding(ClaySpacing.Sm),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Belum ada konversi kemasan (seperti cone atau roll). Digunakan satuan dasar langsung.",
                            fontSize = 12.sp,
                            color = WeMadeColors.OnSurfaceMuted
                        )
                    }
                } else {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)
                    ) {
                        material.alternateUoms.forEach { conv ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .claySurface(
                                        shape = ClayShapes.Card,
                                        background = WeMadeColors.Surface,
                                        outline = WeMadeColors.Outline,
                                        offset = ClayOffset.Small,
                                        borderWidth = ClayBorder.Medium
                                    )
                                    .padding(horizontal = ClaySpacing.Md, vertical = ClaySpacing.Sm)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "1 ${conv.from.displayName} (${conv.from.code})",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = WeMadeColors.OnSurface
                                    )
                                    Text(
                                        text = "= ${conv.equivalent.formatted()}",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = WeMadeColors.Primary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Price History Card
        MaterialPriceHistoryCard(
            prices = prices,
            isLoading = isPriceLoading,
            canManage = canManage,
            onOpenSetPrice = onOpenSetPrice
        )
    }
}

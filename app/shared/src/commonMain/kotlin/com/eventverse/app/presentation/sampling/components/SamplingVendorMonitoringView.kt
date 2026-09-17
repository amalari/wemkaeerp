package com.eventverse.app.presentation.sampling.components

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
import com.eventverse.app.domain.sampling.FinishingPath
import com.eventverse.app.domain.sampling.SamplingOrder
import com.eventverse.app.domain.sampling.VendorFollowUpStatus
import com.eventverse.app.presentation.designsystem.*
import com.eventverse.app.presentation.theme.WeMadeColors

@Composable
fun SamplingVendorMonitoringView(
    orders: List<SamplingOrder>,
    onOpenVendorDialog: (SamplingOrder) -> Unit,
    onConfirmReceive: (SamplingOrder) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedFilter by remember { mutableStateOf<VendorFollowUpStatus?>(null) }

    val makloonOrders = remember(orders, selectedFilter) {
        orders.filter { order ->
            order.finishingPath == FinishingPath.MAKLOON_VENDOR &&
                (selectedFilter == null || order.vendorInfo.status == selectedFilter)
        }
    }

    val totalMakloon = orders.count { it.finishingPath == FinishingPath.MAKLOON_VENDOR }
    val withVendorCount = orders.count { it.finishingPath == FinishingPath.MAKLOON_VENDOR && it.vendorInfo.status == VendorFollowUpStatus.WITH_VENDOR }
    val overdueCount = orders.count { it.finishingPath == FinishingPath.MAKLOON_VENDOR && it.vendorInfo.status == VendorFollowUpStatus.OVERDUE }
    val returnedCount = orders.count { it.finishingPath == FinishingPath.MAKLOON_VENDOR && it.vendorInfo.status == VendorFollowUpStatus.RETURNED }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(ClaySpacing.Md),
        verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
    ) {
        // Stats Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
        ) {
            ClayCard(
                modifier = Modifier.weight(1f),
                shape = ClayShapes.Card,
                contentPadding = PaddingValues(ClaySpacing.Md)
            ) {
                Text(text = "TOTAL MAKLOON", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = WeMadeColors.OnSurfaceMuted)
                Text(text = "$totalMakloon SPK", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = WeMadeColors.Primary)
            }

            ClayCard(
                modifier = Modifier.weight(1f),
                shape = ClayShapes.Card,
                contentPadding = PaddingValues(ClaySpacing.Md)
            ) {
                Text(text = "DI VENDOR", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = WeMadeColors.OnSurfaceMuted)
                Text(text = "$withVendorCount SPK", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = WeMadeColors.Primary)
            }

            ClayCard(
                modifier = Modifier.weight(1f),
                shape = ClayShapes.Card,
                contentPadding = PaddingValues(ClaySpacing.Md)
            ) {
                Text(text = "TERLAMBAT (OVERDUE)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = WeMadeColors.Error)
                Text(text = "$overdueCount SPK", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = WeMadeColors.Error)
            }

            ClayCard(
                modifier = Modifier.weight(1f),
                shape = ClayShapes.Card,
                contentPadding = PaddingValues(ClaySpacing.Md)
            ) {
                Text(text = "SUDAH KEMBALI", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = WeMadeColors.Success)
                Text(text = "$returnedCount SPK", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = WeMadeColors.Success)
            }
        }

        // Filter Pills
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "Filter Status:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = WeMadeColors.OnSurfaceMuted)

            ClayButton(
                text = "Semua",
                style = if (selectedFilter == null) ClayButtonStyle.Primary else ClayButtonStyle.Secondary,
                fontSize = 11.sp,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                onClick = { selectedFilter = null }
            )

            VendorFollowUpStatus.entries.filter { it != VendorFollowUpStatus.NONE }.forEach { status ->
                val isSelected = selectedFilter == status
                ClayButton(
                    text = status.displayName,
                    style = if (isSelected) ClayButtonStyle.Primary else ClayButtonStyle.Secondary,
                    fontSize = 11.sp,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    onClick = { selectedFilter = status }
                )
            }
        }

        // Order list
        if (makloonOrders.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center
            ) {
                ClayCard(
                    modifier = Modifier.widthIn(max = 400.dp).padding(ClaySpacing.Lg),
                    shape = ClayShapes.Card,
                    contentPadding = PaddingValues(ClaySpacing.Lg)
                ) {
                    Text(
                        text = "Tidak Ada SPK Makloon",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = WeMadeColors.OnSurface
                    )
                    Spacer(modifier = Modifier.height(ClaySpacing.Xs))
                    Text(
                        text = "Semua SPK sampling saat ini diproses oleh tim finishing internal pabrik atau belum ada yang dialihkan ke vendor.",
                        fontSize = 12.sp,
                        color = WeMadeColors.OnSurfaceMuted
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
            ) {
                items(makloonOrders, key = { it.id.value }) { order ->
                    ClayCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = ClayShapes.Card,
                        contentPadding = PaddingValues(ClaySpacing.Md)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "${order.spkNumber.value} • ${order.clientName}",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = WeMadeColors.Primary
                                    )
                                    Text(
                                        text = "${order.styleName} (${order.sampleQuantity} Pcs)",
                                        fontSize = 12.sp,
                                        color = WeMadeColors.OnSurfaceMuted
                                    )
                                }

                                ClayBadge(
                                    text = order.pipelineStage.displayName,
                                    tint = WeMadeColors.Primary
                                )
                            }

                            VendorMakloonCard(
                                order = order,
                                onOpenVendorDialog = { onOpenVendorDialog(order) },
                                onConfirmReceive = { onConfirmReceive(order) }
                            )
                        }
                    }
                }
            }
        }
    }
}

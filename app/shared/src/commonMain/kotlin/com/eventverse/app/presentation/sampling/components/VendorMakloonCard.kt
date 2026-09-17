package com.eventverse.app.presentation.sampling.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.domain.sampling.FinishingPath
import com.eventverse.app.domain.sampling.SamplingOrder
import com.eventverse.app.domain.sampling.VendorFollowUpStatus
import com.eventverse.app.presentation.designsystem.*
import com.eventverse.app.presentation.theme.WeMadeColors

@Composable
fun VendorMakloonCard(
    order: SamplingOrder,
    onOpenVendorDialog: () -> Unit,
    onConfirmReceive: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uriHandler = LocalUriHandler.current
    val vendor = order.vendorInfo
    val isWithVendor = vendor.status == VendorFollowUpStatus.WITH_VENDOR || vendor.status == VendorFollowUpStatus.OVERDUE

    ClayCard(
        modifier = modifier.fillMaxWidth(),
        shape = ClayShapes.Card,
        contentPadding = PaddingValues(ClaySpacing.Md)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconTruck(modifier = Modifier.size(18.dp), color = WeMadeColors.Primary)
                    Text(
                        text = "JALUR PERAKITAN / FINISHING",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = WeMadeColors.OnSurface
                    )
                }

                if (order.finishingPath == FinishingPath.MAKLOON_VENDOR) {
                    val badgeTint = when (vendor.status) {
                        VendorFollowUpStatus.WITH_VENDOR -> WeMadeColors.Primary
                        VendorFollowUpStatus.OVERDUE -> WeMadeColors.Error
                        VendorFollowUpStatus.RETURNED -> WeMadeColors.Success
                        VendorFollowUpStatus.NONE -> WeMadeColors.OnSurfaceMuted
                    }
                    ClayBadge(
                        text = "Vendor: ${vendor.status.displayName}",
                        tint = badgeTint
                    )
                } else {
                    ClayBadge(
                        text = "Internal Pabrik",
                        tint = WeMadeColors.Success
                    )
                }
            }

            if (order.finishingPath == FinishingPath.MAKLOON_VENDOR) {
                // Vendor detail box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(WeMadeColors.SurfaceMuted, ClayShapes.Tile)
                        .padding(ClaySpacing.Sm)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Nama Vendor:",
                                fontSize = 11.sp,
                                color = WeMadeColors.OnSurfaceMuted
                            )
                            Text(
                                text = vendor.vendorName.ifBlank { "Belum ditentukan" },
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = WeMadeColors.OnSurface
                            )
                        }

                        if (vendor.vendorPhone.isNotBlank()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Telepon / WA:",
                                    fontSize = 11.sp,
                                    color = WeMadeColors.OnSurfaceMuted
                                )
                                Text(
                                    text = vendor.vendorPhone,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = WeMadeColors.OnSurface
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Tgl Kirim -> Target Kembali:",
                                fontSize = 11.sp,
                                color = WeMadeColors.OnSurfaceMuted
                            )
                            Text(
                                text = "${vendor.sentAt ?: "-"} -> ${vendor.expectedReturnAt ?: "-"}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (vendor.status == VendorFollowUpStatus.OVERDUE) WeMadeColors.Error else WeMadeColors.OnSurface
                            )
                        }

                        if (vendor.costPerPcsIdr > 0) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Biaya Jasa / Pcs:",
                                    fontSize = 11.sp,
                                    color = WeMadeColors.OnSurfaceMuted
                                )
                                Text(
                                    text = "Rp ${vendor.costPerPcsIdr}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = WeMadeColors.Primary
                                )
                            }
                        }
                    }
                }

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
                ) {
                    if (vendor.vendorPhone.isNotBlank() && isWithVendor) {
                        ClayButton(
                            text = "Chat WhatsApp",
                            style = ClayButtonStyle.Success,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                val cleanPhone = vendor.vendorPhone.replace(Regex("[^0-9]"), "").let {
                                    if (it.startsWith("0")) "62" + it.substring(1) else it
                                }
                                val targetDateStr = vendor.expectedReturnAt?.toString() ?: "segera"
                                val text = "Halo ${vendor.vendorName}, mau konfirmasi progres SPK ${order.spkNumber.value} (${order.styleName}) apakah sudah selesai linking/finishing nya? Target kembali tgl $targetDateStr. Terima kasih."
                                val url = "https://wa.me/$cleanPhone?text=${text.replace(" ", "%20")}"
                                uriHandler.openUri(url)
                            }
                        )
                    }

                    if (isWithVendor) {
                        ClayButton(
                            text = "Konfirmasi Terima",
                            style = ClayButtonStyle.Primary,
                            modifier = Modifier.weight(1f),
                            onClick = onConfirmReceive
                        )
                    }

                    ClayButton(
                        text = if (vendor.vendorName.isBlank()) "Tugaskan Vendor" else "Ubah Vendor",
                        style = ClayButtonStyle.Secondary,
                        modifier = if (isWithVendor) Modifier else Modifier.fillMaxWidth(),
                        onClick = onOpenVendorDialog
                    )
                }
            } else {
                Text(
                    text = "Dikerjakan oleh Divisi Finishing internal pabrik melalui antrean Catatan Kerja Operator.",
                    fontSize = 12.sp,
                    color = WeMadeColors.OnSurfaceMuted
                )

                ClayButton(
                    text = "Alihkan ke Vendor Makloon Luar",
                    style = ClayButtonStyle.Secondary,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onOpenVendorDialog
                )
            }
        }
    }
}

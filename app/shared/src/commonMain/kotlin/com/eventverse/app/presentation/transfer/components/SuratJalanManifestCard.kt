package com.eventverse.app.presentation.transfer.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.eventverse.app.domain.transfer.SuratJalanManifest
import com.eventverse.app.domain.transfer.TransferStatus
import com.eventverse.app.domain.transfer.TransferType
import com.eventverse.app.presentation.designsystem.ClayBadge
import com.eventverse.app.presentation.designsystem.ClayButton
import com.eventverse.app.presentation.designsystem.ClayButtonStyle
import com.eventverse.app.presentation.designsystem.ClayCard
import com.eventverse.app.presentation.designsystem.ClayShapes
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.IconCheck
import com.eventverse.app.presentation.designsystem.IconPackage
import com.eventverse.app.presentation.designsystem.IconTruck
import com.eventverse.app.presentation.theme.WeMadeColors

@Composable
fun SuratJalanManifestCard(
    manifest: SuratJalanManifest,
    onReceive: (manifestId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    ClayCard(
        shape = ClayShapes.Card,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(ClaySpacing.Lg)) {
            // Header: Nomor SJ, Tipe Mutasi, & Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(WeMadeColors.Primary.copy(alpha = 0.15f), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        when (manifest.transferType) {
                            TransferType.INTERNAL_SITE_TRANSFER -> IconTruck(modifier = Modifier.size(20.dp), color = WeMadeColors.Primary)
                            TransferType.SUBCONTRACT_OUTBOUND, TransferType.SUBCONTRACT_INBOUND -> IconPackage(modifier = Modifier.size(20.dp), color = WeMadeColors.Accent)
                            TransferType.CUSTOMER_DISPATCH -> IconPackage(modifier = Modifier.size(20.dp), color = WeMadeColors.Success)
                        }
                    }
                    Spacer(modifier = Modifier.width(ClaySpacing.Sm))
                    Column {
                        Text(
                            text = manifest.sjNumber.value,
                            style = MaterialTheme.typography.titleMedium,
                            color = WeMadeColors.OnSurface
                        )
                        Text(
                            text = "${manifest.subject.orderNumber} · ${manifest.subject.articleName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = WeMadeColors.OnSurfaceMuted
                        )
                    }
                }

                val (badgeTint, statusLabel) = when (manifest.status) {
                    TransferStatus.DRAFT -> WeMadeColors.OnSurfaceMuted to "Draft"
                    TransferStatus.DISPATCHED -> WeMadeColors.Info to "Dikirim"
                    TransferStatus.IN_TRANSIT -> WeMadeColors.Warning to "Dalam Perjalanan"
                    TransferStatus.RECEIVED -> WeMadeColors.Success to "Diterima Lengkap"
                    TransferStatus.PARTIAL_RECEIVED -> WeMadeColors.Warning to "Diterima Sebagian"
                    TransferStatus.CANCELLED -> WeMadeColors.Error to "Batal"
                }
                ClayBadge(text = statusLabel, tint = badgeTint)
            }

            Spacer(modifier = Modifier.height(ClaySpacing.Md))

            // Body Detail: Asal/Tujuan atau Vendor atau Buyer
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(WeMadeColors.SurfaceMuted, RoundedCornerShape(8.dp))
                    .padding(ClaySpacing.Sm)
            ) {
                Column {
                    when (manifest.transferType) {
                        TransferType.INTERNAL_SITE_TRANSFER -> {
                            Text(
                                text = "Rute: ${manifest.originLocationId?.value ?: "-"} -> ${manifest.destinationLocationId?.value ?: "-"}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = WeMadeColors.OnSurface
                            )
                            Text(
                                text = "Pelacakan: ${manifest.items.size} bundle individual (tiket diteruskan utuh)",
                                style = MaterialTheme.typography.bodySmall,
                                color = WeMadeColors.Primary
                            )
                        }
                        TransferType.SUBCONTRACT_OUTBOUND -> {
                            Text(
                                text = "Vendor Rekanan: ${manifest.vendorRef ?: "-"}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = WeMadeColors.OnSurface
                            )
                            Text(
                                text = "Target Kembali: ${manifest.expectedReturnDate ?: "-"} · Tarif Jasa: Rp ${manifest.unitServiceFeeIdr}/pcs",
                                style = MaterialTheme.typography.bodySmall,
                                color = WeMadeColors.Accent
                            )
                        }
                        TransferType.CUSTOMER_DISPATCH -> {
                            Text(
                                text = "Penerima: ${manifest.customerName ?: "-"} (${manifest.customerAddress ?: "-"})",
                                style = MaterialTheme.typography.bodyMedium,
                                color = WeMadeColors.OnSurface
                            )
                            Text(
                                text = "Kemasan: ${manifest.totalCartons} dus/karung · Total: ${manifest.totalPcs} pcs",
                                style = MaterialTheme.typography.bodySmall,
                                color = WeMadeColors.Success
                            )
                        }
                        TransferType.SUBCONTRACT_INBOUND -> {
                            Text(
                                text = "Vendor Rekanan: ${manifest.vendorRef ?: "-"}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = WeMadeColors.OnSurface
                            )
                        }
                    }

                    if (!manifest.driverName.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Driver: ${manifest.driverName} (${manifest.vehiclePlate ?: "-"})",
                            style = MaterialTheme.typography.bodySmall,
                            color = WeMadeColors.OnSurfaceMuted
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(ClaySpacing.Md))

            // Footer: Ringkasan Kuantitas & Tombol Serah Terima
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Total Muatan: ${manifest.totalPcs} pcs",
                    style = MaterialTheme.typography.titleSmall,
                    color = WeMadeColors.OnSurface
                )

                if (manifest.isDispatched) {
                    ClayButton(
                        text = "Konfirmasi Terima",
                        onClick = { onReceive(manifest.id.value) },
                        style = ClayButtonStyle.Primary,
                        leading = { IconCheck(modifier = Modifier.size(16.dp), color = WeMadeColors.Surface) }
                    )
                }
            }
        }
    }
}

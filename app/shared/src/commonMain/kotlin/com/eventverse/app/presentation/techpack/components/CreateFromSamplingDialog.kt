package com.eventverse.app.presentation.techpack.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.eventverse.app.domain.pipeline.StockOwnershipSemantics
import com.eventverse.app.presentation.designsystem.*
import com.eventverse.app.presentation.theme.WeMadeColors

@Composable
fun CreateFromSamplingDialog(
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onSave: (samplingOrderId: String, styleCode: String?, defaultOwnership: StockOwnershipSemantics?, defaultWastePercent: Double?) -> Unit
) {
    var samplingOrderId by remember { mutableStateOf("") }
    var styleCode by remember { mutableStateOf("") }
    var wastePercentText by remember { mutableStateOf("5.0") }
    var ownership by remember { mutableStateOf(StockOwnershipSemantics.OWNED_RAW_MATERIAL) }

    Dialog(onDismissRequest = onDismiss) {
        ClayCard(
            modifier = Modifier.fillMaxWidth().padding(ClaySpacing.Md),
            shape = ClayShapes.Card,
            contentPadding = PaddingValues(ClaySpacing.Lg)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
            ) {
                Text(
                    text = "Buat Tech Pack dari SPK Sampling",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = WeMadeColors.OnSurface
                )

                Text(
                    text = "Tech pack akan otomatis mengimpor yarn specs, berat panel gramasi, waktu rajut mesin, dan proses finishing dari SPK sample yang sudah ACC Approved.",
                    fontSize = 12.sp,
                    color = WeMadeColors.OnSurfaceMuted
                )

                Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)) {
                    Text(
                        text = "ID Sampling Order (Wajib ACC Approved):",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = WeMadeColors.OnSurface
                    )
                    ClayTextField(
                        value = samplingOrderId,
                        onValueChange = { samplingOrderId = it },
                        placeholder = "Contoh: smp-001 atau UUID sampling order",
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)) {
                    Text(
                        text = "Kode Style Kustom (Opsional):",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = WeMadeColors.OnSurface
                    )
                    ClayTextField(
                        value = styleCode,
                        onValueChange = { styleCode = it },
                        placeholder = "Kosongkan untuk memakai SPK Number",
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)) {
                    Text(
                        text = "Default Waste Allowance (%):",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = WeMadeColors.OnSurface
                    )
                    ClayTextField(
                        value = wastePercentText,
                        onValueChange = { wastePercentText = it },
                        placeholder = "5.0",
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)) {
                    Text(
                        text = "Default Status Kepemilikan Bahan:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = WeMadeColors.OnSurface
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
                    ) {
                        val owned = StockOwnershipSemantics.OWNED_RAW_MATERIAL
                        val isOwned = ownership == owned
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { ownership = owned }
                                .claySurface(
                                    shape = ClayShapes.Pill,
                                    background = if (isOwned) WeMadeColors.Teal else WeMadeColors.SurfaceMuted,
                                    outline = if (isOwned) WeMadeColors.Outline else WeMadeColors.OutlineSoft,
                                    offset = if (isOwned) ClayOffset.Small else ClayOffset.Flat,
                                    borderWidth = ClayBorder.Hairline
                                )
                                .padding(vertical = ClaySpacing.Sm),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Aset Pabrik",
                                fontSize = 11.sp,
                                fontWeight = if (isOwned) FontWeight.Bold else FontWeight.Normal,
                                color = if (isOwned) androidx.compose.ui.graphics.Color.White else WeMadeColors.OnSurface
                            )
                        }

                        val consigned = StockOwnershipSemantics.CONSIGNED_CLIENT_MATERIAL
                        val isConsigned = ownership == consigned
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { ownership = consigned }
                                .claySurface(
                                    shape = ClayShapes.Pill,
                                    background = if (isConsigned) WeMadeColors.Warning else WeMadeColors.SurfaceMuted,
                                    outline = if (isConsigned) WeMadeColors.Outline else WeMadeColors.OutlineSoft,
                                    offset = if (isConsigned) ClayOffset.Small else ClayOffset.Flat,
                                    borderWidth = ClayBorder.Hairline
                                )
                                .padding(vertical = ClaySpacing.Sm),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Titipan Klien (Rp 0)",
                                fontSize = 11.sp,
                                fontWeight = if (isConsigned) FontWeight.Bold else FontWeight.Normal,
                                color = if (isConsigned) androidx.compose.ui.graphics.Color.White else WeMadeColors.OnSurface
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ClayButton(
                        text = "Batal",
                        onClick = onDismiss,
                        style = ClayButtonStyle.Secondary
                    )
                    Spacer(modifier = Modifier.width(ClaySpacing.Sm))
                    ClayButton(
                        text = if (isSubmitting) "Mengimpor..." else "Impor dari Sampling",
                        onClick = {
                            val wasteDbl = wastePercentText.toDoubleOrNull() ?: 5.0
                            onSave(
                                samplingOrderId.trim(),
                                styleCode.trim().takeIf { it.isNotBlank() },
                                ownership,
                                wasteDbl
                            )
                        },
                        style = ClayButtonStyle.Primary,
                        enabled = samplingOrderId.isNotBlank() && !isSubmitting
                    )
                }
            }
        }
    }
}

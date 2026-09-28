package com.eventverse.app.presentation.sampling.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.eventverse.app.domain.sampling.FinishingDeposit
import com.eventverse.app.domain.sampling.SamplingOrder
import com.eventverse.app.presentation.designsystem.*
import com.eventverse.app.presentation.operator.SpkDetailPanel
import com.eventverse.app.presentation.theme.WeMadeColors
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@Composable
fun FinishingSetoranDialog(
    isOpen: Boolean,
    order: SamplingOrder?,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (FinishingDeposit) -> Unit
) {
    if (!isOpen || order == null) return

    var qtyText by remember(order.id) { mutableStateOf(order.remainingFinishingQty.coerceAtLeast(1).toString()) }
    var weightText by remember(order.id) { mutableStateOf("") }
    var operatorName by remember(order.id) { mutableStateOf("") }
    var notes by remember(order.id) { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        ClayCard(
            modifier = Modifier.fillMaxWidth().padding(ClaySpacing.Md),
            shape = ClayShapes.Card,
            contentPadding = PaddingValues(ClaySpacing.Lg)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "SETORAN FINISHING & LINKING",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = WeMadeColors.OnSurface
                        )
                        Text(
                            text = "${order.spkNumber.value} • ${order.clientName}",
                            fontSize = 12.sp,
                            color = WeMadeColors.OnSurfaceMuted
                        )
                    }
                    ClayIconButton(onClick = onDismiss, shape = ClayShapes.Tile) {
                        IconClose(modifier = Modifier.size(16.dp))
                    }
                }

                // Detail SPK dari tim sampling — operator tahu target & instruksi sebelum setor.
                SpkDetailPanel(order = order, stage = order.stageCode)

                // Progress Info Box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(WeMadeColors.SurfaceMuted, ClayShapes.Tile)
                        .padding(ClaySpacing.Sm)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = "Target Total", fontSize = 10.sp, color = WeMadeColors.OnSurfaceMuted)
                            Text(text = "${order.sampleQuantity} Pcs", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = "Sudah Setor", fontSize = 10.sp, color = WeMadeColors.OnSurfaceMuted)
                            Text(text = "${order.totalFinishedDepositedQty} Pcs", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = WeMadeColors.Success)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = "Sisa Belum", fontSize = 10.sp, color = WeMadeColors.OnSurfaceMuted)
                            Text(text = "${order.remainingFinishingQty} Pcs", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = WeMadeColors.Primary)
                        }
                    }
                }

                // Form Fields
                OutlinedTextField(
                    value = qtyText,
                    onValueChange = { qtyText = it },
                    label = { Text("Jumlah Setor Selesai (Pcs)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = ClayShapes.Tile,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = WeMadeColors.Primary,
                        unfocusedBorderColor = WeMadeColors.Border
                    )
                )

                OutlinedTextField(
                    value = weightText,
                    onValueChange = { weightText = it },
                    label = { Text("Berat Timbangan Hasil Jadi (Kg)") },
                    placeholder = { Text("Contoh: 1.25") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = ClayShapes.Tile,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = WeMadeColors.Primary,
                        unfocusedBorderColor = WeMadeColors.Border
                    )
                )

                OutlinedTextField(
                    value = operatorName,
                    onValueChange = { operatorName = it },
                    label = { Text("Nama Operator Finishing") },
                    placeholder = { Text("Contoh: Kang Cecep / Teh Rina") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = ClayShapes.Tile,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = WeMadeColors.Primary,
                        unfocusedBorderColor = WeMadeColors.Border
                    )
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Catatan / Keterangan (Opsional)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = ClayShapes.Tile,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = WeMadeColors.Primary,
                        unfocusedBorderColor = WeMadeColors.Border
                    )
                )

                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    ClayButton(
                        text = "Batal",
                        style = ClayButtonStyle.Secondary,
                        onClick = onDismiss
                    )
                    Spacer(modifier = Modifier.width(ClaySpacing.Sm))
                    ClayButton(
                        text = if (isSubmitting) "Menyimpan..." else "Simpan Setoran",
                        style = ClayButtonStyle.Primary,
                        enabled = !isSubmitting && (qtyText.toIntOrNull() ?: 0) > 0,
                        onClick = {
                            val pcs = qtyText.toIntOrNull() ?: 1
                            val kg = weightText.toDoubleOrNull() ?: 0.0
                            val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
                            val dep = FinishingDeposit(
                                id = "",
                                samplingOrderId = order.id.value,
                                depositDate = today,
                                qtyPcs = pcs,
                                weightKg = kg,
                                operatorName = operatorName,
                                notes = notes
                            )
                            onSubmit(dep)
                        }
                    )
                }
            }
        }
    }
}

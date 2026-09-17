package com.eventverse.app.presentation.sampling.components

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
import com.eventverse.app.domain.sampling.MakloonVendorInfo
import com.eventverse.app.domain.sampling.SamplingOrder
import com.eventverse.app.domain.sampling.VendorFollowUpStatus
import com.eventverse.app.presentation.designsystem.*
import com.eventverse.app.presentation.theme.WeMadeColors
import kotlinx.datetime.*

@Composable
fun AssignVendorDialog(
    isOpen: Boolean,
    order: SamplingOrder?,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (MakloonVendorInfo) -> Unit
) {
    if (!isOpen || order == null) return

    val currentVendor = order.vendorInfo
    val today = remember { Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date }
    val defaultTarget = remember { today.plus(3, DateTimeUnit.DAY) }

    var vendorName by remember(order.id) { mutableStateOf(currentVendor.vendorName) }
    var vendorPhone by remember(order.id) { mutableStateOf(currentVendor.vendorPhone) }
    var sentAtStr by remember(order.id) { mutableStateOf(currentVendor.sentAt?.toString() ?: today.toString()) }
    var targetAtStr by remember(order.id) { mutableStateOf(currentVendor.expectedReturnAt?.toString() ?: defaultTarget.toString()) }
    var costStr by remember(order.id) { mutableStateOf(if (currentVendor.costPerPcsIdr > 0) currentVendor.costPerPcsIdr.toString() else "") }
    var notes by remember(order.id) { mutableStateOf(currentVendor.notes) }

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
                            text = "TUGASKAN VENDOR MAKLOON",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = WeMadeColors.OnSurface
                        )
                        Text(
                            text = "${order.spkNumber.value} • ${order.styleName} (${order.sampleQuantity} Pcs)",
                            fontSize = 12.sp,
                            color = WeMadeColors.OnSurfaceMuted
                        )
                    }
                    ClayIconButton(onClick = onDismiss, shape = ClayShapes.Tile) {
                        IconClose(modifier = Modifier.size(16.dp))
                    }
                }

                // Form Fields
                OutlinedTextField(
                    value = vendorName,
                    onValueChange = { vendorName = it },
                    label = { Text("Nama Vendor / Mitra Makloon") },
                    placeholder = { Text("Contoh: Konveksi Pak Ade / Teh Nani") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = ClayShapes.Tile,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = WeMadeColors.Primary,
                        unfocusedBorderColor = WeMadeColors.Border
                    )
                )

                OutlinedTextField(
                    value = vendorPhone,
                    onValueChange = { vendorPhone = it },
                    label = { Text("No. WhatsApp Vendor") },
                    placeholder = { Text("Contoh: 08123456789") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = ClayShapes.Tile,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = WeMadeColors.Primary,
                        unfocusedBorderColor = WeMadeColors.Border
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
                ) {
                    OutlinedTextField(
                        value = sentAtStr,
                        onValueChange = { sentAtStr = it },
                        label = { Text("Tgl Kirim (YYYY-MM-DD)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = ClayShapes.Tile,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = WeMadeColors.Primary,
                            unfocusedBorderColor = WeMadeColors.Border
                        )
                    )

                    OutlinedTextField(
                        value = targetAtStr,
                        onValueChange = { targetAtStr = it },
                        label = { Text("Target Kembali") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = ClayShapes.Tile,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = WeMadeColors.Primary,
                            unfocusedBorderColor = WeMadeColors.Border
                        )
                    )
                }

                OutlinedTextField(
                    value = costStr,
                    onValueChange = { costStr = it },
                    label = { Text("Estimasi Biaya Jasa / Pcs (Rp)") },
                    placeholder = { Text("Contoh: 3500") },
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
                    label = { Text("Catatan Pengerjaan (Opsional)") },
                    placeholder = { Text("Contoh: Linking kerah leher jangan terlalu kencang") },
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
                        text = if (isSubmitting) "Menyimpan..." else "Kirim ke Vendor",
                        style = ClayButtonStyle.Primary,
                        enabled = !isSubmitting && vendorName.isNotBlank(),
                        onClick = {
                            val parsedSentAt = runCatching { LocalDate.parse(sentAtStr) }.getOrNull() ?: today
                            val parsedTargetAt = runCatching { LocalDate.parse(targetAtStr) }.getOrNull()
                            val info = MakloonVendorInfo(
                                vendorName = vendorName.trim(),
                                vendorPhone = vendorPhone.trim(),
                                sentAt = parsedSentAt,
                                expectedReturnAt = parsedTargetAt,
                                returnedAt = currentVendor.returnedAt,
                                costPerPcsIdr = costStr.toLongOrNull() ?: 0L,
                                status = VendorFollowUpStatus.WITH_VENDOR,
                                notes = notes.trim()
                            )
                            onSubmit(info)
                        }
                    )
                }
            }
        }
    }
}

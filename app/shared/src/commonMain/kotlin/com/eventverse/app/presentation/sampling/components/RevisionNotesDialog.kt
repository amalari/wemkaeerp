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
import com.eventverse.app.domain.sampling.SamplingOrder
import com.eventverse.app.presentation.designsystem.*
import com.eventverse.app.presentation.theme.WeMadeColors

@Composable
fun RevisionNotesDialog(
    isOpen: Boolean,
    order: SamplingOrder?,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit
) {
    if (!isOpen || order == null) return

    var notes by remember(order.id) { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        ClayCard(
            modifier = Modifier.fillMaxWidth().padding(ClaySpacing.Md),
            shape = ClayShapes.Card,
            contentPadding = PaddingValues(ClaySpacing.Lg)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "AJUKAN REVISI SAMPLING",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = WeMadeColors.Warning
                        )
                        Text(
                            text = "${order.spkNumber.value} (Revisi ke-${order.revisionCount + 1})",
                            fontSize = 12.sp,
                            color = WeMadeColors.OnSurfaceMuted
                        )
                    }
                    ClayIconButton(onClick = onDismiss, shape = ClayShapes.Tile) {
                        IconClose(modifier = Modifier.size(16.dp))
                    }
                }

                Text(
                    text = "Pengajuan revisi akan mengembalikan status alur ke Pemrograman Mesin (CAM) dan menduplikasi riwayat saat ini ke riwayat arsip revisi.",
                    fontSize = 12.sp,
                    color = WeMadeColors.OnSurfaceMuted
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Poin Catatan Revisi dari Buyer / QC") },
                    placeholder = { Text("Contoh: Panjang badan kurang 2 cm, rib leher minta lebih kencang") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    shape = ClayShapes.Tile,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = WeMadeColors.Primary,
                        unfocusedBorderColor = WeMadeColors.Border
                    )
                )

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
                        text = if (isSubmitting) "Menyimpan..." else "Konfirmasi Revisi",
                        style = ClayButtonStyle.Primary,
                        enabled = !isSubmitting && notes.isNotBlank(),
                        onClick = { onSubmit(notes.trim()) }
                    )
                }
            }
        }
    }
}

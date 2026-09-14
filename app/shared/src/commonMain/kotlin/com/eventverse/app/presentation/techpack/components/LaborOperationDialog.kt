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
import com.eventverse.app.domain.common.Ratio
import com.eventverse.app.domain.contracts.LaborOperation
import com.eventverse.app.presentation.designsystem.*
import com.eventverse.app.presentation.theme.WeMadeColors

@Composable
fun LaborOperationDialog(
    initialOp: LaborOperation?,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onSave: (LaborOperation) -> Unit
) {
    var name by remember { mutableStateOf(initialOp?.name ?: "") }
    var workstation by remember { mutableStateOf(initialOp?.workstation ?: "") }
    var samText by remember {
        val sam = initialOp?.samMinutes?.let { it.numerator.toDouble() / it.denominator.toDouble() }
        mutableStateOf(sam?.toString() ?: "15.0")
    }
    var isSubcontracted by remember { mutableStateOf(initialOp?.isSubcontracted ?: false) }

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
                    text = if (initialOp == null) "Tambah Operasi Kerja (SAM)" else "Edit Operasi Kerja",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = WeMadeColors.OnSurface
                )

                // Operation Name
                Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)) {
                    Text(
                        text = "Nama Operasi Kerja:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = WeMadeColors.OnSurface
                    )
                    ClayTextField(
                        value = name,
                        onValueChange = { name = it },
                        placeholder = "Contoh: Knitting Panel Badan Depan & Belakang",
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Workstation Name
                Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)) {
                    Text(
                        text = "Workstation / Pos Kerja:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = WeMadeColors.OnSurface
                    )
                    ClayTextField(
                        value = workstation,
                        onValueChange = { workstation = it },
                        placeholder = "Contoh: Mesin Rajut Flatbed 12GG, Meja Linking, Washing...",
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // SAM Minutes
                Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)) {
                    Text(
                        text = "Standard Allowed Minutes (SAM):",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = WeMadeColors.OnSurface
                    )
                    ClayTextField(
                        value = samText,
                        onValueChange = { samText = it },
                        placeholder = "Contoh: 18.5 (dalam menit kerja)",
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Subcontracted toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Eksekusi Subkon Eksternal?",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = WeMadeColors.OnSurface
                        )
                        Text(
                            text = if (isSubcontracted) "Dikerjakan mitra CMT luar" else "Dikerjakan operator internal",
                            fontSize = 11.sp,
                            color = WeMadeColors.OnSurfaceMuted
                        )
                    }

                    ClayCheckbox(
                        checked = isSubcontracted,
                        onCheckedChange = { isSubcontracted = it }
                    )
                }

                // Actions
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
                        text = if (isSubmitting) "Menyimpan..." else "Simpan Operasi",
                        onClick = {
                            val opId = initialOp?.operationId ?: "op-${kotlinx.datetime.Clock.System.now().toEpochMilliseconds()}"
                            val samMinutesDbl = samText.toDoubleOrNull() ?: 0.0
                            val samRatio = Ratio.percent(samMinutesDbl * 100.0) // samMinutes as exact ratio

                            val op = LaborOperation(
                                operationId = opId,
                                name = name.trim(),
                                samMinutes = samRatio,
                                workstation = workstation.trim(),
                                isSubcontracted = isSubcontracted
                            )
                            onSave(op)
                        },
                        style = ClayButtonStyle.Primary,
                        enabled = name.isNotBlank() && !isSubmitting
                    )
                }
            }
        }
    }
}

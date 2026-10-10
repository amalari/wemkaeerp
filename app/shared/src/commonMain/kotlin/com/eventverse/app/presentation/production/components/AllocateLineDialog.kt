package com.eventverse.app.presentation.production.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.eventverse.app.domain.production.BulkWorkOrder
import com.eventverse.app.domain.production.MachineLineAllocation
import com.eventverse.app.domain.production.ProductionLineName
import com.eventverse.app.presentation.designsystem.*
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Dialog alokasi satu lini mesin ke sebagian beban SPK.
 *
 * Validasinya dikerjakan di sini sebatas yang bisa dijawab tanpa server (angka terbaca, tidak
 * melebihi sisa); aturan sebenarnya tetap milik domain dan akan menolak lagi di `allocateLine`
 * kalau ada dua admin yang mengalokasi bersamaan.
 */
@Composable
fun AllocateLineDialog(
    isOpen: Boolean,
    order: BulkWorkOrder?,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (MachineLineAllocation) -> Unit
) {
    if (!isOpen || order == null) return

    var lineName by remember(order.id) { mutableStateOf("") }
    var assignedPcs by remember(order.id) { mutableStateOf(order.unallocatedPcs.toString()) }
    var machineCount by remember(order.id) { mutableStateOf("1") }
    var operatorCount by remember(order.id) { mutableStateOf("0") }
    var notes by remember(order.id) { mutableStateOf("") }

    val pcs = assignedPcs.trim().toIntOrNull()
    val validationError = when {
        lineName.isBlank() -> "Nama lini wajib diisi."
        pcs == null || pcs <= 0 -> "Jumlah pcs harus angka lebih dari 0."
        pcs > order.unallocatedPcs -> "Melebihi sisa yang belum dialokasikan (${order.unallocatedPcs} pcs)."
        else -> null
    }

    Dialog(onDismissRequest = onDismiss) {
        ClayCard(
            modifier = Modifier.fillMaxWidth().padding(ClaySpacing.Md),
            shape = ClayShapes.Card,
            contentPadding = PaddingValues(ClaySpacing.Xl)
        ) {
            Text(
                text = "ALOKASI LINI MESIN",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = WeMadeColors.OnSurface
            )
            Text(
                text = "${order.spkNumber.value} - sisa belum dialokasikan ${order.unallocatedPcs} pcs " +
                    "dari ${order.totalOrderedPcs} pcs.",
                fontSize = 12.sp,
                color = WeMadeColors.OnSurfaceMuted
            )

            Spacer(Modifier.height(ClaySpacing.Lg))

            ClayTextField(
                value = lineName,
                onValueChange = { lineName = it },
                label = "Nama Lini (cth: Line 2 Jahit)",
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(ClaySpacing.Md))
            ClayTextField(
                value = assignedPcs,
                onValueChange = { assignedPcs = it.filter { ch -> ch.isDigit() } },
                label = "Beban Lini (pcs)",
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(ClaySpacing.Md))
            Row(horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Md)) {
                ClayTextField(
                    value = machineCount,
                    onValueChange = { machineCount = it.filter { ch -> ch.isDigit() } },
                    label = "Jumlah Mesin",
                    modifier = Modifier.weight(1f)
                )
                ClayTextField(
                    value = operatorCount,
                    onValueChange = { operatorCount = it.filter { ch -> ch.isDigit() } },
                    label = "Jumlah Operator",
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(ClaySpacing.Md))
            ClayTextField(
                value = notes,
                onValueChange = { notes = it },
                label = "Catatan (opsional)",
                modifier = Modifier.fillMaxWidth(),
                singleLine = false,
                minLines = 2
            )

            if (validationError != null && lineName.isNotBlank()) {
                Spacer(Modifier.height(ClaySpacing.Md))
                Text(text = validationError, fontSize = 11.sp, color = WeMadeColors.Error)
            }

            Spacer(Modifier.height(ClaySpacing.Xl))

            Row(horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Md)) {
                ClayButton(
                    text = "Batal",
                    onClick = onDismiss,
                    style = ClayButtonStyle.Secondary
                )
                ClayButton(
                    text = if (isSubmitting) "Menyimpan..." else "Alokasikan",
                    onClick = {
                        val allocation = MachineLineAllocation(
                            lineName = ProductionLineName(lineName.trim()),
                            machineCount = machineCount.trim().toIntOrNull() ?: 0,
                            assignedPcs = pcs ?: 0,
                            operatorCount = operatorCount.trim().toIntOrNull() ?: 0,
                            notes = notes.trim()
                        )
                        onSubmit(allocation)
                    },
                    enabled = validationError == null && !isSubmitting,
                    style = ClayButtonStyle.Primary
                )
            }
        }
    }
}

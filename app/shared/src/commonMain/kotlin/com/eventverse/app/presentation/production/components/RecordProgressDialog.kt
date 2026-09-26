package com.eventverse.app.presentation.production.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.eventverse.app.domain.production.BulkWorkOrder
import com.eventverse.app.domain.production.ProductionStage
import com.eventverse.app.presentation.designsystem.*
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Dialog pencatatan hasil satu tahap produksi.
 *
 * Angkanya **kumulatif**, dan itu dinyatakan terang-terangan di layar — operator yang mengira
 * kolom ini berisi "hasil hari ini" akan menimpa hasil kemarin dengan angka kecil, dan angka
 * kecil itu akan terlihat seperti produksi yang mundur.
 */
@Composable
fun RecordProgressDialog(
    isOpen: Boolean,
    order: BulkWorkOrder?,
    stage: ProductionStage,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (completedPcs: Int, reworkPcs: Int, rejectPcs: Int) -> Unit
) {
    if (!isOpen || order == null) return

    val current = order.progressFor(stage)
    val upstreamLimit = stage.previous?.let { order.progressFor(it).passedPcs } ?: order.totalOrderedPcs

    var completed by remember(order.id, stage) { mutableStateOf(current.completedPcs.toString()) }
    var rework by remember(order.id, stage) { mutableStateOf(current.reworkPcs.toString()) }
    var reject by remember(order.id, stage) { mutableStateOf(current.rejectPcs.toString()) }

    val completedPcs = completed.trim().toIntOrNull()
    val reworkPcs = rework.trim().toIntOrNull()
    val rejectPcs = reject.trim().toIntOrNull()

    val validationError = when {
        completedPcs == null -> "Jumlah selesai harus angka."
        reworkPcs == null || rejectPcs == null -> "Rework dan reject harus angka."
        completedPcs > upstreamLimit -> stage.previous?.let {
            "Melebihi hasil ${it.displayName} yang lolos ($upstreamLimit pcs)."
        } ?: "Melebihi total pesanan ($upstreamLimit pcs)."
        reworkPcs > completedPcs -> "Rework tidak boleh melebihi jumlah selesai."
        else -> null
    }

    Dialog(onDismissRequest = onDismiss) {
        ClayCard(
            modifier = Modifier.fillMaxWidth().padding(ClaySpacing.Md),
            shape = ClayShapes.Card,
            contentPadding = PaddingValues(ClaySpacing.Xl)
        ) {
            Text(
                text = "CATAT HASIL ${stage.displayName.uppercase()}",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = WeMadeColors.OnSurface
            )
            Text(
                text = "${order.spkNumber.value} — isi angka KUMULATIF sejak awal, bukan tambahan hari ini. " +
                    "Batas atas $upstreamLimit pcs.",
                fontSize = 12.sp,
                color = WeMadeColors.OnSurfaceMuted
            )

            Spacer(Modifier.height(ClaySpacing.Lg))

            ClayTextField(
                value = completed,
                onValueChange = { completed = it.filter { ch -> ch.isDigit() } },
                label = "Total Selesai (pcs)",
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(ClaySpacing.Md))
            Row(horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Md)) {
                ClayTextField(
                    value = rework,
                    onValueChange = { rework = it.filter { ch -> ch.isDigit() } },
                    label = "Perlu Rework",
                    modifier = Modifier.weight(1f)
                )
                ClayTextField(
                    value = reject,
                    onValueChange = { reject = it.filter { ch -> ch.isDigit() } },
                    label = "Reject",
                    modifier = Modifier.weight(1f)
                )
            }

            if (validationError != null) {
                Spacer(Modifier.height(ClaySpacing.Md))
                Text(text = validationError, fontSize = 11.sp, color = WeMadeColors.Error)
            }

            Spacer(Modifier.height(ClaySpacing.Xl))

            Row(horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Md)) {
                ClayButton(text = "Batal", onClick = onDismiss, style = ClayButtonStyle.Secondary)
                ClayButton(
                    text = if (isSubmitting) "Menyimpan…" else "Simpan Hasil",
                    onClick = {
                        onSubmit(completedPcs ?: 0, reworkPcs ?: 0, rejectPcs ?: 0)
                    },
                    enabled = validationError == null && !isSubmitting,
                    style = ClayButtonStyle.Primary
                )
            }
        }
    }
}

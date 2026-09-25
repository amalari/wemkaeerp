package com.eventverse.app.presentation.operator

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.eventverse.app.domain.sampling.QcInspectionKind
import com.eventverse.app.domain.sampling.QcInspectionReport
import com.eventverse.app.domain.sampling.SamplingOrder
import com.eventverse.app.presentation.designsystem.ClayButton
import com.eventverse.app.presentation.designsystem.ClayButtonStyle
import com.eventverse.app.presentation.designsystem.ClayCard
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.qc.buildQcQueue
import com.eventverse.app.presentation.qc.components.QcInspectionPane
import com.eventverse.app.presentation.theme.WeMadeColors
import kotlinx.datetime.Clock

/**
 * Lembar QC Finishing dari meja QC — memakai [QcInspectionPane] yang sama dengan modul Inspeksi
 * QC, supaya berita acaranya satu format di mana pun diisi. Lolos → SPK pindah sendiri ke
 * Pengemasan; perlu perbaikan → operator menekan "Rework" di kartunya.
 */
@Composable
fun QcDeskInspectionDialog(
    order: SamplingOrder,
    allOrders: List<SamplingOrder>,
    inspectorName: String,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (QcInspectionReport) -> Unit
) {
    val item = remember(order, allOrders) {
        buildQcQueue(allOrders, Clock.System.now(), QcInspectionKind.FINISHING).firstOrNull { it.order.id == order.id }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        ClayCard(
            modifier = Modifier.widthIn(max = 960.dp).fillMaxWidth(0.94f).fillMaxSize(0.92f),
            contentPadding = PaddingValues(ClaySpacing.Xl)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Lembar QC • ${order.spkNumber.value}",
                        style = MaterialTheme.typography.titleLarge,
                        color = WeMadeColors.OnSurface,
                        modifier = Modifier.weight(1f)
                    )
                    ClayButton(text = "Tutup", style = ClayButtonStyle.Secondary, onClick = onDismiss)
                }
                QcInspectionPane(
                    item = item,
                    inspectorName = inspectorName,
                    isSubmitting = isSubmitting,
                    onSubmit = onSubmit,
                    modifier = Modifier.fillMaxWidth().weight(1f)
                )
            }
        }
    }
}

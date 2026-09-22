package com.eventverse.app.presentation.sampling.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.eventverse.app.domain.sampling.SamplingOrder
import com.eventverse.app.domain.sampling.SamplingPipelineStage
import com.eventverse.app.presentation.designsystem.ClayBadge
import com.eventverse.app.presentation.designsystem.ClayButton
import com.eventverse.app.presentation.designsystem.ClayButtonStyle
import com.eventverse.app.presentation.designsystem.ClayCard
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.IconClose
import com.eventverse.app.presentation.sampling.ProcessFlowScope
import com.eventverse.app.presentation.sampling.ProcessFlowUiEvent
import com.eventverse.app.presentation.sampling.ProcessFlowViewModel
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Dialog detail SPK — dibuka saat kartu di kolom Kanban diklik.
 *
 * Fokus tahap SPK Baru / Penentuan Alur adalah **referensi klien & alur proses**, bukan input
 * teknis CAM:
 * - [ClientSamplingReferenceCard] menampilkan referensi dari Deal (mockup depan/belakang,
 *   matriks ukuran POM + alokasi qty, jalur finishing, deadline).
 * - [ProcessFlowAdjusterPanel] membiarkan operator menyisipkan proses opsional (Bordir, Sablon,
 *   Laundry) ke alur desain ini.
 * - Tombol "Alur Siap -> Mulai CAM" membuka gerbang transisi tahap ([StageAdvanceDialog]) —
 *   di sanalah lembar Program CAM diisi, konsisten dengan alur kerja kolom Kanban lainnya.
 */
@Composable
fun SamplingSpkDetailDialog(
    order: SamplingOrder,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onStartCam: () -> Unit,
    onCreateTechPack: ((SamplingOrder) -> Unit)? = null,
    processFlowViewModel: ProcessFlowViewModel? = null
) {
    // Gerbang klien: tombol mulai CAM hanya tampil pada tahap SPK Masuk / Penentuan Alur.
    val isGateStage = order.pipelineStage == SamplingPipelineStage.NEW_INTAKE ||
        order.pipelineStage == SamplingPipelineStage.FLOW_REVIEW

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        ClayCard(
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .heightIn(max = 760.dp),
            contentPadding = PaddingValues(0.dp)
        ) {
            Column(
                modifier = Modifier.padding(ClaySpacing.Lg),
                verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f, fill = false)) {
                        Text(
                            text = "Detail SPK — ${order.spkNumber.value}",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = WeMadeColors.OnSurface
                        )
                        Text(
                            text = "${order.clientName} · ${order.styleName}",
                            fontSize = 11.sp,
                            color = WeMadeColors.OnSurfaceMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    ClayBadge(
                        text = order.pipelineStage.displayName,
                        tint = samplingStageTint(order.pipelineStage)
                    )
                    androidx.compose.material3.IconButton(onClick = onDismiss) {
                        IconClose(modifier = Modifier.size(18.dp))
                    }
                }

                // Konten yang dapat di-scroll: referensi klien di atas, alur proses di bawah
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
                ) {
                    ClientSamplingReferenceCard(order)

                    // Alur Proses Khusus SPK / Desain Ini
                    if (processFlowViewModel != null) {
                        LaunchedEffect(order.id) {
                            processFlowViewModel.onEvent(
                                ProcessFlowUiEvent.SelectScope(
                                    ProcessFlowScope.Design(
                                        orderId = order.id.value,
                                        styleName = order.styleName,
                                        spkNumber = order.spkNumber.value
                                    )
                                )
                            )
                        }
                        ProcessFlowAdjusterPanel(
                            viewModel = processFlowViewModel,
                            hideScopeSelector = true
                        )
                    }
                }

                // Footer
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
                ) {
                    if (onCreateTechPack != null) {
                        ClayButton(
                            text = "Buat Tech Pack",
                            style = ClayButtonStyle.Secondary,
                            modifier = Modifier.weight(1f),
                            onClick = { onCreateTechPack(order) }
                        )
                    }
                    ClayButton(
                        text = "Tutup",
                        style = ClayButtonStyle.Secondary,
                        modifier = Modifier.weight(1f),
                        onClick = onDismiss
                    )
                    if (isGateStage) {
                        ClayButton(
                            text = "Alur Siap -> Mulai CAM",
                            style = ClayButtonStyle.Primary,
                            modifier = Modifier.weight(2f),
                            enabled = !isSubmitting,
                            onClick = onStartCam
                        )
                    }
                }
            }
        }
    }
}

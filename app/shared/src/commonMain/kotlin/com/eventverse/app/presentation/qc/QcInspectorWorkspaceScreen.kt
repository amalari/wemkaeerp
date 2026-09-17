package com.eventverse.app.presentation.qc

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.domain.rbac.AccessDecision
import com.eventverse.app.domain.rbac.TestingPersona
import com.eventverse.app.domain.sampling.QcInspectionReport
import com.eventverse.app.domain.sampling.QcInspectionResult
import com.eventverse.app.domain.sampling.SamplingOrder
import com.eventverse.app.domain.sampling.SamplingPipelineStage
import com.eventverse.app.presentation.designsystem.*
import com.eventverse.app.presentation.sampling.SamplingUiEvent
import com.eventverse.app.presentation.sampling.SamplingViewModel
import com.eventverse.app.presentation.sampling.components.QcInspectionDialog
import com.eventverse.app.presentation.theme.WeMadeColors

@Composable
fun QcInspectorWorkspaceScreen(
    tenantSlug: String,
    decision: AccessDecision,
    persona: TestingPersona?,
    modifier: Modifier = Modifier,
    viewModel: SamplingViewModel = remember(tenantSlug) { SamplingViewModel(tenantSlug) }
) {
    val state by viewModel.uiState.collectAsState()
    var targetOrderForQc by remember { mutableStateOf<SamplingOrder?>(null) }

    // QC memeriksa order yang berada pada tahap FINISHING_QC, IN_DELIVERY, atau yang sudah punya setoran finishing
    val qcQueue = remember(state.orders) {
        state.orders.filter { order ->
            order.pipelineStage == SamplingPipelineStage.FINISHING_QC ||
            order.pipelineStage == SamplingPipelineStage.IN_DELIVERY ||
            order.qcInspections.isNotEmpty() ||
            order.totalFinishedDepositedQty > 0
        }
    }

    val pendingQc = qcQueue.count { it.pipelineStage == SamplingPipelineStage.FINISHING_QC }
    val passedQc = qcQueue.count { it.latestQcReport?.qcResult == QcInspectionResult.PASSED }
    val reworkQc = qcQueue.count { it.latestQcReport?.qcResult == QcInspectionResult.REWORK }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(ClaySpacing.Md),
        verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
    ) {
        // Header
        ClayCard(
            modifier = Modifier.fillMaxWidth(),
            shape = ClayShapes.Card,
            contentPadding = PaddingValues(ClaySpacing.Md)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "KONTROL KUALITAS (QUALITY CONTROL)",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = WeMadeColors.OnSurface
                        )
                        ClayBadge(text = "Divisi QC Inspeksi", tint = WeMadeColors.Success)
                    }
                    Text(
                        text = "Verifikasi fisik POM vs toleransi spesifikasi buyer (maks. +/- 1.0 cm) & checklist cacat",
                        fontSize = 12.sp,
                        color = WeMadeColors.OnSurfaceMuted
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
                    ClayBadge(
                        text = "$pendingQc Menunggu QC",
                        tint = if (pendingQc > 0) WeMadeColors.Warning else WeMadeColors.OnSurfaceMuted
                    )
                    ClayBadge(
                        text = "$passedQc Lolos QC",
                        tint = WeMadeColors.Success
                    )
                    if (reworkQc > 0) {
                        ClayBadge(
                            text = "$reworkQc Perlu Rework",
                            tint = WeMadeColors.Error
                        )
                    }
                }
            }
        }

        // Queue
        if (state.isLoading && state.orders.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = WeMadeColors.Primary)
            }
        } else if (qcQueue.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center
            ) {
                ClayCard(
                    modifier = Modifier.widthIn(max = 420.dp).padding(ClaySpacing.Xl),
                    shape = ClayShapes.Card,
                    contentPadding = PaddingValues(ClaySpacing.Xl)
                ) {
                    Text(
                        text = "Tidak Ada Antrean QC",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = WeMadeColors.OnSurface
                    )
                    Spacer(modifier = Modifier.height(ClaySpacing.Sm))
                    Text(
                        text = "Belum ada pakaian sampel jadi dari finishing yang siap diinspeksi.",
                        fontSize = 12.sp,
                        color = WeMadeColors.OnSurfaceMuted
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
            ) {
                items(qcQueue, key = { it.id.value }) { order ->
                    val latestQc = order.latestQcReport
                    val resultBadgeColor = when (latestQc?.qcResult) {
                        QcInspectionResult.PASSED -> WeMadeColors.Success
                        QcInspectionResult.REWORK -> WeMadeColors.Warning
                        QcInspectionResult.REJECT -> WeMadeColors.Error
                        null -> WeMadeColors.OnSurfaceMuted
                    }

                    ClayCard(
                        modifier = Modifier.fillMaxWidth(),
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
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = order.spkNumber.value,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = WeMadeColors.Primary
                                        )
                                        ClayBadge(
                                            text = latestQc?.qcResult?.displayName ?: "Belum Diinspeksi",
                                            tint = resultBadgeColor
                                        )
                                    }
                                    Text(
                                        text = "${order.clientName} • ${order.styleName} (${order.sampleQuantity} Pcs)",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = WeMadeColors.OnSurface
                                    )
                                }

                                ClayButton(
                                    text = if (latestQc != null) "Inspeksi Ulang" else "+ Lakukan Inspeksi QC",
                                    style = ClayButtonStyle.Success,
                                    onClick = { targetOrderForQc = order }
                                )
                            }

                            // Summary of measurements if any
                            if (latestQc != null) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(WeMadeColors.SurfaceMuted, ClayShapes.Tile)
                                        .padding(ClaySpacing.Md)
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)) {
                                        Text(
                                            text = "Inspektor: ${latestQc.inspectorName} (${latestQc.inspectedAt.toString().take(10)})",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = WeMadeColors.OnSurface
                                        )
                                        if (latestQc.qcNotes.isNotBlank()) {
                                            Text(
                                                text = "Catatan: ${latestQc.qcNotes}",
                                                fontSize = 11.sp,
                                                color = WeMadeColors.OnSurfaceMuted
                                            )
                                        }
                                        if (latestQc.defectsFound.isNotEmpty()) {
                                            Text(
                                                text = "Temuan Cacat: ${latestQc.defectsFound.joinToString(", ")}",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = WeMadeColors.Error
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // QC Dialog
    QcInspectionDialog(
        isOpen = targetOrderForQc != null,
        order = targetOrderForQc,
        isSubmitting = state.isSubmitting,
        onDismiss = { targetOrderForQc = null },
        onSubmit = { report ->
            val order = targetOrderForQc ?: return@QcInspectionDialog
            viewModel.onEvent(SamplingUiEvent.SubmitQcInspection(order.id, report))
            targetOrderForQc = null
        }
    )
}

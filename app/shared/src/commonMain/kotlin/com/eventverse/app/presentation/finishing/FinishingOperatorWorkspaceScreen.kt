package com.eventverse.app.presentation.finishing

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
import com.eventverse.app.domain.sampling.FinishingDeposit
import com.eventverse.app.domain.sampling.FinishingPath
import com.eventverse.app.domain.sampling.SamplingOrder
import com.eventverse.app.domain.sampling.SamplingPipelineStage
import com.eventverse.app.presentation.designsystem.*
import com.eventverse.app.presentation.sampling.SamplingUiEvent
import com.eventverse.app.presentation.sampling.SamplingViewModel
import com.eventverse.app.presentation.sampling.components.FinishingSetoranDialog
import com.eventverse.app.presentation.theme.WeMadeColors

@Composable
fun FinishingOperatorWorkspaceScreen(
    tenantSlug: String,
    decision: AccessDecision,
    persona: TestingPersona?,
    modifier: Modifier = Modifier,
    viewModel: SamplingViewModel = remember(tenantSlug) { SamplingViewModel(tenantSlug) }
) {
    val state by viewModel.uiState.collectAsState()
    var targetOrderForSetoran by remember { mutableStateOf<SamplingOrder?>(null) }

    // Operator finishing melihat order yang berada pada tahap LINKING_ASSEMBLY atau FINISHING_QC dengan jalur internal
    val finishingQueue = remember(state.orders) {
        state.orders.filter { order ->
            (order.pipelineStage == SamplingPipelineStage.LINKING_ASSEMBLY ||
             order.pipelineStage == SamplingPipelineStage.FINISHING_QC ||
             order.finishingDeposits.isNotEmpty()) &&
            order.finishingPath == FinishingPath.INTERNAL
        }
    }

    val totalWaiting = finishingQueue.count { !it.isFinishingComplete }
    val totalDone = finishingQueue.count { it.isFinishingComplete }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(ClaySpacing.Md),
        verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
    ) {
        // Top Header
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
                            text = "CATATAN KERJA OPERATOR FINISHING",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = WeMadeColors.OnSurface
                        )
                        ClayBadge(text = "Divisi Finishing & Linking", tint = WeMadeColors.Primary)
                    }
                    Text(
                        text = "Antrean perakitan, linking, obras, dan setoran bertahap hasil timbangan",
                        fontSize = 12.sp,
                        color = WeMadeColors.OnSurfaceMuted
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
                    ClayBadge(
                        text = "$totalWaiting Perlu Dikerjakan",
                        tint = if (totalWaiting > 0) WeMadeColors.Warning else WeMadeColors.Success
                    )
                    ClayBadge(
                        text = "$totalDone Selesai",
                        tint = WeMadeColors.Success
                    )
                }
            }
        }

        // Main Queue
        if (state.isLoading && state.orders.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = WeMadeColors.Primary)
            }
        } else if (finishingQueue.isEmpty()) {
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
                        text = "Tidak Ada Antrean Finishing",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = WeMadeColors.OnSurface
                    )
                    Spacer(modifier = Modifier.height(ClaySpacing.Sm))
                    Text(
                        text = "Saat ini tidak ada kain rajutan yang sedang menunggu perakitan linking atau finishing internal.",
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
                items(finishingQueue, key = { it.id.value }) { order ->
                    ClayCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = ClayShapes.Card,
                        contentPadding = PaddingValues(ClaySpacing.Lg)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)) {
                            // Header Row
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
                                            text = order.pipelineStage.displayName,
                                            tint = if (order.isFinishingComplete) WeMadeColors.Success else WeMadeColors.Primary
                                        )
                                    }
                                    Text(
                                        text = "${order.clientName} • ${order.styleName}",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = WeMadeColors.OnSurface
                                    )
                                }

                                ClayButton(
                                    text = if (order.isFinishingComplete) "Tambah Setoran" else "+ Input Setoran Hasil",
                                    style = if (order.isFinishingComplete) ClayButtonStyle.Secondary else ClayButtonStyle.Primary,
                                    onClick = { targetOrderForSetoran = order }
                                )
                            }

                            // Progress Summary
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(WeMadeColors.SurfaceMuted, ClayShapes.Tile)
                                    .padding(ClaySpacing.Md),
                                horizontalArrangement = Arrangement.SpaceAround
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(text = "Target Total", fontSize = 11.sp, color = WeMadeColors.OnSurfaceMuted)
                                    Text(text = "${order.sampleQuantity} Pcs", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(text = "Sudah Disetor", fontSize = 11.sp, color = WeMadeColors.OnSurfaceMuted)
                                    Text(text = "${order.totalFinishedDepositedQty} Pcs", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = WeMadeColors.Success)
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(text = "Sisa Belum", fontSize = 11.sp, color = WeMadeColors.OnSurfaceMuted)
                                    Text(
                                        text = "${order.remainingFinishingQty} Pcs",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (order.remainingFinishingQty == 0) WeMadeColors.Success else WeMadeColors.Accent
                                    )
                                }
                            }

                            // Riwayat Setoran Sebelumnya
                            if (order.finishingDeposits.isNotEmpty()) {
                                Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)) {
                                    Text(
                                        text = "Riwayat Setoran Operator:",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = WeMadeColors.OnSurfaceMuted
                                    )
                                    order.finishingDeposits.forEach { dep ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "• ${dep.depositDate} — ${dep.operatorName.ifBlank { "Operator" }}: ${dep.qtyPcs} Pcs (${dep.weightKg} Kg)",
                                                fontSize = 11.sp,
                                                color = WeMadeColors.OnSurface
                                            )
                                            if (dep.notes.isNotBlank()) {
                                                Text(
                                                    text = dep.notes,
                                                    fontSize = 11.sp,
                                                    color = WeMadeColors.OnSurfaceMuted
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
    }

    // Setoran Dialog
    FinishingSetoranDialog(
        isOpen = targetOrderForSetoran != null,
        order = targetOrderForSetoran,
        isSubmitting = state.isSubmitting,
        onDismiss = { targetOrderForSetoran = null },
        onSubmit = { deposit ->
            val order = targetOrderForSetoran ?: return@FinishingSetoranDialog
            viewModel.onEvent(SamplingUiEvent.AddFinishingDeposit(order.id, deposit))
            targetOrderForSetoran = null
        }
    )
}

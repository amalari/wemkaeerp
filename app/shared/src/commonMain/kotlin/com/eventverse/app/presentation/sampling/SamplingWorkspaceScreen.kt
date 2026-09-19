package com.eventverse.app.presentation.sampling

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.domain.rbac.AccessDecision
import com.eventverse.app.domain.rbac.TestingPersona
import com.eventverse.app.domain.sampling.SamplingOrder
import com.eventverse.app.domain.sampling.SamplingPipelineStage
import com.eventverse.app.domain.sampling.requiresStageWorksheet
import com.eventverse.app.presentation.designsystem.ClayBadge
import com.eventverse.app.presentation.designsystem.ClayButton
import com.eventverse.app.presentation.designsystem.ClayButtonStyle
import com.eventverse.app.presentation.designsystem.ClayCard
import com.eventverse.app.presentation.designsystem.ClayShapes
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.sampling.components.CreateSamplingOrderDialog
import com.eventverse.app.presentation.sampling.components.RevisionNotesDialog
import com.eventverse.app.presentation.sampling.components.SamplingPipelineKanbanBoard
import com.eventverse.app.presentation.sampling.components.SamplingSpkDetailDialog
import com.eventverse.app.presentation.sampling.components.StageAdvanceDialog
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Layar modul Order Sampling — murni Pipeline Kanban.
 *
 * - Tidak ada lagi tab Workbench/Monitoring Vendor: papan kanban adalah satu-satunya view.
 * - Klik kartu di kolom "SPK Baru" membuka dialog detail SPK ([SamplingSpkDetailDialog]) —
 *   meja persiapan Program CAM tim sampling.
 * - Transisi antar kolom yang menuntut lembar kerja (CAM, Rajut, Finishing) membuka
 *   dialog tahap masing-masing via [StageAdvanceDialog].
 */
@Composable
fun SamplingWorkspaceScreen(
    tenantSlug: String,
    decision: AccessDecision,
    persona: TestingPersona?,
    modifier: Modifier = Modifier,
    viewModel: SamplingViewModel = remember(tenantSlug) { SamplingViewModel(tenantSlug) },
    onCreateTechPack: ((SamplingOrder) -> Unit)? = null
) {
    val state by viewModel.uiState.collectAsState()

    Column(modifier = modifier.fillMaxSize()) {
        // Top Toolbar
        ClayCard(
            modifier = Modifier.fillMaxWidth().padding(horizontal = ClaySpacing.Md, vertical = ClaySpacing.Sm),
            shape = ClayShapes.Card,
            contentPadding = PaddingValues(horizontal = ClaySpacing.Md, vertical = ClaySpacing.Sm)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Md),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "ORDER SAMPLING",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = WeMadeColors.OnSurface
                        )
                        Text(
                            text = "Pipeline Kanban SPK — Program CAM, Rajut, Finishing & ACC Buyer",
                            fontSize = 11.sp,
                            color = WeMadeColors.OnSurfaceMuted
                        )
                    }

                    if (state.orders.isNotEmpty()) {
                        ClayBadge(
                            text = "${state.orders.size} SPK",
                            tint = WeMadeColors.Primary
                        )
                    }
                }

                ClayButton(
                    text = "+ Buat SPK Sample",
                    style = ClayButtonStyle.Primary,
                    onClick = { viewModel.onEvent(SamplingUiEvent.OpenCreateDialog) }
                )
            }
        }

        // Status banner if any
        if (state.statusMessage != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ClaySpacing.Md, vertical = ClaySpacing.Xs)
            ) {
                ClayBadge(
                    text = state.statusMessage ?: "",
                    tint = if (state.isErrorMessage) WeMadeColors.Error else WeMadeColors.Success,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        // Main Content — Pipeline Kanban murni
        Box(modifier = Modifier.fillMaxSize()) {
            if (state.isLoading && state.orders.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = WeMadeColors.Primary)
                }
            } else if (state.orders.isEmpty()) {
                // Empty state
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    ClayCard(
                        modifier = Modifier.widthIn(max = 420.dp).padding(ClaySpacing.Xl),
                        shape = ClayShapes.Card,
                        contentPadding = PaddingValues(ClaySpacing.Xl)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Belum Ada SPK Sample",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = WeMadeColors.OnSurface
                            )
                            Spacer(Modifier.padding(ClaySpacing.Sm))
                            Text(
                                text = "Buat SPK Sample pertama untuk mulai memprogram mesin rajut dan mencatat ukuran sampel.",
                                fontSize = 12.sp,
                                color = WeMadeColors.OnSurfaceMuted
                            )
                            Spacer(Modifier.padding(ClaySpacing.Lg))
                            ClayButton(
                                text = "Buat SPK Sample Sekarang",
                                style = ClayButtonStyle.Accent,
                                onClick = { viewModel.onEvent(SamplingUiEvent.OpenCreateDialog) }
                            )
                        }
                    }
                }
            } else {
                SamplingPipelineKanbanBoard(
                    orders = state.filteredOrders,
                    selectedOrderId = state.selectedOrderId,
                    onSelectOrder = { viewModel.onEvent(SamplingUiEvent.SelectOrder(it)) },
                    onOpenSpkDetail = { viewModel.onEvent(SamplingUiEvent.OpenSpkDetailDialog(it)) },
                    onAdvanceStageRequested = { order, stage ->
                        // Satu sumber kebenaran: transisi yang menuntut lembar kerja
                        // membuka dialog dulu; sisanya langsung maju (backend tetap
                        // memvalidasi gerbang + mencatat audit aktor).
                        if (stage.requiresStageWorksheet()) {
                            viewModel.onEvent(SamplingUiEvent.OpenStageAdvanceDialog(order, stage))
                        } else {
                            viewModel.onEvent(SamplingUiEvent.AdvanceStage(order.id, stage))
                        }
                    },
                    onOpenRevisionDialog = {
                        viewModel.onEvent(SamplingUiEvent.OpenRevisionDialog(it))
                    },
                    onApproveOrder = { id, notes ->
                        viewModel.onEvent(SamplingUiEvent.ApproveOrder(id, true, notes))
                    }
                )
            }
        }
    // 1. Create SPK Dialog
    CreateSamplingOrderDialog(
        isOpen = state.isCreateDialogOpen,
        isSubmitting = state.isSubmitting,
        onDismiss = { viewModel.onEvent(SamplingUiEvent.CloseCreateDialog) },
        onSubmit = { client, style, sizeMode, usePreset ->
            viewModel.onEvent(SamplingUiEvent.CreateOrder(client, style, sizeMode, usePreset))
        }
    )

    // 2. SPK Detail Dialog — klik kartu di kolom "SPK Baru" (persiapan Program CAM)
    state.spkDetailTarget?.let { target ->
        SamplingSpkDetailDialog(
            order = target,
            isSubmitting = state.isSubmitting,
            onDismiss = { viewModel.onEvent(SamplingUiEvent.CloseSpkDetailDialog) },
            onStartCamProgram = { sections ->
                viewModel.onEvent(
                    SamplingUiEvent.ConfirmStageAdvance(
                        target.id, SamplingPipelineStage.CAM_PROGRAMMING, sections
                    )
                )
            },
            onCreateTechPack = onCreateTechPack
        )
    }

    // 3. Revision Dialog
    RevisionNotesDialog(
        isOpen = state.isRevisionDialogOpen,
        order = state.targetOrderForAction ?: state.selectedOrder,
        isSubmitting = state.isSubmitting,
        onDismiss = { viewModel.onEvent(SamplingUiEvent.CloseRevisionDialog) },
        onSubmit = { notes ->
            val targetId = (state.targetOrderForAction ?: state.selectedOrder)?.id ?: return@RevisionNotesDialog
            viewModel.onEvent(SamplingUiEvent.RequestRevision(targetId, notes))
        }
    )

    // 4. Stage Advance Dialog — lembar kerja dinamis (CAM, Rajut, Finishing)
    val advanceTarget = state.stageAdvanceTarget
    val advanceTargetStage = state.stageAdvanceTargetStage
    if (advanceTarget != null && advanceTargetStage != null) {
        StageAdvanceDialog(
            order = advanceTarget,
            targetStage = advanceTargetStage,
            isSubmitting = state.isSubmitting,
            onDismiss = { viewModel.onEvent(SamplingUiEvent.CloseStageAdvanceDialog) },
            onConfirm = { sections ->
                viewModel.onEvent(
                    SamplingUiEvent.ConfirmStageAdvance(advanceTarget.id, advanceTargetStage, sections)
                )
            }
        )
    }
    }
}

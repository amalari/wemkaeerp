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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.eventverse.app.domain.traceability.TraceWorkOrderKind
import com.eventverse.app.domain.traceability.TraceWorkOrderRef
import com.eventverse.app.presentation.deal.components.rememberPdfPrintLauncher
import com.eventverse.app.presentation.designsystem.ClayBadge
import com.eventverse.app.presentation.designsystem.ClayCard
import com.eventverse.app.presentation.designsystem.ClayShapes
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.sampling.components.RevisionNotesDialog
import com.eventverse.app.presentation.sampling.components.SamplingPipelineKanbanBoard
import com.eventverse.app.presentation.sampling.components.SamplingSpkDetailDialog
import com.eventverse.app.presentation.sampling.components.StageAdvanceDialog
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Layar modul Order Sampling — murni Pipeline Kanban.
 *
 * - Tidak ada lagi tab Workbench/Monitoring Vendor: papan kanban adalah satu-satunya view.
 * - SPK masuk otomatis dari Deal/CRM saat deal disetujui.
 * - Klik kartu di kolom Kanban membuka dialog detail SPK ([SamplingSpkDetailDialog]) —
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
    // Launcher di level layar, bukan di dialog: dialog sudah tertutup saat pindah tahap sukses.
    val spkCardPrinter = rememberPdfPrintLauncher()
    LaunchedEffect(state.spkCardToPrint) {
        val orderId = state.spkCardToPrint ?: return@LaunchedEffect
        spkCardPrinter.open { spkCardPdfUrl(it, TraceWorkOrderRef(TraceWorkOrderKind.SAMPLING, orderId.value)) }
        viewModel.onEvent(SamplingUiEvent.SpkCardPrintHandled)
    }
    val processFlowViewModel = remember(tenantSlug) { ProcessFlowViewModel() }

    LaunchedEffect(state.orders) {
        val scopeItems = state.orders.map {
            SamplingOrderScopeItem(
                orderId = it.id.value,
                styleName = it.styleName,
                spkNumber = it.spkNumber.value,
                isCustomFlow = it.isCustomFlow
            )
        }
        processFlowViewModel.onEvent(ProcessFlowUiEvent.SetAvailableOrders(scopeItems))
    }

    LaunchedEffect(state.selectedOrderId) {
        val selectedId = state.selectedOrderId?.value
        if (selectedId != null) {
            val selected = state.orders.firstOrNull { it.id.value == selectedId }
            if (selected != null) {
                processFlowViewModel.onEvent(
                    ProcessFlowUiEvent.SelectScope(
                        ProcessFlowScope.Design(selected.id.value, selected.styleName, selected.spkNumber.value)
                    )
                )
            }
        }
    }

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

        // Gagal menyiapkan Kartu SPK A6 otomatis — dialognya sudah tertutup, jadi dilaporkan di sini.
        spkCardPrinter.error?.let { message ->
            Box(modifier = Modifier.fillMaxWidth().padding(horizontal = ClaySpacing.Md, vertical = ClaySpacing.Xs)) {
                ClayBadge(text = message, tint = WeMadeColors.Error, modifier = Modifier.fillMaxWidth())
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
                                text = "SPK Sample otomatis masuk saat pesanan sampling disetujui di modul CRM Deal.",
                                fontSize = 12.sp,
                                color = WeMadeColors.OnSurfaceMuted
                            )
                        }
                    }
                }
            } else {
                SamplingPipelineKanbanBoard(
                    orders = state.filteredOrders,
                    selectedOrderId = state.selectedOrderId,
                    onSelectOrder = { viewModel.onEvent(SamplingUiEvent.SelectOrder(it)) },
                    onOpenSpkDetail = { order, focusFlow ->
                        viewModel.onEvent(SamplingUiEvent.OpenSpkDetailDialog(order, focusFlow))
                        if (focusFlow) viewModel.onEvent(SamplingUiEvent.DetermineFlow(order.id))
                    },
                    onAdvanceStageRequested = { order, stage ->
                        // Satu sumber kebenaran: transisi yang menuntut lembar kerja
                        // membuka dialog dulu; sisanya langsung maju (backend tetap
                        // memvalidasi gerbang + mencatat audit aktor).
                        if (stage == SamplingPipelineStage.CAM_PROGRAMMING) {
                            // Masuk Program CAM menuntut lembar Program CAM (program, feeder,
                            // tenselity, dan catatan rumus pola) diisi di dialog Detail SPK.
                            viewModel.onEvent(SamplingUiEvent.OpenSpkDetailDialog(order, focusCam = true))
                        } else if (stage.requiresStageWorksheet()) {
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
    }

    // SPK Detail Dialog — klik kartu di kolom Kanban (persiapan Program CAM & atur alur proses desain)
    state.spkDetailTarget?.let { target ->
        SamplingSpkDetailDialog(
            order = target,
            isSubmitting = state.isSubmitting,
            initialShowFlowSection = state.spkDetailFocusFlow,
            initialShowCamSection = state.spkDetailFocusCam,
            onDismiss = { viewModel.onEvent(SamplingUiEvent.CloseSpkDetailDialog) },
            onSubmitCamProgram = { sections ->
                val targetStage = if (target.pipelineStage == SamplingPipelineStage.CAM_PROGRAMMING) {
                    SamplingPipelineStage.MACHINE_KNITTING
                } else {
                    SamplingPipelineStage.CAM_PROGRAMMING
                }
                viewModel.onEvent(
                    SamplingUiEvent.ConfirmStageAdvance(
                        orderId = target.id,
                        targetStage = targetStage,
                        sections = sections,
                        inputStage = SamplingPipelineStage.CAM_PROGRAMMING,
                        // Hanya CAM → Rajut yang mencetak kartu; gerbang → CAM belum punya kartu fisik.
                        openSpkCardOnSuccess = target.pipelineStage == SamplingPipelineStage.CAM_PROGRAMMING
                    )
                )
            },
            draftSaveStatus = state.draftSave.statusFor(target.id),
            onDraftChange = { sections ->
                viewModel.onEvent(SamplingUiEvent.SaveStageInput(target.id, SamplingPipelineStage.CAM_PROGRAMMING, sections))
            },
            onDetermineFlow = { viewModel.onEvent(SamplingUiEvent.DetermineFlow(target.id)) },
            onCreateTechPack = onCreateTechPack,
            processFlowViewModel = processFlowViewModel
        )
    }

    // Revision Dialog
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

    // Stage Advance Dialog — lembar kerja dinamis (CAM, Rajut, Finishing)
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


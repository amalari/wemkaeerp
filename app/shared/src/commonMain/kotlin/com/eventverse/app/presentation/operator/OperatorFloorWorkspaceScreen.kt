package com.eventverse.app.presentation.operator

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.eventverse.app.domain.rbac.AccessDecision
import com.eventverse.app.domain.rbac.TestingPersona
import com.eventverse.app.domain.sampling.OperatorDeskColumn
import com.eventverse.app.domain.sampling.SamplingOrder
import com.eventverse.app.domain.sampling.SamplingPipelineStage
import com.eventverse.app.domain.sampling.deskColumn
import com.eventverse.app.domain.sampling.isOperatorDesk
import com.eventverse.app.presentation.designsystem.ClayChoiceChip
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.ClayStatusBanner
import com.eventverse.app.presentation.sampling.SamplingUiEvent
import com.eventverse.app.presentation.sampling.SamplingViewModel
import com.eventverse.app.presentation.sampling.components.FinishingSetoranDialog
import com.eventverse.app.presentation.sampling.components.StageAdvanceDialog
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Modul Lantai Produksi (`OPERATOR_EXEC`) — satu meja per bagian sampling (Rajut sampai Kemas),
 * masing-masing kanban tiga kolom Antrian / Sedang Dikerjakan / Selesai.
 *
 * Semua meja berbagi satu [SamplingViewModel], jadi SPK yang diserahkan satu meja langsung muncul
 * di antrian meja berikutnya tanpa memuat ulang. Daftar meja dibaca dari
 * [SamplingPipelineStage.isOperatorDesk], bukan didaftar tangan, supaya tahap baru ikut punya meja.
 */
@Composable
fun OperatorFloorWorkspaceScreen(
    tenantSlug: String,
    decision: AccessDecision,
    persona: TestingPersona?,
    modifier: Modifier = Modifier
) {
    val viewModel = remember(tenantSlug) { SamplingViewModel(tenantSlug) }
    val state by viewModel.uiState.collectAsState()
    val desks = remember { SamplingPipelineStage.entries.filter { it.isOperatorDesk } }
    var desk by remember { mutableStateOf(desks.first()) }
    // Dibaca dari sesi sebagai isian awal, tapi tetap bisa diganti: satu tablet lantai produksi
    // lazim dipakai bergantian beberapa operator dalam satu shift.
    var operatorName by remember(persona?.userId) { mutableStateOf(persona?.name.orEmpty()) }
    var setoranTarget by remember { mutableStateOf<SamplingOrder?>(null) }
    var qcTarget by remember { mutableStateOf<SamplingOrder?>(null) }
    var showHistory by remember { mutableStateOf(false) }

    val timeZone = remember { TimeZone.currentSystemDefault() }
    val today = remember(state.orders) { Clock.System.now().toLocalDateTime(timeZone).date }
    val board = remember(state.orders, desk, today) { buildOperatorDeskBoard(state.orders, desk, today, timeZone) }

    Column(modifier = modifier.fillMaxSize().padding(ClaySpacing.Md), verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)) {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
        ) {
            desks.forEach { stage ->
                val waiting = state.orders.count { it.deskColumn(stage) == OperatorDeskColumn.QUEUE }
                ClayChoiceChip(
                    text = if (waiting > 0) "${stage.deskLabel} ($waiting)" else stage.deskLabel,
                    selected = desk == stage,
                    onClick = { desk = stage }
                )
            }
        }

        state.statusMessage?.let { message ->
            ClayStatusBanner(
                message = message,
                isError = state.isErrorMessage,
                onDismiss = { viewModel.onEvent(SamplingUiEvent.DismissStatusMessage) }
            )
        }

        OperatorStageDesk(
            board = board,
            operatorName = operatorName,
            onOperatorNameChange = { operatorName = it },
            isSubmitting = state.isSubmitting,
            timeZone = timeZone,
            onStart = { viewModel.onEvent(SamplingUiEvent.StartStageWork(it.id, operatorName.trim())) },
            onRelease = { viewModel.onEvent(SamplingUiEvent.ReleaseStageWork(it.id)) },
            onFinish = { order ->
                when (val action = desk.finishAction()) {
                    is DeskFinishAction.Worksheet ->
                        viewModel.onEvent(SamplingUiEvent.OpenStageAdvanceDialog(order, action.target))
                    DeskFinishAction.Deposit -> setoranTarget = order
                    DeskFinishAction.QcInspection -> qcTarget = order
                    is DeskFinishAction.Handoff -> viewModel.onEvent(SamplingUiEvent.AdvanceStage(order.id, action.target))
                    null -> Unit
                }
            },
            onRework = { viewModel.onEvent(SamplingUiEvent.OpenReworkDialog(it)) },
            onOpenHistory = { showHistory = true },
            modifier = Modifier.weight(1f)
        )
    }

    // Lembar hasil turun mesin — dialog tahap yang sama dengan Kanban (satu sumber kebenaran).
    val target = state.stageAdvanceTarget
    val targetStage = state.stageAdvanceTargetStage
    if (target != null && targetStage != null) {
        StageAdvanceDialog(
            order = target,
            targetStage = targetStage,
            isSubmitting = state.isSubmitting,
            onConfirm = { sections ->
                viewModel.onEvent(SamplingUiEvent.ConfirmStageAdvance(target.id, targetStage, sections))
            },
            onDismiss = { viewModel.onEvent(SamplingUiEvent.CloseStageAdvanceDialog) }
        )
    }

    FinishingSetoranDialog(
        isOpen = setoranTarget != null,
        order = setoranTarget,
        isSubmitting = state.isSubmitting,
        onDismiss = { setoranTarget = null },
        onSubmit = { deposit ->
            setoranTarget?.let { viewModel.onEvent(SamplingUiEvent.AddFinishingDeposit(it.id, deposit)) }
            setoranTarget = null
        }
    )

    qcTarget?.let { order ->
        QcDeskInspectionDialog(
            order = state.orders.firstOrNull { it.id == order.id } ?: order,
            allOrders = state.orders,
            // Petugas QC dari sesi, bukan isian bebas: lembar QC adalah tanda tangan.
            inspectorName = persona?.name.orEmpty().ifBlank { operatorName },
            isSubmitting = state.isSubmitting,
            onDismiss = { qcTarget = null },
            onSubmit = { report ->
                viewModel.onEvent(SamplingUiEvent.SubmitQcInspection(order.id, report))
                qcTarget = null
            }
        )
    }

    state.reworkTarget?.let { order ->
        ReworkDialog(
            order = order,
            isSubmitting = state.isSubmitting,
            onDismiss = { viewModel.onEvent(SamplingUiEvent.CloseReworkDialog) },
            onConfirm = { reworkStage, reason, liability ->
                viewModel.onEvent(SamplingUiEvent.ConfirmRework(order.id, reworkStage, reason, liability))
            }
        )
    }

    if (showHistory) {
        StageDeskHistoryDialog(board = board, today = today, timeZone = timeZone, onDismiss = { showHistory = false })
    }
}

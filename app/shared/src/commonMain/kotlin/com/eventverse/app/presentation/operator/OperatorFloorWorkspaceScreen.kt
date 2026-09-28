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
import com.eventverse.app.domain.rbac.AccessSource
import com.eventverse.app.domain.rbac.TestingPersona
import com.eventverse.app.domain.sampling.SamplingOrder
import com.eventverse.app.domain.sampling.samplingRoute
import com.eventverse.app.domain.sampling.resolveAccessibleOperatorDesks
import com.eventverse.app.domain.sampling.toSamplingStageOrNull
import com.eventverse.app.domain.sampling.toStageCode
import com.eventverse.app.domain.stageflow.StageTrait
import com.eventverse.app.presentation.designsystem.ClayChoiceChip
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.ClayStatusBanner
import com.eventverse.app.presentation.sampling.SamplingUiEvent
import com.eventverse.app.presentation.sampling.SamplingViewModel
import com.eventverse.app.presentation.sampling.SampleStorageDialogHost
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
 * kerangka tahap pabrik (trait `OPERATOR_DESK`, TRD-FLOW-001), bukan didaftar tangan, supaya tahap
 * baru — dan template industri lain — ikut punya meja.
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
    val desks = remember(state.stageFlow) { state.stageFlow.filter { it.has(StageTrait.OPERATOR_DESK) } }
    // Meja yang boleh diakses persona. Batasannya disetel admin lewat penugasan modul di RBAC
    // (sumbu divisi); null berarti tanpa batasan — bypass owner/superadmin selalu membuka semua.
    val accessibleDesks = remember(decision, desks) {
        resolveAccessibleOperatorDesks(
            bypass = decision.source == AccessSource.OWNER_BYPASS ||
                decision.source == AccessSource.SUPERADMIN_BYPASS,
            departmentAccess = decision.fromDepartment,
            desks = desks
        )
    }
    val visibleDesks = remember(accessibleDesks, desks) {
        accessibleDesks?.let { allowed -> desks.filter { it.code in allowed } } ?: desks
    }

    if (visibleDesks.isEmpty()) {
        // Penugasan yang mengunci semua meja bukan layar kosong misterius — sebutkan pintunya.
        Column(modifier = modifier.fillMaxSize().padding(ClaySpacing.Md)) {
            ClayStatusBanner(
                message = "Divisi Anda belum diberi akses ke meja mana pun. " +
                    "Minta admin menambahkan meja pada penugasan modul Lantai Produksi di RBAC.",
                isError = true,
                onDismiss = { }
            )
        }
        return
    }

    var desk by remember(visibleDesks) { mutableStateOf(visibleDesks.first()) }
    // Dibaca dari sesi sebagai isian awal, tapi tetap bisa diganti: satu tablet lantai produksi
    // lazim dipakai bergantian beberapa operator dalam satu shift.
    var operatorName by remember(persona?.userId) { mutableStateOf(persona?.name.orEmpty()) }
    var setoranTarget by remember { mutableStateOf<SamplingOrder?>(null) }
    var qcTarget by remember { mutableStateOf<SamplingOrder?>(null) }
    var showHistory by remember { mutableStateOf(false) }

    val timeZone = remember { TimeZone.currentSystemDefault() }
    val today = remember(state.orders) { Clock.System.now().toLocalDateTime(timeZone).date }
    val board = remember(state.orders, desk, today) { buildOperatorDeskBoard(state.orders, desk, today, timeZone, state.stageFlow) }

    Column(modifier = modifier.fillMaxSize().padding(ClaySpacing.Md), verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)) {
        // Tab hanya berarti kalau ada pilihan: satu meja tunggal dirender langsung tanpa chip,
        // jadi tablet lantai tidak membuang satu baris hanya untuk nomor satu.
        if (visibleDesks.size > 1) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
            ) {
                visibleDesks.forEach { stage ->
                    val waiting = state.orders.queueCountAt(stage)
                    ClayChoiceChip(
                        text = if (waiting > 0) "${stage.deskLabel} ($waiting)" else stage.deskLabel,
                        selected = desk == stage,
                        onClick = { desk = stage }
                    )
                }
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
                when (val action = desk.finishAction(state.stageFlow, order.samplingRoute)) {
                    is DeskFinishAction.Worksheet ->
                        viewModel.onEvent(SamplingUiEvent.OpenStageAdvanceDialog(order, action.target))
                    DeskFinishAction.Deposit -> setoranTarget = order
                    DeskFinishAction.QcInspection -> qcTarget = order
                    is DeskFinishAction.Handoff -> viewModel.onEvent(SamplingUiEvent.AdvanceStage(order.id, action.target.code))
                    DeskFinishAction.Store -> viewModel.onEvent(SamplingUiEvent.OpenStoreDialog(order))
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
    // Lembar tahap hanya ada untuk tahap rajut (lembar per tahap kerangka lain = Tahap 3).
    val targetStage = state.stageAdvanceTargetStage?.toSamplingStageOrNull()
    if (target != null && targetStage != null) {
        StageAdvanceDialog(
            order = target,
            targetStage = targetStage,
            isSubmitting = state.isSubmitting,
            deskStage = desk.code,
            onConfirm = { sections ->
                viewModel.onEvent(SamplingUiEvent.ConfirmStageAdvance(target.id, targetStage.toStageCode(), sections))
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

    SampleStorageDialogHost(state = state, onEvent = viewModel::onEvent)
}

package com.eventverse.app.presentation.operator

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.eventverse.app.domain.rbac.AccessDecision
import com.eventverse.app.domain.rbac.TestingPersona
import com.eventverse.app.domain.sampling.SamplingPipelineStage
import com.eventverse.app.presentation.designsystem.ClayChoiceChip
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.finishing.FinishingOperatorWorkspaceScreen
import com.eventverse.app.presentation.sampling.SamplingUiEvent
import com.eventverse.app.presentation.sampling.SamplingViewModel
import com.eventverse.app.presentation.sampling.components.StageAdvanceDialog

private enum class OperatorDesk(val label: String) { KNITTING("Rajut"), FINISHING("Finishing & Linking") }

/**
 * Modul Lantai Produksi (`OPERATOR_EXEC`) — satu modul, beberapa meja operator.
 *
 * Kedua meja berbagi satu [SamplingViewModel] sehingga SPK yang diserahkan dari meja Rajut
 * langsung muncul di antrean Finishing tanpa memuat ulang.
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
    var desk by remember { mutableStateOf(OperatorDesk.KNITTING) }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = ClaySpacing.Md, vertical = ClaySpacing.Sm),
            horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
        ) {
            OperatorDesk.entries.forEach { entry ->
                ClayChoiceChip(text = entry.label, selected = desk == entry, onClick = { desk = entry })
            }
        }

        when (desk) {
            OperatorDesk.KNITTING -> KnittingOperatorDesk(
                queue = state.orders.filter { it.pipelineStage == SamplingPipelineStage.MACHINE_KNITTING },
                isSubmitting = state.isSubmitting,
                onFinishKnitting = { order ->
                    viewModel.onEvent(
                        SamplingUiEvent.OpenStageAdvanceDialog(order, SamplingPipelineStage.LINKING_ASSEMBLY)
                    )
                },
                modifier = Modifier.weight(1f).padding(ClaySpacing.Md)
            )
            OperatorDesk.FINISHING -> FinishingOperatorWorkspaceScreen(
                tenantSlug = tenantSlug,
                decision = decision,
                persona = persona,
                modifier = Modifier.weight(1f),
                viewModel = viewModel
            )
        }
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
}

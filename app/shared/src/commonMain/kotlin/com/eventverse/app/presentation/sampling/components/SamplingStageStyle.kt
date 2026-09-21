package com.eventverse.app.presentation.sampling.components

import androidx.compose.ui.graphics.Color
import com.eventverse.app.domain.sampling.SamplingPipelineStage
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Warna identitas tiap tahap pipeline sampling.
 *
 * Dipakai bersama oleh header kolom [SamplingPipelineKanbanBoard] dan badge tahap di
 * [SamplingDesktopWorkbench] agar satu tahap selalu berwarna sama di seluruh layar —
 * warna adalah pembeda state, bukan bentuk (design-system-rules Kontrak 8).
 * Token saja, tanpa literal `Color(0xFF...)`.
 */
fun samplingStageTint(stage: SamplingPipelineStage): Color = when (stage) {
    SamplingPipelineStage.NEW_INTAKE -> WeMadeColors.OnSurfaceMuted
    SamplingPipelineStage.FLOW_REVIEW -> WeMadeColors.Accent
    SamplingPipelineStage.CAM_PROGRAMMING -> WeMadeColors.Primary
    SamplingPipelineStage.MACHINE_KNITTING -> WeMadeColors.Warning
    SamplingPipelineStage.LINKING_ASSEMBLY -> WeMadeColors.Purple
    SamplingPipelineStage.FINISHING_QC -> WeMadeColors.Teal
    SamplingPipelineStage.IN_DELIVERY -> WeMadeColors.Info
    SamplingPipelineStage.ACC_APPROVED -> WeMadeColors.Success
}

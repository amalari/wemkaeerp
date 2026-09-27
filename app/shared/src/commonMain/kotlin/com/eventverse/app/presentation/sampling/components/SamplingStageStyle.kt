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
    // Keempat tahap penyelesaian akhir berbagi satu warna: mereka satu kolom di papan, dan
    // pembedanya adalah nama tahap di kartunya, bukan rona yang harus dihafal.
    SamplingPipelineStage.CUCI_SOFTENER -> WeMadeColors.Teal
    SamplingPipelineStage.SETRIKA_UAP -> WeMadeColors.Teal
    SamplingPipelineStage.QC_FINISHING -> WeMadeColors.Teal
    SamplingPipelineStage.PENGEMASAN -> WeMadeColors.Teal
    // Penyimpanan netral: barangnya diam menunggu, bukan sedang dikerjakan atau dikirim.
    SamplingPipelineStage.STORAGE_HOLDING -> WeMadeColors.OutlineSoft
    SamplingPipelineStage.IN_DELIVERY -> WeMadeColors.Info
    SamplingPipelineStage.ACC_APPROVED -> WeMadeColors.Success
}

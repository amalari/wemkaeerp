package com.eventverse.app.presentation.sampling.components

import androidx.compose.ui.graphics.Color
import com.eventverse.app.domain.sampling.SamplingPipelineStage
import com.eventverse.app.domain.sampling.toStageCode
import com.eventverse.app.domain.stageflow.IndustryStageTemplates
import com.eventverse.app.domain.stageflow.IndustryTemplateCode
import com.eventverse.app.presentation.theme.WeMadeColors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Warna tahap pindah dari token (`samplingStageTint`, dihapus di R3a) ke data tenant
 * (`StageDefinition.colorHex`, TRD-FLOW-001). Tabel token lama disimpan di sini sebagai rujukan:
 * kanban rajut tidak boleh berubah warna.
 */
class StageColorParityTest {

    private val knit = IndustryStageTemplates.stagesOf(IndustryTemplateCode.KNIT_SWEATER)

    @Test
    fun knitTemplateColorHex_shouldEqualLegacyStageTint() {
        SamplingPipelineStage.entries.forEach { legacy ->
            val stage = assertNotNull(knit.firstOrNull { it.code == legacy.toStageCode() })
            assertEquals(legacyStageTint(legacy), Color(stage.colorHex), "warna $legacy")
        }
    }

    /** Salinan `samplingStageTint` sebelum R3a (git HEAD 0f5c080). */
    private fun legacyStageTint(stage: SamplingPipelineStage): Color = when (stage) {
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
}

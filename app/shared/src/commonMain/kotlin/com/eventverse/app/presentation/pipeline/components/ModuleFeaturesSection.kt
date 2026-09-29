package com.eventverse.app.presentation.pipeline.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.eventverse.app.domain.pipeline.ModuleFeature
import com.eventverse.app.domain.pipeline.ModuleFeatureKind
import com.eventverse.app.domain.stageflow.StageCode
import com.eventverse.app.domain.stageflow.StageDefinition
import com.eventverse.app.domain.stageflow.StageKind
import com.eventverse.app.domain.workqueue.WorkStationSpec
import com.eventverse.app.presentation.designsystem.ClayFlowRow
import com.eventverse.app.presentation.designsystem.ClayShapes
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.ClayTag
import com.eventverse.app.presentation.designsystem.clayFlat
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Kanvas level 2 (TRD-FLOW-002 Fase 4): fitur di dalam modul yang dipilih, dari
 * [com.eventverse.app.domain.pipeline.ModuleFeatureRegistry]. Kerangka tahap digambar dari data
 * tenant, stasiun dari katalog lini — fitur baru yang didaftarkan otomatis muncul di sini.
 *
 * Kartu terang mandiri (latar + teks sendiri) supaya terbaca di mode presentasi tanpa ternary baru
 * (design-system-rules Kontrak 15).
 */
@Composable
fun ModuleFeaturesSection(
    features: List<ModuleFeature>,
    stageFlow: List<StageDefinition>,
    stageWip: Map<StageCode, Int> = emptyMap(),
    stations: List<WorkStationSpec>,
    modifier: Modifier = Modifier
) {
    if (features.isEmpty()) return
    Column(
        modifier = modifier.fillMaxWidth()
            .clayFlat(shape = ClayShapes.Chip, background = WeMadeColors.Background, outline = WeMadeColors.Border)
            .padding(ClaySpacing.Lg),
        verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
    ) {
        Text("Di dalam modul ini", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = WeMadeColors.OnSurface)
        features.forEach { feature ->
            Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
                    Text(feature.displayName, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = WeMadeColors.OnSurface,
                        modifier = Modifier.weight(1f, fill = false))
                    ClayTag(text = feature.kind.displayName, tint = WeMadeColors.Info)
                }
                Text(feature.description, fontSize = 11.sp, color = WeMadeColors.OnSurfaceMuted)
                when (feature.kind) {
                    ModuleFeatureKind.STAGE_FRAME -> ClayFlowRow {
                        stageFlow.filter { it.kind == StageKind.WORK }.forEachIndexed { i, stage ->
                            // Warna tahap = data tenant (design-system-rules Kontrak 1, pengecualian 1).
                            ClayTag(
                                text = "${i + 1}. ${stage.shortLabel}" + (stageWip[stage.code]?.let { " · $it SPK" } ?: ""),
                                tint = Color(stage.colorHex)
                            )
                        }
                    }
                    ModuleFeatureKind.STATION -> ClayFlowRow {
                        stations.forEach { ClayTag(text = it.displayName, tint = WeMadeColors.Teal) }
                    }
                    ModuleFeatureKind.OPTIONAL_PROCESS, ModuleFeatureKind.TOOL -> Unit
                }
            }
        }
    }
}

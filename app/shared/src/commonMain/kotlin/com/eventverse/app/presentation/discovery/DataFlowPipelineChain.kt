package com.eventverse.app.presentation.discovery

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.presentation.designsystem.ClayBadge
import com.eventverse.app.presentation.designsystem.ClayCard
import com.eventverse.app.presentation.designsystem.ClayChoiceChip
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.rememberClayTypography
import com.eventverse.app.presentation.theme.WeMadeColors

private enum class FlowViewMode {
    PIPELINE_CHAIN,
    PORT_MATRIX
}

/**
 * Peta Rantai Alur Nilai (Pipeline Flow Chain - Opsi 1):
 * Menggantikan tabel datar 11 baris dengan visualisasi rantai alur alami berurutan dari hulu ke hilir.
 * Mendukung inspeksi fokus per stasiun dan toggle ke mode matriks teknis.
 */
@Composable
internal fun DataFlowPipelineChain(
    map: DataFlowMap,
    draft: DiscoveryDraftUi,
    modifier: Modifier = Modifier
) {
    val typography = rememberClayTypography()
    val pipeline = remember(map) { map.pipelineOrder() }
    var viewMode by remember { mutableStateOf(FlowViewMode.PIPELINE_CHAIN) }
    var selectedModuleId by remember(pipeline) {
        mutableStateOf(pipeline.firstOrNull()?.module?.id.orEmpty())
    }

    val selectedFlow = remember(pipeline, selectedModuleId) {
        pipeline.firstOrNull { it.module.id == selectedModuleId } ?: pipeline.firstOrNull()
    }

    ClayCard(modifier = modifier.fillMaxWidth(), contentPadding = PaddingValues(ClaySpacing.Md)) {
        Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)) {
            // Header Peta Alur + Switch Tampilan (Rantai Alur vs Matriks Port)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
                    ) {
                        Text(
                            text = "Peta Rantai Alur Nilai (Value Stream)",
                            style = typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = WeMadeColors.OnSurface
                        )
                        ClayBadge(
                            text = "${pipeline.size} Stasiun Alur",
                            tint = WeMadeColors.Success,
                            dot = true,
                            fontSize = 10.sp
                        )
                    }
                    Text(
                        text = "Urutan serah-terima data stasiun produksi dari order masuk hingga produk jadi & pengiriman",
                        style = typography.bodySmall,
                        fontSize = 10.sp,
                        color = WeMadeColors.OnSurfaceMuted
                    )
                }

                // Toggle Tampilan: Rantai Alur (Default) vs Matriks Port
                Row(horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)) {
                    ClayChoiceChip(
                        text = "Rantai Alur",
                        selected = viewMode == FlowViewMode.PIPELINE_CHAIN,
                        tint = WeMadeColors.Primary,
                        fontSize = 10.sp,
                        onClick = { viewMode = FlowViewMode.PIPELINE_CHAIN }
                    )
                    ClayChoiceChip(
                        text = "Matriks Port",
                        selected = viewMode == FlowViewMode.PORT_MATRIX,
                        tint = WeMadeColors.Accent,
                        fontSize = 10.sp,
                        onClick = { viewMode = FlowViewMode.PORT_MATRIX }
                    )
                }
            }

            if (viewMode == FlowViewMode.PORT_MATRIX) {
                // Tampilan Matriks Teknis
                HandoffMatrixTable(map = map, draft = draft)
            } else {
                // Tampilan Opsi 1: Rantai Alur Horizontal Berurutan
                HorizontalPipelineRail(
                    pipeline = pipeline,
                    selectedModuleId = selectedModuleId,
                    onSelect = { selectedModuleId = it },
                    draft = draft
                )

                // Panel Inspeksi Stasiun Terpilih
                if (selectedFlow != null) {
                    val stageIndex = pipeline.indexOf(selectedFlow) + 1
                    StationInspectorCard(
                        flow = selectedFlow,
                        stageIndex = stageIndex,
                        totalStages = pipeline.size,
                        draft = draft
                    )
                }
            }
        }
    }
}

/** Rel horizontal stasiun alur yang dapat digulir bebas. */
@Composable
private fun HorizontalPipelineRail(
    pipeline: List<ModuleDataFlow>,
    selectedModuleId: String,
    onSelect: (String) -> Unit,
    draft: DiscoveryDraftUi
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = ClaySpacing.Xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        pipeline.forEachIndexed { index, flow ->
            val isSelected = flow.module.id == selectedModuleId
            val style = resolveSectionStyle(flow.module.section, draft)
            val clayRes = resolveClayAsset(flow.module)
            val iconRenderer = resolveModuleIcon(flow.module)

            // Kartu Node Stasiun
            StationNodeCard(
                step = index + 1,
                flow = flow,
                isSelected = isSelected,
                style = style,
                clayRes = clayRes,
                iconRenderer = iconRenderer,
                onClick = { onSelect(flow.module.id) }
            )

            // Kabel Penghubung (Transfer Conduit) ke stasiun berikutnya
            if (index < pipeline.lastIndex) {
                val nextFlow = pipeline[index + 1]
                val handoff = flow.outgoing.firstOrNull { it.to?.id == nextFlow.module.id }
                    ?: flow.outgoing.firstOrNull()

                StationConnectorBridge(
                    payload = handoff?.payloadLabel ?: "Aliran Alur",
                    isReference = handoff?.isReference == true
                )
            }
        }
    }
}

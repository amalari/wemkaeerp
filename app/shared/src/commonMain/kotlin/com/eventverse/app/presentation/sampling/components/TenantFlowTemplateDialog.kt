package com.eventverse.app.presentation.sampling.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.eventverse.app.domain.sampling.SamplingRoute
import com.eventverse.app.domain.stageflow.StageDefinition
import com.eventverse.app.presentation.designsystem.ClayCard
import com.eventverse.app.presentation.designsystem.ClayIconButton
import com.eventverse.app.presentation.designsystem.ClayShapes
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.IconClose
import com.eventverse.app.presentation.sampling.ProcessFlowViewModel
import com.eventverse.app.presentation.sampling.StageFlowEditorViewModel
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Template Alur Pabrik — default yang diwarisi setiap desain baru: proses sisipan dan tag fase
 * `[Sampling ×] [Produksi ×]` pada Cuci & Setrika.
 *
 * Memakai [ProcessFlowViewModel] sendiri (lingkup bawaan `DefaultTenant`), bukan milik dialog
 * detail SPK, supaya membuka template tidak menggeser lingkup panel desain yang sedang diatur.
 */
@Composable
fun TenantFlowTemplateDialog(
    onDismiss: () -> Unit,
    /** Kerangka pabrik yang disisipi template alur (TRD-FLOW-001). */
    stageFlow: List<StageDefinition> = SamplingRoute.DEFAULT_STAGES,
    onStageFlowChanged: (List<StageDefinition>) -> Unit = {}
) {
    val viewModel = remember { ProcessFlowViewModel() }
    val scope = rememberCoroutineScope()
    val editor = remember { StageFlowEditorViewModel(scope = scope, onChanged = onStageFlowChanged) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        ClayCard(
            modifier = Modifier.widthIn(max = 1100.dp).fillMaxWidth(0.95f),
            shape = ClayShapes.Panel,
            contentPadding = PaddingValues(ClaySpacing.Lg)
        ) {
            Column(
                modifier = Modifier.heightIn(max = 760.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f, fill = false)) {
                        Text("TEMPLATE ALUR PABRIK", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = WeMadeColors.OnSurface)
                        Text(
                            text = "Default untuk desain baru: kerangka tahap, proses sisipan, dan tag fase. " +
                                "SPK yang sudah masuk lantai produksi tidak ikut berubah.",
                            fontSize = 11.sp,
                            color = WeMadeColors.OnSurfaceMuted
                        )
                    }
                    ClayIconButton(onClick = onDismiss, shape = ClayShapes.Tile) {
                        IconClose(modifier = Modifier.size(16.dp))
                    }
                }
                StageFlowEditorPanel(viewModel = editor)
                ProcessFlowAdjusterPanel(viewModel = viewModel, hideScopeSelector = true, frame = stageFlow)
            }
        }
    }
}

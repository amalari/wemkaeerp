package com.eventverse.app.presentation.pipeline

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.presentation.pipeline.components.ExecutiveKpiHeader
import com.eventverse.app.presentation.pipeline.components.NodeInputInspectorModal
import com.eventverse.app.presentation.pipeline.components.NodeInspectorDrawer
import com.eventverse.app.presentation.pipeline.components.PipelineFlowCanvas
import com.eventverse.app.presentation.pipeline.components.PresetSelectorBar
import com.eventverse.app.presentation.theme.WeMadeColors

@Composable
fun FactoryFlowScreen(
    viewModel: FactoryFlowViewModel = remember { FactoryFlowViewModel() },
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val isPresentationMode = state.isPresentationMode

    val screenBg = if (isPresentationMode) Color(0xFF020617) else WeMadeColors.Background

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(screenBg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Presentation Mode Banner (If active)
            AnimatedVisibility(visible = isPresentationMode) {
                ExecutivePresentationBanner(
                    onExit = { viewModel.onEvent(FactoryFlowUiEvent.TogglePresentationMode) }
                )
            }

            // Top Header & Title
            if (!isPresentationMode) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Alur Operasional & Monitoring Pabrik",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = WeMadeColors.OnSurface
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(WeMadeColors.PrimaryContainer)
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "Live Monitoring Pipeline",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = WeMadeColors.Primary
                                )
                            }
                        }

                        Text(
                            text = "Visualisasi alur kerja modul dari order hingga pengiriman, dilengkapi deteksi bottleneck dan kontrak data antar divisi.",
                            fontSize = 13.sp,
                            color = WeMadeColors.OnSurfaceMuted
                        )
                    }
                }
            }

            // Preset Selector Bar (FOB vs CMT vs Brand D2C + Presentation Button)
            PresetSelectorBar(
                selectedPreset = state.selectedPreset,
                isPresentationMode = isPresentationMode,
                isSimulating = state.isSimulatingRealtime,
                onSelectPreset = { viewModel.onEvent(FactoryFlowUiEvent.SelectPreset(it)) },
                onTogglePresentationMode = { viewModel.onEvent(FactoryFlowUiEvent.TogglePresentationMode) },
                onToggleSimulation = { viewModel.onEvent(FactoryFlowUiEvent.ToggleSimulation) }
            )

            // Executive KPI Cards Ribbon
            ExecutiveKpiHeader(
                snapshot = state.snapshot,
                modifier = Modifier.fillMaxWidth()
            )

            // Main Interactive Flow Canvas
            PipelineFlowCanvas(
                nodes = state.filteredNodes,
                selectedNode = state.selectedNode,
                selectedStageFilter = state.selectedStageFilter,
                searchQuery = state.searchQuery,
                viewMode = state.viewMode,
                isPresentationMode = isPresentationMode,
                hideBypassedNodes = state.hideBypassedNodes,
                bypassedCount = state.bypassedCount,
                onSelectNode = { viewModel.onEvent(FactoryFlowUiEvent.SelectNode(it)) },
                onInspectInputs = { viewModel.onEvent(FactoryFlowUiEvent.InspectNodeInputs(it)) },
                onFilterStage = { viewModel.onEvent(FactoryFlowUiEvent.FilterByStage(it)) },
                onSearchChange = { viewModel.onEvent(FactoryFlowUiEvent.UpdateSearchQuery(it)) },
                onSetViewMode = { viewModel.onEvent(FactoryFlowUiEvent.SetViewMode(it)) },
                onToggleHideBypassed = { viewModel.onEvent(FactoryFlowUiEvent.ToggleHideBypassed) },
                onResetFilters = { viewModel.onEvent(FactoryFlowUiEvent.ResetFilters) },
                modifier = Modifier.weight(1f)
            )

        }

        // Side Sheet Inspector (Drawer) for Selected Node
        AnimatedVisibility(
            visible = state.selectedNode != null,
            enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            NodeInspectorDrawer(
                node = state.selectedNode,
                isPresentationMode = isPresentationMode,
                onClose = { viewModel.onEvent(FactoryFlowUiEvent.SelectNode(null)) }
            )
        }

        // n8n-Style Node Input & Upstream Mapping Inspector Modal
        AnimatedVisibility(
            visible = state.inspectingInputNode != null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            state.inspectingInputNode?.let { node ->
                NodeInputInspectorModal(
                    node = node,
                    isPresentationMode = isPresentationMode,
                    onClose = { viewModel.onEvent(FactoryFlowUiEvent.InspectNodeInputs(null)) }
                )
            }
        }
    }
}

@Composable
private fun ExecutivePresentationBanner(
    onExit: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF1E293B))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            com.eventverse.app.presentation.pipeline.components.IconPresentation(modifier = Modifier.size(18.dp), color = Color.White)
            Column {
                Text(
                    text = "MODE PRESENTASI KLIEN & DEMO EKSEKUTIF AKTIF",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    letterSpacing = 0.5.sp
                )
                Text(
                    text = "Klik salah satu kartu modul di diagram alur untuk mendemonstrasikan kejelasan kontrak data antar divisi.",
                    fontSize = 11.sp,
                    color = Color(0xFF94A3B8)
                )
            }
        }

        Button(
            onClick = onExit,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
            shape = RoundedCornerShape(6.dp),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
            modifier = Modifier.height(30.dp)
        ) {
            Text("✕ Tutup Presentasi", fontSize = 11.sp, color = Color.White)
        }
    }
}

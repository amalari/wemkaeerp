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
import com.eventverse.app.presentation.pipeline.components.IconFlowGraph
import com.eventverse.app.presentation.pipeline.components.NodeInputInspectorModal
import com.eventverse.app.presentation.pipeline.components.NodeInspectorDrawer
import com.eventverse.app.presentation.pipeline.components.PipelineFlowCanvas
import com.eventverse.app.presentation.theme.WeMadeColors

@Composable
fun FactoryFlowScreen(
    tenantSlug: String = "wemade-demo",
    viewModel: FactoryFlowViewModel = remember { FactoryFlowViewModel() },
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val isPresentationMode = state.isPresentationMode

    val activeCompany = remember(tenantSlug) {
        com.eventverse.app.presentation.navigation.CompanyTenantProfile.findBySlug(tenantSlug)
    }

    // Sync pipeline automatically with the active company tenant selected in GCP switcher
    LaunchedEffect(tenantSlug) {
        viewModel.onEvent(FactoryFlowUiEvent.SelectPreset(activeCompany.preset))
    }

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

            // Top Header: Title + Presentation Mode Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(WeMadeColors.Primary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        IconFlowGraph(
                            modifier = Modifier.size(24.dp),
                            color = WeMadeColors.Primary
                        )
                    }

                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Alur Operasional Pabrik",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = WeMadeColors.OnSurface
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(WeMadeColors.Primary.copy(alpha = 0.12f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = activeCompany.name,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
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

            // Main Interactive Flow Canvas
            PipelineFlowCanvas(
                nodes = state.filteredNodes,
                selectedNode = state.selectedNode,
                selectedStageFilter = state.selectedStageFilter,
                isPresentationMode = isPresentationMode,
                hideBypassedNodes = state.hideBypassedNodes,
                onSelectNode = { viewModel.onEvent(FactoryFlowUiEvent.SelectNode(it)) },
                onInspectInputs = { viewModel.onEvent(FactoryFlowUiEvent.InspectNodeInputs(it)) },
                onFilterStage = { viewModel.onEvent(FactoryFlowUiEvent.FilterByStage(it)) },
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
            com.eventverse.app.presentation.pipeline.components.IconPresentation(
                modifier = Modifier.size(18.dp),
                color = Color.White
            )
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

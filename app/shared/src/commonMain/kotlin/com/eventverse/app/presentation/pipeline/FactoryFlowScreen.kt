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
import com.eventverse.app.presentation.designsystem.ClayBorder
import com.eventverse.app.presentation.designsystem.ClayButton
import com.eventverse.app.presentation.designsystem.ClayButtonStyle
import com.eventverse.app.presentation.designsystem.ClayOffset
import com.eventverse.app.presentation.designsystem.ClayShapes
import com.eventverse.app.presentation.designsystem.ClayTag
import com.eventverse.app.presentation.designsystem.clayFlat
import com.eventverse.app.presentation.designsystem.claySurface
import com.eventverse.app.presentation.pipeline.components.IconFlowGraph
import com.eventverse.app.presentation.pipeline.components.NodeInputInspectorModal
import com.eventverse.app.presentation.pipeline.components.NodeInspectorDrawer
import com.eventverse.app.presentation.pipeline.components.PipelineFlowCanvas
import com.eventverse.app.presentation.pipeline.components.RenameModuleDialog
import com.eventverse.app.presentation.pipeline.components.TenantModuleActionBar
import com.eventverse.app.presentation.pipeline.components.TenantModulePanel
import com.eventverse.app.presentation.pipeline.components.TenantPipelineStatusBanner
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

    // Load the tenant's persisted topology whenever the active company changes. The canvas
    // renders what this tenant actually has in the database, not a hardcoded preset.
    LaunchedEffect(tenantSlug) {
        viewModel.onEvent(FactoryFlowUiEvent.LoadTenantPipeline(tenantSlug))
    }

    val screenBg = if (isPresentationMode) WeMadeColors.BackgroundDark else WeMadeColors.BackgroundWarm

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
                            .clayFlat(
                                shape = ClayShapes.Tile,
                                background = WeMadeColors.PrimaryContainer,
                                outline = WeMadeColors.Outline,
                                borderWidth = ClayBorder.Medium
                            ),
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
                            ClayTag(
                                text = activeCompany.name,
                                tint = WeMadeColors.Primary,
                                fontSize = 11.sp
                            )
                        }

                        Text(
                            text = state.pipelineName
                                ?: "Visualisasi alur kerja modul dari order hingga pengiriman, dilengkapi deteksi bottleneck dan kontrak data antar divisi.",
                            fontSize = 13.sp,
                            color = WeMadeColors.OnSurfaceMuted
                        )
                    }
                }

                // Entry point to per-tenant module provisioning.
                TenantModuleActionBar(
                    state = state,
                    onToggleModulePanel = { viewModel.onEvent(FactoryFlowUiEvent.ToggleModulePanel) }
                )
            }

            // Data provenance / progress / failure feedback
            TenantPipelineStatusBanner(
                state = state,
                onRetry = { viewModel.onEvent(FactoryFlowUiEvent.Retry(tenantSlug)) },
                onDismiss = { viewModel.onEvent(FactoryFlowUiEvent.DismissStatusMessage) }
            )

            // Per-tenant module provisioning panel
            AnimatedVisibility(visible = state.isModulePanelVisible) {
                TenantModulePanel(
                    catalog = state.moduleCatalog,
                    isSaving = state.isSaving,
                    onSetModuleActive = { moduleId, isActive ->
                        viewModel.onEvent(
                            FactoryFlowUiEvent.SetModuleActive(tenantSlug, moduleId, isActive)
                        )
                    },
                    onResetToPreset = { preset ->
                        viewModel.onEvent(FactoryFlowUiEvent.ResetToPreset(tenantSlug, preset))
                    },
                    activePreset = state.selectedPreset
                )
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
                onClose = { viewModel.onEvent(FactoryFlowUiEvent.SelectNode(null)) },
                // Renaming writes to persisted tenant data, so it is only offered when the
                // canvas is showing that data rather than the preset template.
                onRenameRequest = state.selectedNode
                    ?.takeIf { state.isTenantDataLoaded }
                    ?.let { node ->
                        { viewModel.onEvent(FactoryFlowUiEvent.StartRenamingModule(node)) }
                    }
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

        // Per-tenant module renaming
        state.renamingNode?.let { node ->
            RenameModuleDialog(
                node = node,
                isSaving = state.isSaving,
                onConfirm = { newName ->
                    viewModel.onEvent(
                        FactoryFlowUiEvent.RenameModule(tenantSlug, node.id, newName)
                    )
                },
                onDismiss = { viewModel.onEvent(FactoryFlowUiEvent.StartRenamingModule(null)) }
            )
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
            .claySurface(
                shape = ClayShapes.Card,
                background = WeMadeColors.SurfaceDarkElevated,
                outline = WeMadeColors.BackgroundDark,
                offset = ClayOffset.Small
            )
            .padding(horizontal = 16.dp, vertical = 11.dp),
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

        ClayButton(
            text = "✕ Tutup Presentasi",
            onClick = onExit,
            style = ClayButtonStyle.Secondary,
            fontSize = 11.sp,
            offset = ClayOffset.Pressed,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

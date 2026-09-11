package com.eventverse.app.presentation.pipeline.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.domain.pipeline.PipelineGraph
import com.eventverse.app.domain.pipeline.PipelineNode
import com.eventverse.app.domain.pipeline.PipelineStage
import com.eventverse.app.presentation.designsystem.ClayBorder
import com.eventverse.app.presentation.designsystem.ClayCard
import com.eventverse.app.presentation.designsystem.ClayButton
import com.eventverse.app.presentation.designsystem.ClayOffset
import com.eventverse.app.presentation.designsystem.ClayShapes
import com.eventverse.app.presentation.designsystem.ClayTag
import com.eventverse.app.presentation.designsystem.clayFlat
import com.eventverse.app.presentation.designsystem.claySurface
import com.eventverse.app.presentation.theme.WeMadeColors

@Composable
fun PipelineFlowCanvas(
    nodes: List<PipelineNode>,
    selectedNode: PipelineNode?,
    selectedStageFilter: PipelineStage?,
    isPresentationMode: Boolean,
    hideBypassedNodes: Boolean = true,
    onSelectNode: (PipelineNode) -> Unit,
    onInspectInputs: (PipelineNode) -> Unit = {},
    onFilterStage: (PipelineStage?) -> Unit,
    onResetFilters: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Macro Process Flow Ribbon (Left-to-Right Progress Stepper)
        MacroProcessStepper(
            nodes = nodes,
            selectedStageFilter = selectedStageFilter,
            isPresentationMode = isPresentationMode,
            onStageClick = { stage ->
                if (selectedStageFilter == stage) onFilterStage(null)
                else onFilterStage(stage)
            }
        )

        // Main Content Area
        if (nodes.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 40.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        IconSearch(
                            modifier = Modifier.size(16.dp),
                            color = if (isPresentationMode) WeMadeColors.OnSurfaceMutedInverse else WeMadeColors.OnSurfaceMuted
                        )
                        Text(
                            text = "Tidak ada modul yang cocok dengan pencarian",
                            color = if (isPresentationMode) WeMadeColors.OnSurfaceMutedInverse else WeMadeColors.OnSurfaceMuted
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    ClayButton(
                        text = "Reset Filter & Pencarian",
                        onClick = onResetFilters
                    )
                }
            }
        } else {
            // Multi-Column Swimlane Layout (Horizontal Left-to-Right Stages)
            HorizontalSwimlaneLayout(
                nodes = nodes,
                selectedNode = selectedNode,
                isPresentationMode = isPresentationMode,
                hideBypassedNodes = hideBypassedNodes,
                onSelectNode = onSelectNode,
                onInspectInputs = onInspectInputs
            )
        }
    }
}

/**
 * Visual Macro Stepper showing the 5 progressive stages from Left to Right
 */
@Composable
private fun MacroProcessStepper(
    nodes: List<PipelineNode>,
    selectedStageFilter: PipelineStage?,
    isPresentationMode: Boolean,
    onStageClick: (PipelineStage) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clayFlat(
                shape = ClayShapes.Card,
                background = if (isPresentationMode) WeMadeColors.SurfaceDark else WeMadeColors.SurfaceMuted,
                outline = if (isPresentationMode) WeMadeColors.OutlineInverse else WeMadeColors.Border,
                borderWidth = ClayBorder.Medium
            )
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        PipelineStage.entries.forEachIndexed { index, stage ->
            val stageNodes = nodes.filter { it.stage == stage }
            val totalWipInStage = stageNodes.sumOf { it.wipPieces }
            val hasBottleneck = stageNodes.any { it.isBottleneck }
            val isSelected = selectedStageFilter == stage
            val isStageBypassed = stageNodes.isEmpty()

            // Stage Step Pill
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    // Pil tahapan ikut bahasa clay: outline selalu setebal 2dp, hanya warnanya
                    // yang berubah saat terpilih. Sebelumnya border-nya dihilangkan (0.dp) saat
                    // terpilih, yang membuat pil aktif menciut setengah piksel.
                    .claySurface(
                        shape = ClayShapes.Button,
                        background = when {
                            isSelected -> Color(stage.colorHex)
                            isPresentationMode -> WeMadeColors.SurfaceDarkElevated
                            else -> Color.White
                        },
                        outline = if (isSelected) WeMadeColors.Outline
                        else Color(stage.colorHex).copy(alpha = 0.4f),
                        offset = ClayOffset.Pressed,
                        pressed = isSelected,
                        borderWidth = ClayBorder.Medium,
                        innerShade = false
                    )
                    .clickable { onStageClick(stage) }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                // Circle Step Number
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) Color.White else Color(stage.colorHex)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${stage.stepOrder}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) Color(stage.colorHex) else Color.White
                    )
                }

                Column {
                    Text(
                        text = stage.displayName.substringAfter(". "),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            isSelected -> Color.White
                            isPresentationMode -> Color.White
                            else -> WeMadeColors.OnSurface
                        }
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = if (isStageBypassed) "Di-bypass (Buyer)" else "${stageNodes.size} Modul",
                            fontSize = 10.sp,
                            color = if (isSelected) Color.White.copy(alpha = 0.85f) else WeMadeColors.OnSurfaceMuted
                        )
                        if (!isStageBypassed) {
                            Text(
                                text = "•",
                                fontSize = 9.sp,
                                color = if (isSelected) Color.White.copy(alpha = 0.85f) else WeMadeColors.OnSurfaceMuted
                            )
                            if (hasBottleneck) {
                                IconWarning(
                                    modifier = Modifier.size(10.dp),
                                    color = if (isSelected) Color.White else WeMadeColors.Warning
                                )
                            }
                            Text(
                                text = "$totalWipInStage Pcs",
                                fontSize = 10.sp,
                                fontWeight = if (hasBottleneck) FontWeight.Bold else FontWeight.Medium,
                                color = when {
                                    isSelected -> Color.White
                                    hasBottleneck -> WeMadeColors.Warning
                                    else -> WeMadeColors.Success
                                }
                            )
                        }
                    }
                }
            }

            // Directional Chevron Arrow between Stages
            if (index < PipelineStage.entries.size - 1) {
                IconChevronRight(
                    modifier = Modifier.size(14.dp),
                    color = if (isPresentationMode) WeMadeColors.OnSurfaceMuted else WeMadeColors.Primary.copy(alpha = 0.6f)
                )
            }
        }
    }
}

/**
 * The Hero UI/UX Pro Layout: Horizontal Swimlane Stages (Left-to-Right Stage Columns)
 */
@Composable
private fun HorizontalSwimlaneLayout(
    nodes: List<PipelineNode>,
    selectedNode: PipelineNode?,
    isPresentationMode: Boolean,
    hideBypassedNodes: Boolean,
    onSelectNode: (PipelineNode) -> Unit,
    onInspectInputs: (PipelineNode) -> Unit,
    modifier: Modifier = Modifier
) {
    val groupedByStage = nodes.groupBy { it.stage }
    val visibleStages = if (hideBypassedNodes) {
        PipelineStage.entries.filter { stage -> (groupedByStage[stage] ?: emptyList()).isNotEmpty() }
    } else {
        PipelineStage.entries
    }

    val scrollState = rememberScrollState()
    val verticalScrollState = rememberScrollState()

    // Real connectors, resolved from the module data — the same graph the node canvas uses.
    val graph = remember(nodes) { PipelineGraph.from(nodes) }
    val bounds = remember { SwimlaneBounds() }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(verticalScrollState)
            .horizontalScroll(scrollState)
            .padding(vertical = 4.dp)
    ) {
        Box(modifier = Modifier.swimlaneRoot(bounds)) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    // Wide enough for multi-lane vertical corridor connectors between columns to breathe freely.
                    horizontalArrangement = Arrangement.spacedBy(80.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    visibleStages.forEach { stage ->
                        StageSwimlaneColumn(
                            stage = stage,
                            nodes = groupedByStage[stage] ?: emptyList(),
                            selectedNode = selectedNode,
                            isPresentationMode = isPresentationMode,
                            bounds = bounds,
                            onSelectNode = onSelectNode,
                            onInspectInputs = onInspectInputs,
                            // Dinaikkan dari 305dp: outline 3dp + hard shadow 6dp pada setiap
                            // kartu node menambah ~18dp lebar yang sebelumnya tidak ada.
                            modifier = Modifier.width(324.dp)
                        )
                    }
                }

                // Reserved routing corridor for stage skips and feedback loops.
                Spacer(modifier = Modifier.height(SWIMLANE_CORRIDOR_HEIGHT))
            }

            // Drawn last so connectors stay visible over the stage columns' own background;
            // the routing keeps them off the node cards themselves. A plain Canvas takes no
            // pointer input, so cards underneath stay clickable.
            SwimlaneConnectionCanvas(
                graph = graph,
                bounds = bounds,
                selectedNodeId = selectedNode?.id,
                isPresentationMode = isPresentationMode,
                modifier = Modifier.matchParentSize()
            )
        }
    }
}

@Composable
private fun StageSwimlaneColumn(
    stage: PipelineStage,
    nodes: List<PipelineNode>,
    selectedNode: PipelineNode?,
    isPresentationMode: Boolean,
    bounds: SwimlaneBounds,
    onSelectNode: (PipelineNode) -> Unit,
    onInspectInputs: (PipelineNode) -> Unit,
    modifier: Modifier = Modifier
) {
    ClayCard(
        modifier = modifier,
        shape = ClayShapes.Panel,
        containerColor = if (isPresentationMode) WeMadeColors.SurfaceDark else WeMadeColors.Background,
        outlineColor = if (isPresentationMode) WeMadeColors.OutlineInverse else WeMadeColors.OutlineSoft,
        // Kolom swimlane adalah wadah, bukan objek yang bisa disentuh — bayangannya dibuat tipis
        // supaya kartu node di dalamnya tetap menjadi lapisan yang paling menonjol.
        offset = ClayOffset.Small,
        innerShade = false,
        contentPadding = PaddingValues(12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Stage Column Header
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clayFlat(
                        shape = ClayShapes.Chip,
                        background = Color(stage.colorHex)
                            .copy(alpha = if (isPresentationMode) 0.25f else 0.12f),
                        outline = Color(stage.colorHex).copy(alpha = 0.45f),
                        borderWidth = ClayBorder.Medium
                    )
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(Color(stage.colorHex)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${stage.stepOrder}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        Column {
                            Text(
                                text = stage.displayName.substringAfter(". "),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isPresentationMode) Color.White else Color(stage.colorHex)
                            )
                            Text(
                                text = stage.subtitle,
                                fontSize = 10.sp,
                                color = if (isPresentationMode) WeMadeColors.OnSurfaceMutedInverse else WeMadeColors.OnSurfaceMuted,
                                maxLines = 1
                            )
                        }
                    }

                    ClayTag(
                        text = "${nodes.size}",
                        tint = if (isPresentationMode) Color.White else Color(stage.colorHex),
                        fontSize = 11.sp
                    )
                }

            }

            // Stacked Nodes within this Stage Column
            if (nodes.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .clayFlat(
                            shape = ClayShapes.Card,
                            background = if (isPresentationMode) WeMadeColors.SurfaceDarkElevated
                            else WeMadeColors.SurfaceMuted,
                            outline = WeMadeColors.Border,
                            borderWidth = ClayBorder.Medium
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Tahapan ini di-bypass",
                        fontSize = 11.sp,
                        color = WeMadeColors.OnSurfaceMuted
                    )
                }
            } else {
                // Spaced apart so the real vertical connector between them has room to draw.
                nodes.forEach { node ->
                    PipelineNodeCard(
                        node = node,
                        isSelected = selectedNode?.id == node.id,
                        isPresentationMode = isPresentationMode,
                        onClick = { onSelectNode(node) },
                        onInspectInputs = onInspectInputs,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 18.dp)
                            .swimlaneCard(node.id, bounds)
                    )
                }
            }

        }
    }
}


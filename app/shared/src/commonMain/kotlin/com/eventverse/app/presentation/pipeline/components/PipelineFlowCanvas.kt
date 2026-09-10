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
                            color = if (isPresentationMode) Color(0xFF94A3B8) else WeMadeColors.OnSurfaceMuted
                        )
                        Text(
                            text = "Tidak ada modul yang cocok dengan pencarian",
                            color = if (isPresentationMode) Color(0xFF94A3B8) else WeMadeColors.OnSurfaceMuted
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = onResetFilters) {
                        Text("Reset Filter & Pencarian")
                    }
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
            .clip(RoundedCornerShape(10.dp))
            .background(if (isPresentationMode) Color(0xFF0F172A) else Color(0xFFF1F5F9))
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
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        when {
                            isSelected -> Color(stage.colorHex)
                            isPresentationMode -> Color(0xFF1E293B)
                            else -> Color.White
                        }
                    )
                    .border(
                        width = if (isSelected) 0.dp else 1.dp,
                        color = if (isSelected) Color.Transparent else Color(stage.colorHex).copy(alpha = 0.3f),
                        shape = RoundedCornerShape(8.dp)
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
                    color = if (isPresentationMode) Color(0xFF64748B) else WeMadeColors.Primary.copy(alpha = 0.6f)
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
                            modifier = Modifier.width(305.dp)
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
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isPresentationMode) Color(0xFF0F172A) else Color(0xFFF8FAFC)
        ),
        border = BorderStroke(
            1.dp,
            if (isPresentationMode) Color(0xFF334155) else WeMadeColors.Border
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Stage Column Header
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(stage.colorHex).copy(alpha = if (isPresentationMode) 0.25f else 0.12f))
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
                                color = if (isPresentationMode) Color(0xFF94A3B8) else WeMadeColors.OnSurfaceMuted,
                                maxLines = 1
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(stage.colorHex).copy(alpha = 0.2f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "${nodes.size}",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isPresentationMode) Color.White else Color(stage.colorHex)
                        )
                    }
                }

            }

            // Stacked Nodes within this Stage Column
            if (nodes.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isPresentationMode) Color(0xFF1E293B) else Color(0xFFF1F5F9)),
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


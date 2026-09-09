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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.domain.pipeline.PipelineNode
import com.eventverse.app.domain.pipeline.PipelineStage
import com.eventverse.app.presentation.pipeline.PipelineViewMode
import com.eventverse.app.presentation.theme.WeMadeColors

@Composable
fun PipelineFlowCanvas(
    nodes: List<PipelineNode>,
    selectedNode: PipelineNode?,
    selectedStageFilter: PipelineStage?,
    searchQuery: String,
    viewMode: PipelineViewMode,
    isPresentationMode: Boolean,
    hideBypassedNodes: Boolean,
    bypassedCount: Int,
    onSelectNode: (PipelineNode) -> Unit,
    onFilterStage: (PipelineStage?) -> Unit,
    onSearchChange: (String) -> Unit,
    onSetViewMode: (PipelineViewMode) -> Unit,
    onToggleHideBypassed: () -> Unit,
    onResetFilters: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Control Bar: View Mode Switcher + Bypass Filter + Search Field
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: View Mode Switcher Pills & Hide Bypassed Toggle
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Layout:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isPresentationMode) Color(0xFF94A3B8) else WeMadeColors.OnSurfaceMuted
                )

                PipelineViewMode.entries.forEach { mode ->
                    val isSelected = viewMode == mode
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                when {
                                    isSelected && isPresentationMode -> WeMadeColors.Primary
                                    isSelected -> WeMadeColors.PrimaryContainer
                                    isPresentationMode -> Color(0xFF1E293B)
                                    else -> Color(0xFFF1F5F9)
                                }
                            )
                            .border(
                                width = if (isSelected) 1.5.dp else 0.dp,
                                color = if (isSelected) WeMadeColors.Primary else Color.Transparent,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable { onSetViewMode(mode) }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val iconColor = when {
                                isSelected && isPresentationMode -> Color.White
                                isSelected -> WeMadeColors.Primary
                                isPresentationMode -> Color(0xFFCBD5E1)
                                else -> WeMadeColors.OnSurface
                            }
                            when (mode) {
                                PipelineViewMode.SWIMLANE -> IconSwimlane(modifier = Modifier.size(13.dp), color = iconColor)
                                PipelineViewMode.FLOW_GRAPH -> IconFlowGraph(modifier = Modifier.size(13.dp), color = iconColor)
                                PipelineViewMode.VERTICAL_LIST -> IconList(modifier = Modifier.size(13.dp), color = iconColor)
                            }
                            Text(
                                text = mode.displayName,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = iconColor
                            )
                        }
                    }
                }

                // Toggle Hide Bypassed Modules
                if (bypassedCount > 0) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                when {
                                    hideBypassedNodes && isPresentationMode -> Color(0xFF1E293B)
                                    hideBypassedNodes -> WeMadeColors.PrimaryContainer.copy(alpha = 0.7f)
                                    isPresentationMode -> Color(0xFF0F172A)
                                    else -> Color(0xFFF8FAFC)
                                }
                            )
                            .border(
                                width = 1.dp,
                                color = if (hideBypassedNodes) WeMadeColors.Primary else if (isPresentationMode) Color(0xFF334155) else WeMadeColors.Border,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable { onToggleHideBypassed() }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (hideBypassedNodes) {
                                IconEyeOff(
                                    modifier = Modifier.size(13.dp),
                                    color = WeMadeColors.Primary
                                )
                            } else {
                                IconEye(
                                    modifier = Modifier.size(13.dp),
                                    color = if (isPresentationMode) Color(0xFF94A3B8) else WeMadeColors.OnSurfaceMuted
                                )
                            }
                            Text(
                                text = if (hideBypassedNodes) "Bypass Disembunyikan ($bypassedCount)" else "Sembunyikan Bypass ($bypassedCount)",
                                fontSize = 11.sp,
                                fontWeight = if (hideBypassedNodes) FontWeight.Bold else FontWeight.Medium,
                                color = if (hideBypassedNodes) WeMadeColors.Primary else if (isPresentationMode) Color(0xFFCBD5E1) else WeMadeColors.OnSurface
                            )
                        }
                    }
                }
            }

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                placeholder = { Text("Cari modul, divisi, atau kontrak data...", fontSize = 12.sp) },
                singleLine = true,
                modifier = Modifier
                    .width(300.dp)
                    .height(42.dp),
                shape = RoundedCornerShape(8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = WeMadeColors.Primary,
                    unfocusedBorderColor = if (isPresentationMode) Color(0xFF334155) else WeMadeColors.Border,
                    focusedTextColor = if (isPresentationMode) Color.White else WeMadeColors.OnSurface,
                    unfocusedTextColor = if (isPresentationMode) Color.White else WeMadeColors.OnSurface
                )
            )
        }


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

        // Main Content Area based on View Mode
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
            when (viewMode) {
                PipelineViewMode.SWIMLANE -> {
                    // Modern Multi-Column Swimlane Layout (Horizontal Left-to-Right Stages)
                    HorizontalSwimlaneLayout(
                        nodes = nodes,
                        selectedNode = selectedNode,
                        isPresentationMode = isPresentationMode,
                        hideBypassedNodes = hideBypassedNodes,
                        onSelectNode = onSelectNode
                    )
                }
                PipelineViewMode.FLOW_GRAPH -> {
                    // Linear Sequential Flow Canvas
                    LinearFlowGraphLayout(
                        nodes = nodes,
                        selectedNode = selectedNode,
                        isPresentationMode = isPresentationMode,
                        onSelectNode = onSelectNode
                    )
                }
                PipelineViewMode.VERTICAL_LIST -> {
                    // Expanded Table/List Layout
                    VerticalListLayout(
                        nodes = nodes,
                        selectedNode = selectedNode,
                        isPresentationMode = isPresentationMode,
                        onSelectNode = onSelectNode
                    )
                }
            }
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
    modifier: Modifier = Modifier
) {
    val groupedByStage = nodes.groupBy { it.stage }
    val scrollState = rememberScrollState()

    val visibleStages = if (hideBypassedNodes) {
        PipelineStage.entries.filter { stage -> (groupedByStage[stage] ?: emptyList()).isNotEmpty() }
    } else {
        PipelineStage.entries
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.Top
    ) {
        visibleStages.forEachIndexed { stageIndex, stage ->
            val stageNodes = groupedByStage[stage] ?: emptyList()

            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Individual Stage Column
                StageSwimlaneColumn(
                    stage = stage,
                    nodes = stageNodes,
                    selectedNode = selectedNode,
                    isPresentationMode = isPresentationMode,
                    onSelectNode = onSelectNode,
                    modifier = Modifier.width(305.dp)
                )

                // Visual Stage-to-Stage Handoff Bridge Arrow
                if (stageIndex < visibleStages.size - 1) {
                    StageTransitionBridge(
                        isPresentationMode = isPresentationMode,
                        modifier = Modifier.padding(top = 80.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun StageSwimlaneColumn(
    stage: PipelineStage,
    nodes: List<PipelineNode>,
    selectedNode: PipelineNode?,
    isPresentationMode: Boolean,
    onSelectNode: (PipelineNode) -> Unit,
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(stage.colorHex).copy(alpha = if (isPresentationMode) 0.25f else 0.12f))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
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
                nodes.forEachIndexed { nodeIndex, node ->
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        PipelineNodeCard(
                            node = node,
                            isSelected = selectedNode?.id == node.id,
                            isPresentationMode = isPresentationMode,
                            onClick = { onSelectNode(node) },
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Vertical connector between nodes in the same stage
                        if (nodeIndex < nodes.size - 1) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(vertical = 4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(2.dp)
                                        .height(10.dp)
                                        .background(if (isPresentationMode) Color(0xFF475569) else WeMadeColors.Border)
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    IconArrowDown(
                                        modifier = Modifier.size(9.dp),
                                        color = WeMadeColors.Primary
                                    )
                                    Text(
                                        text = "Diteruskan",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = WeMadeColors.Primary
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .width(2.dp)
                                        .height(10.dp)
                                        .background(if (isPresentationMode) Color(0xFF475569) else WeMadeColors.Border)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Directional Arrow Bridge connecting adjacent Stage Swimlanes
 */
@Composable
private fun StageTransitionBridge(
    isPresentationMode: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(
                    if (isPresentationMode) Color(0xFF1E293B)
                    else WeMadeColors.PrimaryContainer
                )
                .border(
                    width = 1.dp,
                    color = if (isPresentationMode) Color(0xFF475569) else WeMadeColors.Primary.copy(alpha = 0.3f),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            IconArrowRight(
                modifier = Modifier.size(13.dp),
                color = WeMadeColors.Primary
            )
        }
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = "Handoff",
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = if (isPresentationMode) Color(0xFF94A3B8) else WeMadeColors.OnSurfaceMuted
        )
    }
}

/**
 * Linear Horizontal Chain Layout (Step-by-Step Chain)
 */
@Composable
private fun LinearFlowGraphLayout(
    nodes: List<PipelineNode>,
    selectedNode: PipelineNode?,
    isPresentationMode: Boolean,
    onSelectNode: (PipelineNode) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        nodes.forEachIndexed { index, node ->
            PipelineNodeCard(
                node = node,
                isSelected = selectedNode?.id == node.id,
                isPresentationMode = isPresentationMode,
                onClick = { onSelectNode(node) },
                modifier = Modifier.width(290.dp)
            )

            if (index < nodes.size - 1) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(horizontal = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(WeMadeColors.PrimaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        IconArrowRight(
                            modifier = Modifier.size(12.dp),
                            color = WeMadeColors.Primary
                        )
                    }
                    Text(
                        text = "Ke Tahap ${node.stepNumber + 1}",
                        fontSize = 9.sp,
                        color = if (isPresentationMode) Color(0xFF94A3B8) else WeMadeColors.OnSurfaceMuted
                    )
                }
            }
        }
    }
}

/**
 * Vertical Table/List Layout for traditional line audit
 */
@Composable
private fun VerticalListLayout(
    nodes: List<PipelineNode>,
    selectedNode: PipelineNode?,
    isPresentationMode: Boolean,
    onSelectNode: (PipelineNode) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        nodes.forEach { node ->
            PipelineNodeCard(
                node = node,
                isSelected = selectedNode?.id == node.id,
                isPresentationMode = isPresentationMode,
                onClick = { onSelectNode(node) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

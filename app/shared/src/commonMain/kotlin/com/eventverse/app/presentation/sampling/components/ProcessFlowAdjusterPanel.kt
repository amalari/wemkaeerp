package com.eventverse.app.presentation.sampling.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import com.eventverse.app.domain.process.TenantOptionalProcess
import com.eventverse.app.domain.sampling.SamplingPipelineStage
import com.eventverse.app.domain.workqueue.WorkStationCatalog
import com.eventverse.app.domain.workqueue.WorkStationSpec
import com.eventverse.app.presentation.designsystem.ClayBadge
import com.eventverse.app.presentation.designsystem.ClayBorder
import com.eventverse.app.presentation.designsystem.ClayButton
import com.eventverse.app.presentation.designsystem.ClayButtonStyle
import com.eventverse.app.presentation.designsystem.ClayCard
import com.eventverse.app.presentation.designsystem.ClayShapes
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.IconChevronDown
import com.eventverse.app.presentation.designsystem.clayFlat
import com.eventverse.app.presentation.sampling.ProcessFlowScope
import com.eventverse.app.presentation.sampling.ProcessFlowUiEvent
import com.eventverse.app.presentation.sampling.ProcessFlowViewModel
import com.eventverse.app.presentation.sampling.SamplingOrderScopeItem
import com.eventverse.app.presentation.theme.WeMadeColors

/** Tahap wajib yang tampil di Adjust Flow — kerangka tidak bisa diubah, hanya disisipi. */
private val ADJUSTABLE_STAGES = listOf(
    SamplingPipelineStage.NEW_INTAKE,
    SamplingPipelineStage.CAM_PROGRAMMING,
    SamplingPipelineStage.MACHINE_KNITTING,
    SamplingPipelineStage.LINKING_ASSEMBLY,
    SamplingPipelineStage.FINISHING_QC,
    SamplingPipelineStage.IN_DELIVERY
)

private val GAP_SIZE = 28.dp

/**
 * Panel "Adjust Flow" divisi sampling — flow wajib dirender sebagai kerangka, dan
 * divisi sampling bisa menyisipkan proses opsional (Bordir, Sablon, dst.) dua cara:
 * tombol `+` di celah mana pun, atau drag chip dari palet ke celah yang diinginkan.
 * Chip yang sudah terpasang juga bisa didrag ke celah lain untuk reposisi.
 */
@Composable
fun ProcessFlowAdjusterPanel(
    viewModel: ProcessFlowViewModel,
    modifier: Modifier = Modifier,
    hideScopeSelector: Boolean = false
) {
    val state by viewModel.uiState.collectAsState()
    val dragState = rememberProcessFlowDragState()
    val available = availableTemplates(state.processes)

    ClayCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = ClaySpacing.Md, vertical = ClaySpacing.Sm),
        shape = ClayShapes.Card,
        contentPadding = PaddingValues(horizontal = ClaySpacing.Md, vertical = ClaySpacing.Sm)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f, fill = false)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
                    ) {
                        Text(
                            text = "ALUR PROSES",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = WeMadeColors.OnSurface
                        )
                        if (!hideScopeSelector) {
                            FlowScopeSelector(
                                currentScope = state.scope,
                                availableOrders = state.availableOrders,
                                onSelectScope = { viewModel.onEvent(ProcessFlowUiEvent.SelectScope(it)) }
                            )
                        }
                        when (val currentScope = state.scope) {
                            is ProcessFlowScope.DefaultTenant -> {
                                ClayBadge(
                                    text = "Template Default Pabrik",
                                    tint = WeMadeColors.Primary
                                )
                            }
                            is ProcessFlowScope.Design -> {
                                if (state.isCustomFlow) {
                                    ClayBadge(
                                        text = "Alur Kustom Desain",
                                        tint = WeMadeColors.Secondary
                                    )
                                    ClayButton(
                                        text = "Reset ke Default",
                                        onClick = { viewModel.onEvent(ProcessFlowUiEvent.ResetToDefault) },
                                        style = ClayButtonStyle.Secondary,
                                        fontSize = 10.sp,
                                        contentPadding = PaddingValues(horizontal = ClaySpacing.Sm, vertical = 4.dp)
                                    )
                                } else {
                                    ClayBadge(
                                        text = "Mengikuti Alur Default",
                                        tint = WeMadeColors.Success
                                    )
                                }
                            }
                        }
                    }
                    val subtitle = when (val scope = state.scope) {
                        is ProcessFlowScope.DefaultTenant ->
                            "Mengatur alur template standar untuk seluruh artikel baru. Seret proses opsional ke celah flow."
                        is ProcessFlowScope.Design ->
                            "Menyesuaikan alur khusus untuk ${scope.spkNumber} (${scope.styleName}). Mengubah flow di sini tidak mempengaruhi alur default."
                    }
                    Text(
                        text = subtitle,
                        fontSize = 10.sp,
                        color = WeMadeColors.OnSurfaceMuted
                    )
                }
                if (state.error != null) {
                    ClayBadge(text = state.error ?: "", tint = WeMadeColors.Error)
                }
            }

            // Baris flow: tahap wajib, chip proses tersisip, dan celah drop (+)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ADJUSTABLE_STAGES.forEach { stage ->
                    StagePill(stage.displayName)
                    state.processes
                        .filter { it.samplingAnchorAfter == stage }
                        .forEach { process ->
                            PlacedProcessChip(
                                process = process,
                                dragState = dragState,
                                onMove = { _, anchor ->
                                    viewModel.onEvent(ProcessFlowUiEvent.MoveProcess(process.processId, anchor))
                                },
                                onRemove = {
                                    viewModel.onEvent(ProcessFlowUiEvent.RemoveProcess(process.processId))
                                }
                            )
                        }
                    GapDropSlot(
                        anchor = stage,
                        dragState = dragState,
                        availableTemplates = available,
                        onInsertFromMenu = { template ->
                            viewModel.onEvent(
                                ProcessFlowUiEvent.InsertProcess(template.code.value, template.displayName, stage)
                            )
                        }
                    )
                }
            }

            // Palet: template opsional yang belum terpasang — bisa didrag ke celah mana pun
            Row(
                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "PALET OPSIONAL",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = WeMadeColors.OnSurfaceMuted
                )
                if (available.isEmpty()) {
                    Text(
                        text = "Semua proses opsional sudah terpasang di flow",
                        fontSize = 10.sp,
                        color = WeMadeColors.OnSurfaceMuted
                    )
                } else {
                    available.forEach { template ->
                        PaletteChip(
                            template = template,
                            dragState = dragState,
                            onInsert = { anchor ->
                                viewModel.onEvent(
                                    ProcessFlowUiEvent.InsertProcess(template.code.value, template.displayName, anchor)
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

private fun availableTemplates(placed: List<TenantOptionalProcess>): List<WorkStationSpec> {
    val placedCodes = placed.map { it.code }.toSet()
    return WorkStationCatalog.optionalStations().filter { it.code.value !in placedCodes }
}

@Composable
private fun StagePill(label: String) {
    Box(
        modifier = Modifier.clayFlat(
            shape = ClayShapes.Chip,
            background = WeMadeColors.SurfaceMuted,
            outline = WeMadeColors.Outline
        )
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = WeMadeColors.OnSurface,
            modifier = Modifier.padding(horizontal = ClaySpacing.Sm, vertical = 6.dp)
        )
    }
}

/** Celah antar tahap: klik `+` untuk menu sisip, atau target drop saat drag chip. */
@Composable
private fun GapDropSlot(
    anchor: SamplingPipelineStage,
    dragState: ProcessFlowDragState,
    availableTemplates: List<WorkStationSpec>,
    onInsertFromMenu: (WorkStationSpec) -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    val isHovered = dragState.hoveredGap == anchor

    Box(
        modifier = Modifier
            .size(GAP_SIZE)
            .onGloballyPositioned { coordinates ->
                dragState.registerGap(anchor, Rect(coordinates.positionInWindow(), coordinates.size.toSize()))
            }
            .clayFlat(
                shape = ClayShapes.Chip,
                background = if (isHovered) WeMadeColors.PrimaryContainer else WeMadeColors.SurfaceMuted,
                outline = if (isHovered) WeMadeColors.Primary else WeMadeColors.OutlineSoft
            )
            .clickable { menuOpen = true },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "+",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = if (isHovered) WeMadeColors.Primary else WeMadeColors.OnSurfaceMuted
        )
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            if (availableTemplates.isEmpty()) {
                DropdownMenuItem(text = { Text("Tidak ada proses tersisa", fontSize = 11.sp) }, onClick = {})
            } else {
                availableTemplates.forEach { template ->
                    DropdownMenuItem(
                        text = { Text(template.displayName, fontSize = 12.sp) },
                        onClick = {
                            menuOpen = false
                            onInsertFromMenu(template)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun PlacedProcessChip(
    process: TenantOptionalProcess,
    dragState: ProcessFlowDragState,
    onMove: (processId: String, anchor: SamplingPipelineStage) -> Unit,
    onRemove: () -> Unit
) {
    DraggableChipFrame(
        processId = process.processId,
        templateCode = null,
        dragState = dragState,
        onDrop = { pid, _, anchor -> if (pid != null) onMove(pid, anchor) }
    ) {
        ClayBadge(
            text = process.displayName,
            tint = WeMadeColors.Accent,
            trailing = {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(ClayShapes.Chip)
                        .background(WeMadeColors.OutlineSoft)
                        .clickable(onClick = onRemove),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "x", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = WeMadeColors.OnSurface)
                }
            }
        )
    }
}

@Composable
private fun PaletteChip(
    template: WorkStationSpec,
    dragState: ProcessFlowDragState,
    onInsert: (anchor: SamplingPipelineStage) -> Unit
) {
    DraggableChipFrame(
        processId = null,
        templateCode = template.code.value,
        dragState = dragState,
        onDrop = { _, templateCode, anchor ->
            if (templateCode != null) onInsert(anchor)
        }
    ) {
        ClayBadge(text = template.displayName, tint = WeMadeColors.Primary)
    }
}

/**
 * Bingkai chip yang bisa didrag: mencatat posisi window chip, mengalirkan gesture
 * drag ke [ProcessFlowDragState], dan melakukan commit drop lewat [onDrop] hanya
 * bila chip dilepas di atas celah sah.
 */
@Composable
private fun DraggableChipFrame(
    processId: String?,
    templateCode: String?,
    dragState: ProcessFlowDragState,
    onDrop: (processId: String?, templateCode: String?, anchor: SamplingPipelineStage) -> Unit,
    content: @Composable RowScope.() -> Unit
) {
    var chipWindowPos by remember { mutableStateOf(Offset.Zero) }
    Row(
        modifier = Modifier
            .onGloballyPositioned { chipWindowPos = it.positionInWindow() }
            .pointerInput(processId, templateCode) {
                detectDragGestures(
                    onDragStart = { local ->
                        dragState.onDragStart(processId, templateCode, chipWindowPos + local)
                    },
                    onDrag = { change, amount ->
                        if (dragState.isDragging) {
                            change.consume()
                            dragState.onDrag(amount)
                        }
                    },
                    onDragEnd = {
                        dragState.onDragEnd { pid, tcode, anchor ->
                            if (anchor != null) onDrop(pid, tcode, anchor)
                        }
                    },
                    onDragCancel = { dragState.onDragCancel() }
                )
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        content()
    }
}

/**
 * Komponen pemilih ruang lingkup flow: Template Default Pabrik atau Alur Kustom per SPK/Desain.
 */
@Composable
private fun FlowScopeSelector(
    currentScope: ProcessFlowScope,
    availableOrders: List<SamplingOrderScopeItem>,
    onSelectScope: (ProcessFlowScope) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    val currentLabel = when (currentScope) {
        is ProcessFlowScope.DefaultTenant -> "Template Default Pabrik"
        is ProcessFlowScope.Design -> "${currentScope.spkNumber} • ${currentScope.styleName}"
    }

    Box {
        Row(
            modifier = Modifier
                .clip(ClayShapes.Pill)
                .clickable { expanded = true }
                .padding(horizontal = ClaySpacing.Sm, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)
        ) {
            Text(
                text = currentLabel,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = WeMadeColors.Primary
            )
            IconChevronDown(
                color = WeMadeColors.Primary,
                modifier = Modifier.size(12.dp)
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = {
                    Text(
                        text = "Template Default Pabrik",
                        fontSize = 12.sp,
                        fontWeight = if (currentScope is ProcessFlowScope.DefaultTenant) FontWeight.Bold else FontWeight.Normal
                    )
                },
                onClick = {
                    onSelectScope(ProcessFlowScope.DefaultTenant)
                    expanded = false
                }
            )
            if (availableOrders.isNotEmpty()) {
                availableOrders.forEach { item ->
                    val isSelected = currentScope is ProcessFlowScope.Design && currentScope.orderId == item.orderId
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "${item.spkNumber} • ${item.styleName}",
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        onClick = {
                            onSelectScope(
                                ProcessFlowScope.Design(
                                    orderId = item.orderId,
                                    styleName = item.styleName,
                                    spkNumber = item.spkNumber
                                )
                            )
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}
package com.eventverse.app.presentation.sampling.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.domain.process.TenantOptionalProcess
import com.eventverse.app.domain.sampling.SamplingPipelineStage
import com.eventverse.app.domain.transfer.FlowLegView
import com.eventverse.app.domain.transfer.FlowNodeRef
import com.eventverse.app.domain.workqueue.WorkStationCatalog
import com.eventverse.app.domain.workqueue.WorkStationSpec
import com.eventverse.app.presentation.designsystem.ClayBadge
import com.eventverse.app.presentation.designsystem.ClayButton
import com.eventverse.app.presentation.designsystem.ClayButtonStyle
import com.eventverse.app.presentation.designsystem.ClayCard
import com.eventverse.app.presentation.designsystem.ClayShapes
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.IconChevronDown
import com.eventverse.app.domain.process.FlowPhase
import com.eventverse.app.domain.process.PhaseTaggableStage
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
    SamplingPipelineStage.CUCI_SOFTENER,
    SamplingPipelineStage.SETRIKA_UAP,
    SamplingPipelineStage.QC_FINISHING,
    SamplingPipelineStage.PENGEMASAN,
    SamplingPipelineStage.STORAGE_HOLDING,
    SamplingPipelineStage.IN_DELIVERY
)

/**
 * Panel "Adjust Flow" divisi sampling.
 *
 * Perannya hanya **merakit**: kerangka tahap wajib, chip proses tersisip, celah di antaranya,
 * dan palet di bawah. Yang merender masing-masing tinggal di berkasnya sendiri —
 * [ProcessFlowChips], [ProcessFlowGap], [ProcessFlowInsertDialog].
 *
 * Celah antar chip berubah menjadi konektor pengiriman **dengan sendirinya** ketika perpindahan
 * itu melintasi gedung atau keluar ke vendor. Tidak ada chip "Pengiriman" yang bisa diseret
 * orang ke tempat yang salah: perpindahan adalah sifat sambungan, diturunkan dari konfigurasi
 * lokasi, bukan simpul yang diketik.
 */
@Composable
fun ProcessFlowAdjusterPanel(
    viewModel: ProcessFlowViewModel,
    modifier: Modifier = Modifier,
    hideScopeSelector: Boolean = false,
    /** Alur sudah final (SPK di Program CAM ke atas): tampil read-only tanpa sisip/geser/hapus. */
    isLocked: Boolean = false
) {
    val state by viewModel.uiState.collectAsState()
    val dragState = rememberProcessFlowDragState()
    val available = availableTemplates(state.processes)

    // Templat yang sedang ditanyakan "dikerjakan di mana", beserta celah tujuannya.
    var pendingInsert by remember { mutableStateOf<Pair<WorkStationSpec, SamplingPipelineStage>?>(null) }
    var inspectedLeg by remember { mutableStateOf<FlowLegView?>(null) }
    var panelWindowPos by remember { mutableStateOf(Offset.Zero) }

    ClayCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = ClaySpacing.Md, vertical = ClaySpacing.Sm),
        shape = ClayShapes.Card,
        contentPadding = PaddingValues(horizontal = ClaySpacing.Md, vertical = ClaySpacing.Sm)
    ) {
        Box(modifier = Modifier.onGloballyPositioned { panelWindowPos = it.positionInWindow() }) {
        Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
            FlowPanelHeader(
                state = state,
                hideScopeSelector = hideScopeSelector,
                isLocked = isLocked,
                onSelectScope = { viewModel.onEvent(ProcessFlowUiEvent.SelectScope(it)) },
                onReset = { viewModel.onEvent(ProcessFlowUiEvent.ResetToDefault) }
            )

            ProcessFlowCarousel(modifier = Modifier.fillMaxWidth()) {
                // Penomoran berjalan mencakup proses opsional: proses yang disisipkan ikut
                // mendapat nomor dan seluruh tahap sesudahnya bergeser, jadi baris selalu
                // terbaca sebagai satu urutan utuh.
                var step = 1
                ADJUSTABLE_STAGES.forEachIndexed { index, stage ->
                    val tagged = PhaseTaggableStage.forSamplingStage(stage)
                    if (tagged == null) StagePill(step = step++, label = stage.displayName) else PhaseTaggedStagePill(
                        step = if (state.phaseTags.phasesOf(tagged).isNotEmpty()) step++ else step,
                        label = stage.displayName, stage = tagged, tags = state.phaseTags, isLocked = isLocked,
                        onToggle = { st, phase -> viewModel.onEvent(ProcessFlowUiEvent.TogglePhaseTag(st, phase)) }
                    )

                    val anchored = state.processes.filter { it.samplingAnchorAfter == stage }
                    anchored.forEachIndexed { procIndex, process ->
                        // Celah juga ada di antara tahap dan proses pertamanya (dan antar
                        // proses) — menyisipkan proses tidak boleh "memakan" tombol + yang
                        // sudah ada di situ. Leg pengiriman tetap hanya di celah terakhir,
                        // karena leg berangkat dari simpul terakhir tahap ini.
                        ProcessFlowGap(
                            slotId = "${stage.name}-$procIndex",
                            isLast = false,
                            anchor = stage,
                            legs = emptyList(),
                            dragState = dragState,
                            availableTemplates = available,
                            onInsertFromMenu = { template -> pendingInsert = template to stage },
                            onLegClick = { inspectedLeg = it },
                            isLocked = isLocked
                        )
                        PlacedProcessChip(
                            process = process,
                            stepNumber = step++,
                            dragState = dragState,
                            onMove = { pid, anchor ->
                                viewModel.onEvent(ProcessFlowUiEvent.MoveProcess(pid, anchor))
                            },
                            onRemove = {
                                viewModel.onEvent(ProcessFlowUiEvent.RemoveProcess(process.processId))
                            },
                            isLocked = isLocked
                        )
                    }

                    // Celah berada setelah seluruh proses yang berjangkar di tahap ini, jadi
                    // leg yang digambar di sini adalah yang berangkat dari simpul terakhir —
                    // proses paling buncit bila ada, kalau tidak tahapnya sendiri.
                    // Tahap terakhir tidak punya "sesudah"; celahnya tetap ada agar proses bisa
                    // disisipkan di ujung, tapi tanpa garis yang menggantung ke ruang kosong.
                    ProcessFlowGap(
                        slotId = "${stage.name}-final",
                        isLast = index == ADJUSTABLE_STAGES.lastIndex,
                        anchor = stage,
                        legs = state.legsLeaving(lastNodeAt(stage, anchored)),
                        dragState = dragState,
                        availableTemplates = available,
                        onInsertFromMenu = { template -> pendingInsert = template to stage },
                        onLegClick = { inspectedLeg = it },
                        isLocked = isLocked
                    )
                }
            }

            if (!isLocked) {
                FlowPalette(
                    available = available,
                    dragState = dragState,
                    onInsert = { template, anchor -> pendingInsert = template to anchor }
                )
            }
        }
        ProcessFlowDragGhost(dragState = dragState, panelWindowPos = panelWindowPos)
        }
    }

    pendingInsert?.let { (template, anchor) ->
        ProcessFlowInsertDialog(
            template = template,
            onDismiss = { pendingInsert = null },
            onConfirm = { mode, vendorRef ->
                pendingInsert = null
                viewModel.onEvent(
                    ProcessFlowUiEvent.InsertProcess(
                        code = template.code.value,
                        displayName = template.displayName,
                        anchorAfter = anchor,
                        executionMode = mode,
                        vendorRef = vendorRef
                    )
                )
            }
        )
    }

    inspectedLeg?.let { view ->
        TransferLegDetailDialog(view = view, onDismiss = { inspectedLeg = null })
    }
}

/** Simpul terakhir sebelum celah: proses paling buncit di tahap ini, atau tahapnya sendiri. */
private fun lastNodeAt(
    stage: SamplingPipelineStage,
    anchored: List<TenantOptionalProcess>
): FlowNodeRef =
    anchored.lastOrNull()?.let { FlowNodeRef.Process(it.code) } ?: FlowNodeRef.Stage(stage)

private fun availableTemplates(placed: List<TenantOptionalProcess>): List<WorkStationSpec> {
    val placedCodes = placed.map { it.code }.toSet()
    return WorkStationCatalog.optionalStations().filter { it.code.value !in placedCodes }
}

@Composable
private fun FlowPanelHeader(
    state: com.eventverse.app.presentation.sampling.ProcessFlowUiState,
    hideScopeSelector: Boolean,
    isLocked: Boolean,
    onSelectScope: (ProcessFlowScope) -> Unit,
    onReset: () -> Unit
) {
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
                        onSelectScope = onSelectScope
                    )
                }
                when (val currentScope = state.scope) {
                    is ProcessFlowScope.DefaultTenant ->
                        ClayBadge(text = "Template Default Pabrik", tint = WeMadeColors.Primary)
                    is ProcessFlowScope.Design -> {
                        if (state.isCustomFlow || state.hasCustomPhaseTags) {
                            ClayBadge(text = "Alur Kustom Desain", tint = WeMadeColors.Accent)
                            if (!isLocked) ClayButton(
                                text = "Reset ke Default",
                                style = ClayButtonStyle.Secondary,
                                onClick = onReset
                            )
                        } else {
                            ClayBadge(text = "Mengikuti Alur Default", tint = WeMadeColors.Success)
                        }
                        if (isLocked) ClayBadge(text = "Alur Dikunci", tint = WeMadeColors.OnSurfaceMuted)
                        Unit
                    }
                }
            }
            when (val currentScope = state.scope) {
                is ProcessFlowScope.Design -> Text(
                    text = if (isLocked) {
                        "Alur ${currentScope.spkNumber} sudah dikunci sejak masuk Program CAM — " +
                            "tidak bisa diubah lagi."
                    } else {
                        "Menyesuaikan alur khusus untuk ${currentScope.spkNumber} " +
                            "(${currentScope.styleName}). Mengubah flow di sini tidak mempengaruhi alur default."
                    },
                    fontSize = 10.sp,
                    color = WeMadeColors.OnSurfaceMuted
                )
                is ProcessFlowScope.DefaultTenant -> Unit
            }
        }
        state.error?.let { message ->
            ClayBadge(text = message, tint = WeMadeColors.Error)
        }
    }
}

@Composable
private fun FlowPalette(
    available: List<WorkStationSpec>,
    dragState: ProcessFlowDragState,
    onInsert: (WorkStationSpec, SamplingPipelineStage) -> Unit
) {
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
                    onInsert = { anchor -> onInsert(template, anchor) }
                )
            }
        }
    }
}

/** Pemilih ruang lingkup flow: Template Default Pabrik atau Alur Kustom per SPK/Desain. */
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
            IconChevronDown(color = WeMadeColors.Primary, modifier = Modifier.size(12.dp))
        }

        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = {
                    Text(
                        text = "Template Default Pabrik",
                        fontSize = 12.sp,
                        fontWeight = if (currentScope is ProcessFlowScope.DefaultTenant) {
                            FontWeight.Bold
                        } else {
                            FontWeight.Normal
                        }
                    )
                },
                onClick = {
                    onSelectScope(ProcessFlowScope.DefaultTenant)
                    expanded = false
                }
            )
            availableOrders.forEach { item ->
                val isSelected = currentScope is ProcessFlowScope.Design &&
                    currentScope.orderId == item.orderId
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

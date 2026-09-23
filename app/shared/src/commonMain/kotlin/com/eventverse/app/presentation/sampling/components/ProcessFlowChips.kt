package com.eventverse.app.presentation.sampling.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt
import androidx.compose.ui.unit.sp
import com.eventverse.app.domain.process.TenantOptionalProcess
import com.eventverse.app.domain.sampling.SamplingPipelineStage
import com.eventverse.app.domain.workqueue.WorkExecutionMode
import com.eventverse.app.domain.workqueue.WorkStationSpec
import com.eventverse.app.presentation.designsystem.ClayBadge
import com.eventverse.app.presentation.designsystem.ClayShapes
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.clayFlat
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Keluarga chip pada baris alur: tahap wajib, proses terpasang, dan chip palet — beserta
 * bingkai drag yang menyatukan ketiganya.
 *
 * Dipisahkan dari panel karena semuanya berubah karena alasan yang sama (bagaimana sebuah
 * simpul digambar dan diseret), sementara panel berubah karena alasan lain (apa yang dirakit
 * dan dari mana datanya).
 */

/** Tahap wajib: kerangka alur yang tidak bisa dihapus, hanya disisipi. */
@Composable
internal fun StagePill(step: Int, label: String) {
    Row(
        modifier = Modifier
            .clayFlat(
                shape = ClayShapes.Chip,
                background = WeMadeColors.SurfaceMuted,
                outline = WeMadeColors.Outline
            )
            .padding(start = ClaySpacing.Sm, end = ClaySpacing.Md, top = ClaySpacing.Sm, bottom = ClaySpacing.Sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
    ) {
        // Nomor urut membuat kerangka wajib terbaca sebagai urutan, bukan deretan tombol.
        Box(
            modifier = Modifier.size(18.dp).clip(ClayShapes.Pill).background(WeMadeColors.Primary),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = step.toString(),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = WeMadeColors.Surface
            )
        }
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = WeMadeColors.OnSurface,
            maxLines = 1
        )
    }
}

/**
 * Proses opsional yang sudah terpasang di alur.
 *
 * Yang dikerjakan vendor luar diberi warna [WeMadeColors.Accent] dan nama vendornya, karena
 * "Bordir Komputer" saja tidak cukup untuk tahu barangnya keluar pabrik — dan itu justru
 * informasi yang menentukan ada tidaknya Surat Jalan.
 */
@Composable
internal fun PlacedProcessChip(
    process: TenantOptionalProcess,
    dragState: ProcessFlowDragState,
    onMove: (processId: String, anchor: SamplingPipelineStage) -> Unit,
    onRemove: () -> Unit
) {
    val isSubcontracted = process.executionMode == WorkExecutionMode.SUBCONTRACTED
    val label = if (isSubcontracted && !process.vendorRef.isNullOrBlank()) {
        "${process.displayName} · ${process.vendorRef}"
    } else {
        process.displayName
    }

    DraggableChipFrame(
        processId = process.processId,
        templateCode = null,
        label = label,
        dragState = dragState,
        onDrop = { pid, _, anchor -> if (pid != null) onMove(process.processId, anchor) }
    ) {
        ClayBadge(
            text = label,
            tint = if (isSubcontracted) WeMadeColors.Accent else WeMadeColors.Primary,
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

/** Chip palet: proses yang belum terpasang, bisa diseret ke celah mana pun. */
@Composable
internal fun PaletteChip(
    template: WorkStationSpec,
    dragState: ProcessFlowDragState,
    onInsert: (anchor: SamplingPipelineStage) -> Unit
) {
    DraggableChipFrame(
        processId = null,
        templateCode = template.code.value,
        label = template.displayName,
        dragState = dragState,
        onDrop = { _, templateCode, anchor ->
            if (templateCode != null) onInsert(anchor)
        }
    ) {
        ClayBadge(text = template.displayName, tint = WeMadeColors.Primary)
    }
}

/**
 * Bingkai chip yang bisa didrag: mencatat posisi window chip, mengalirkan gesture drag ke
 * [ProcessFlowDragState], dan melakukan commit drop lewat [onDrop] hanya bila chip dilepas di
 * atas celah sah.
 */
@Composable
private fun DraggableChipFrame(
    processId: String?,
    templateCode: String?,
    label: String,
    dragState: ProcessFlowDragState,
    onDrop: (processId: String?, templateCode: String?, anchor: SamplingPipelineStage) -> Unit,
    content: @Composable RowScope.() -> Unit
) {
    var chipWindowPos by remember { mutableStateOf(Offset.Zero) }
    // Chip asal dipudarkan selama diseret; yang terlihat bergerak adalah ghost di panel.
    val isSource = dragState.isDragging &&
        dragState.draggedProcessId == processId &&
        dragState.draggedTemplateCode == templateCode
    Row(
        modifier = Modifier
            .alpha(if (isSource) 0.35f else 1f)
            .onGloballyPositioned { chipWindowPos = it.positionInWindow() }
            .pointerInput(processId, templateCode) {
                detectDragGestures(
                    onDragStart = { local ->
                        dragState.onDragStart(processId, templateCode, label, chipWindowPos + local)
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
        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xxs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        content()
    }
}

/**
 * Ghost chip yang mengikuti pointer selama drag, digambar di atas seluruh panel.
 *
 * [panelWindowPos] adalah posisi window kontainer tempat ghost ini ditaruh — pointer disimpan
 * dalam koordinat window, jadi perlu dikurangi agar jatuh tepat di bawah kursor.
 */
@Composable
internal fun ProcessFlowDragGhost(dragState: ProcessFlowDragState, panelWindowPos: Offset) {
    if (!dragState.isDragging) return
    val local = dragState.dragPointerWindowPos - panelWindowPos
    Box(
        modifier = Modifier
            .zIndex(10f)
            .offset { IntOffset(local.x.roundToInt() + GHOST_POINTER_GAP, local.y.roundToInt() + GHOST_POINTER_GAP) }
            .alpha(0.9f)
    ) {
        ClayBadge(
            text = dragState.draggedLabel,
            tint = if (dragState.hoveredGap != null) WeMadeColors.Success else WeMadeColors.Primary
        )
    }
}

/** Ghost digeser sedikit dari ujung kursor supaya celah di bawahnya tetap terlihat. */
private const val GHOST_POINTER_GAP = 12

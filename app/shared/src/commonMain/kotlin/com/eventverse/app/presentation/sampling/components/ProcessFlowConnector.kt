package com.eventverse.app.presentation.sampling.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import com.eventverse.app.domain.sampling.SamplingPipelineStage
import com.eventverse.app.domain.stageflow.StageCode
import com.eventverse.app.domain.transfer.FlowLegStatus
import com.eventverse.app.domain.transfer.FlowLegView
import com.eventverse.app.domain.workqueue.WorkStationSpec
import com.eventverse.app.presentation.designsystem.ClayBorder
import com.eventverse.app.presentation.designsystem.ClayShapes
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.IconTruck
import com.eventverse.app.presentation.designsystem.clayDashedOutline
import com.eventverse.app.presentation.theme.WeMadeColors

/** Lebar celah kosong — cukup untuk tombol `+`, tidak lebih. */
internal val GAP_SIZE = 28.dp

/** Konektor perpindahan perlu ruang untuk ikon dan nama tujuan. */
private val CONNECTOR_MIN_WIDTH = 116.dp

/**
 * Celah antar chip pada baris alur: tombol `+` polos, atau konektor pengiriman.
 *
 * Pemilihannya bukan preferensi tampilan melainkan cerminan data: celah menjadi konektor
 * **hanya** bila ada leg yang berangkat dari simpul itu, dan leg diturunkan dari konfigurasi
 * lokasi — bukan diketik siapa pun. Pabrik satu atap tanpa makloon karenanya melihat baris alur
 * persis seperti sebelum fitur ini ada.
 */
@Composable
internal fun ProcessFlowGap(
    slotId: String,
    isLast: Boolean,
    anchor: StageCode,
    legs: List<FlowLegView>,
    dragState: ProcessFlowDragState,
    availableTemplates: List<WorkStationSpec>,
    onInsertFromMenu: (WorkStationSpec) -> Unit,
    onLegClick: (FlowLegView) -> Unit,
    isLocked: Boolean = false
) {
    // Seluruh celah (garis + slot/konektor) adalah zona drop, supaya chip palet bisa dijatuhkan
    // juga di celah yang sudah menjadi konektor pengiriman — bukan cuma di tombol `+` kecil.
    // [slotId] unik per celah karena satu tahap bisa punya lebih dari satu celah.
    Row(
        modifier = Modifier.onGloballyPositioned { coordinates ->
            dragState.registerGap(slotId, anchor, Rect(coordinates.positionInWindow(), coordinates.size.toSize()))
        },
        verticalAlignment = Alignment.CenterVertically
    ) {
        FlowLine()
        if (legs.isEmpty()) {
            // Alur terkunci: celah kosong cukup garis — tidak ada tombol + untuk menyisipkan.
            if (!isLocked) PlainGapSlot(slotId, dragState, availableTemplates, onInsertFromMenu)
        } else {
            LegStack(legs, onLegClick)
        }
        if (!isLast) FlowLine()
    }
}

/** Garis penghubung antar simpul — memberi napas sekaligus membaca arah alur kiri → kanan. */
@Composable
private fun FlowLine() {
    Box(
        modifier = Modifier
            .width(ClaySpacing.Lg)
            .height(ClayBorder.Medium)
            .background(WeMadeColors.OnSurfaceDisabled)
    )
}

@Composable
private fun LegStack(legs: List<FlowLegView>, onLegClick: (FlowLegView) -> Unit) {
    // Beberapa leg bisa berbagi satu celah ketika dua proses berurutan memakai vendor berbeda;
    // model datanya belum punya urutan di dalam satu jangkar, jadi keduanya ditumpuk apa adanya
    // alih-alih berpura-pura tahu mana yang lebih dulu.
    Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Xxs)) {
        legs.forEach { view -> TransferConnector(view, onLegClick) }
    }
}

/** Celah kosong — perilaku lama, tidak berubah. */
@Composable
private fun PlainGapSlot(
    slotId: String,
    dragState: ProcessFlowDragState,
    availableTemplates: List<WorkStationSpec>,
    onInsertFromMenu: (WorkStationSpec) -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    val isHovered = dragState.hoveredGapId == slotId

    Box(
        modifier = Modifier
            .size(GAP_SIZE)
            .clayDashedOutline(
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

/**
 * Konektor pengiriman: barang berpindah tempat di celah ini.
 *
 * Dibuat sebagai kelas visual yang berbeda dari chip stasiun — outline putus-putus, latar
 * pucat, tanpa isi pekat — supaya tidak terbaca sebagai stasiun kerja tambahan. Warnanya yang
 * berbicara soal status; ketebalan garisnya tetap sama di semua keadaan.
 */
@Composable
private fun TransferConnector(
    view: FlowLegView,
    onClick: (FlowLegView) -> Unit
) {
    val tint = view.status.tint()

    Row(
        modifier = Modifier
            .widthIn(min = CONNECTOR_MIN_WIDTH)
            .clayDashedOutline(
                shape = ClayShapes.Pill,
                background = WeMadeColors.SurfaceMuted,
                outline = tint
            )
            .clickable { onClick(view) }
            .padding(horizontal = ClaySpacing.Sm, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconTruck(modifier = Modifier.size(14.dp), color = tint)
        Column(modifier = Modifier.weight(1f, fill = false)) {
            Text(
                text = view.leg.destination.displayLabel,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = WeMadeColors.OnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = view.status.shortLabel(),
                fontSize = 9.sp,
                color = tint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Warna status leg. Memakai token peran yang sudah ada — merah untuk yang menghambat, amber
 * untuk yang sedang berjalan, hijau untuk yang selesai — jadi ia sekeluarga dengan sinyal
 * produksi di kanvas Factory Flow, bukan palet baru yang bersaing dengannya.
 */
private fun FlowLegStatus.tint(): Color = when (this) {
    FlowLegStatus.BELUM_TERBIT -> WeMadeColors.Error
    FlowLegStatus.DIKIRIM -> WeMadeColors.Warning
    FlowLegStatus.DITERIMA -> WeMadeColors.Success
}

private fun FlowLegStatus.shortLabel(): String = when (this) {
    FlowLegStatus.BELUM_TERBIT -> "SJ belum terbit"
    FlowLegStatus.DIKIRIM -> "Dalam perjalanan"
    FlowLegStatus.DITERIMA -> "Sudah diterima"
}

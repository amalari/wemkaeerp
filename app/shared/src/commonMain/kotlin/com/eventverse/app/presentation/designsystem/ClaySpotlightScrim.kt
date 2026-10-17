package com.eventverse.app.presentation.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.eventverse.app.presentation.theme.WeMadeColors

/** Padding lubang di sekeliling target, dan radius sudut membulatnya (dipakai kedua mode scrim). */
internal val SPOTLIGHT_PADDING = 6.dp
internal val SPOTLIGHT_RADIUS = 14.dp

/**
 * Scrim gelap dengan satu lubang membulat di [target] — dasar coach mark. `null` = scrim penuh tanpa sorotan.
 *
 * Buta domain (design-system Kontrak 6): hanya tahu persegi dalam koordinat kanvas ini. Lubang diberi outline
 * tebal clay ([ClayBorder.Thick]) supaya elemen yang disorot terbaca sebagai "kartu" meski di atas latar gelap.
 *
 * [interactive] = mode tembus (TRD-TUT-002 FR-1): scrim disusun sebagai **≤4 region** ([spotlightRegions]) yang
 * masing-masing jadi node anak tersendiri — lubang **tidak tertutup node apa pun**, sehingga hit-test Compose
 * jatuh menembus ke elemen aplikasi di bawahnya; tiap region gelap tetap menelan ketukannya (fokus terjaga,
 * FR-6). Default `false` = perilaku lama: satu `Canvas` penuh menelan semua ketukan. `target == null` ⇒ mode
 * tembus tak berlaku (scrim penuh), sesuai degradasi FR-5.
 *
 * [containerSize] ukuran overlay di window (dipakai mode tembus untuk membatasi region ke tepi layar).
 */
@Composable
fun ClaySpotlightScrim(
    target: Rect?,
    modifier: Modifier = Modifier,
    scrim: Color = WeMadeColors.Scrim.copy(alpha = 0.62f),
    outline: Color = WeMadeColors.Accent,
    interactive: Boolean = false,
    containerSize: IntSize = IntSize.Zero,
) {
    val density = LocalDensity.current
    val paddingPx = with(density) { SPOTLIGHT_PADDING.toPx() }
    val radiusPx = with(density) { SPOTLIGHT_RADIUS.toPx() }
    val outlinePx = with(density) { ClayBorder.Thick.toPx() }

    if (interactive && target != null && containerSize != IntSize.Zero) {
        // Mode tembus: tiap region gelap = satu node anak berisi Canvas tanpa pointerInput (visual) + pointerInput
        // menelan ketukan. Lubang tanpa node → tembus. (Hit-test Compose per-node; path tidak bisa "menolak".)
        Box(modifier = modifier.fillMaxSize()) {
            spotlightRegions(containerSize, target, paddingPx, radiusPx).forEach { region ->
                Box(
                    modifier = Modifier
                        .offset { IntOffset(region.left.toInt(), region.top.toInt()) }
                        .size(with(density) { region.width.toDp() }, with(density) { region.height.toDp() })
                        .pointerInput(region) { detectTapGestures { } },
                ) {
                    Canvas(Modifier.fillMaxSize()) {
                        drawRect(scrim, topLeft = Offset.Zero, size = Size(size.width, size.height))
                    }
                }
            }
        }
        // Outline lubang di atas region (Canvas terpisah tanpa pointerInput) — tampilan lubang tak berubah.
        Canvas(modifier = modifier.fillMaxSize()) {
            val hole = target.inflate(paddingPx)
            drawPath(Path().apply { addRoundRect(RoundRect(hole, CornerRadius(radiusPx))) }, outline, style = Stroke(outlinePx))
        }
        return
    }

    Canvas(modifier = modifier.pointerInput(Unit) { detectTapGestures { } }) {
        val hole = target?.let {
            RoundRect(it.left - paddingPx, it.top - paddingPx, it.right + paddingPx, it.bottom + paddingPx, CornerRadius(radiusPx))
        }
        val path = Path().apply {
            fillType = PathFillType.EvenOdd
            addRect(Rect(0f, 0f, size.width, size.height))
            hole?.let { addRoundRect(it) }
        }
        drawPath(path, scrim)
        hole?.let { drawPath(Path().apply { addRoundRect(it) }, outline, style = Stroke(outlinePx)) }
    }
}

/**
 * Fungsi murni: ≤4 region gelap (atas, bawah, start, end) yang mengelilingi [target] dalam koordinat lokal
 * [container] — dasar mode tembus (TRD-TUT-002 FR-1). Diuji tanpa Compose. `target == null` ⇒ `emptyList` (scrim
 * penuh, mode tembus tak berlaku). Region yang lebar/tingginya ≤ 0 (target menyentuh tepi layar) **dibuang**, bukan
 * digambar. [radius] ikut dibekukan di signature (kontrak Track 0) meski tidak mengubah persegi region — dipakai
 * pemanggil untuk menggambar outline lubang membulat.
 */
internal fun spotlightRegions(
    container: IntSize,
    target: Rect?,
    padding: Float,
    radius: Float,
): List<Rect> {
    if (target == null) return emptyList()
    val hole = target.inflate(padding)
    val w = container.width.toFloat()
    val h = container.height.toFloat()
    return buildList {
        val top = Rect(0f, 0f, w, hole.top)
        if (top.height > 0f) add(top)
        val bottom = Rect(0f, hole.bottom, w, h)
        if (bottom.height > 0f) add(bottom)
        val start = Rect(0f, hole.top, hole.left, hole.bottom)
        if (start.width > 0f) add(start)
        val end = Rect(hole.right, hole.top, w, hole.bottom)
        if (end.width > 0f) add(end)
    }
}

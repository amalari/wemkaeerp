package com.eventverse.app.presentation.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Scrim gelap dengan satu lubang membulat di [target] — dasar coach mark. `null` = scrim penuh tanpa sorotan.
 *
 * Buta domain (design-system Kontrak 6): hanya tahu persegi dalam koordinat kanvas ini. Lubang diberi outline
 * tebal clay ([ClayBorder.Thick]) supaya elemen yang disorot terbaca sebagai "kartu" meski di atas latar gelap.
 * Scrim menelan ketukan — selama tutorial, maju-mundur lewat callout, bukan lewat klik tembus.
 */
@Composable
fun ClaySpotlightScrim(
    target: Rect?,
    modifier: Modifier = Modifier,
    scrim: Color = WeMadeColors.Scrim.copy(alpha = 0.62f),
    outline: Color = WeMadeColors.Accent,
) {
    Canvas(modifier = modifier.pointerInput(Unit) { detectTapGestures { } }) {
        val padding = 6.dp.toPx()
        val radius = CornerRadius(14.dp.toPx())
        val hole = target?.let {
            RoundRect(it.left - padding, it.top - padding, it.right + padding, it.bottom + padding, radius)
        }
        val path = Path().apply {
            fillType = PathFillType.EvenOdd
            addRect(Rect(0f, 0f, size.width, size.height))
            hole?.let { addRoundRect(it) }
        }
        drawPath(path, scrim)
        hole?.let { drawPath(Path().apply { addRoundRect(it) }, outline, style = Stroke(ClayBorder.Thick.toPx())) }
    }
}

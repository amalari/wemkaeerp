package com.eventverse.app.presentation.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Batang abu-abu penanda isi pada sketsa (kerangka non-interaktif). Buta domain: hanya warna dan
 * lebar relatif. Diangkat karena muncul di tabel, formulir, dan kartu angka (Aturan Tiga Kali).
 */
@Composable
fun ClaySketchBar(
    tint: Color,
    modifier: Modifier = Modifier,
    widthFraction: Float = 1f,
    height: Dp = 8.dp
) {
    Box(
        modifier = modifier
            .fillMaxWidth(widthFraction)
            .height(height)
            .background(tint, ClayShapes.Element)
    )
}

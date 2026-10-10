package com.eventverse.app.presentation.designsystem

import androidx.compose.foundation.ScrollState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Petunjuk "masih bisa digeser" untuk bilah horizontal: pudar ke warna latar di tepi kanan selama
 * [state] masih bisa menggulir maju. Pasang SEBELUM `horizontalScroll(state)` supaya pudarnya menempel
 * di viewport, bukan ikut tergulir. Buta domain; [background] harus sama dengan latar bilah.
 */
fun Modifier.scrollEdgeFade(state: ScrollState, background: Color, width: Dp = 24.dp): Modifier =
    drawWithContent {
        drawContent()
        if (state.canScrollForward) {
            val w = width.toPx()
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(background.copy(alpha = 0f), background),
                    startX = size.width - w,
                    endX = size.width
                ),
                topLeft = Offset(size.width - w, 0f),
                size = Size(w, size.height)
            )
        }
    }

package com.eventverse.app.presentation.designsystem

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Tombol ikon-saja — tombol tutup pada modal & drawer, aksi ikonik di toolbar.
 *
 * Menggantikan `IconButton` Material, yang membawa ripple dan ukuran sentuh 48dp bawaan Material
 * tanpa outline apa pun. Di sini umpan baliknya sama seperti komponen clay lain: tombolnya masuk
 * ke dalam bayangannya sendiri.
 *
 * Bentuk defaultnya lingkaran karena itu pemakaian paling umum (tombol ✕). Untuk ikon di dalam
 * toolbar persegi, berikan [shape] = `ClayShapes.Tile`.
 */
@Composable
fun ClayIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    shape: Shape = CircleShape,
    containerColor: Color = WeMadeColors.SurfaceMuted,
    outlineColor: Color = WeMadeColors.Outline,
    offset: Dp = ClayOffset.Pressed,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    Box(
        modifier = modifier
            .size(size)
            .claySurface(
                shape = shape,
                background = containerColor,
                outline = if (enabled) outlineColor else outlineColor.copy(alpha = 0.4f),
                offset = if (enabled) offset else ClayOffset.Flat,
                pressed = isPressed,
                borderWidth = ClayBorder.Medium,
                innerShade = false
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center,
        content = { content() }
    )
}

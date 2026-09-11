package com.eventverse.app.presentation.designsystem

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.presentation.theme.WeMadeColors

/** Peran tombol. Warna isian diturunkan dari sini, outline selalu gelap seperti pada demo. */
enum class ClayButtonStyle { Primary, Secondary, Accent, Danger, Ghost }

/**
 * Tombol clay — sepadan dengan `.btn-primary` pada demo: isian pekat, outline 3px,
 * hard shadow 4px, radius 16px, dan efek tertekan saat disentuh.
 *
 * Padding default sengaja jauh lebih rapat dari demo (`1rem 2rem`). Layar operasional di sini
 * menampung belasan aksi sekaligus; ukuran tombol landing page akan mendorong toolbar ke
 * bawah lipatan.
 */
@Composable
fun ClayButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: ClayButtonStyle = ClayButtonStyle.Primary,
    enabled: Boolean = true,
    fontSize: TextUnit = 13.sp,
    offset: Dp = ClayOffset.Small,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 9.dp),
    leading: (@Composable () -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val container = when (style) {
        ClayButtonStyle.Primary -> WeMadeColors.Primary
        ClayButtonStyle.Secondary -> WeMadeColors.Surface
        ClayButtonStyle.Accent -> WeMadeColors.Accent
        ClayButtonStyle.Danger -> WeMadeColors.Error
        ClayButtonStyle.Ghost -> Color.Transparent
    }
    val label = when (style) {
        ClayButtonStyle.Secondary -> WeMadeColors.OnSurface
        ClayButtonStyle.Ghost -> WeMadeColors.OnSurfaceMuted
        else -> Color.White
    }

    // Tombol nonaktif kehilangan bayangannya sekalian, bukan cuma diredupkan. Tombol clay yang
    // masih "mengambang" tapi tidak bisa ditekan membaca sebagai bug, bukan sebagai disabled.
    val alpha = if (enabled) 1f else 0.45f
    val effectiveOffset = if (enabled) offset else ClayOffset.Flat

    Row(
        modifier = modifier
            .claySurface(
                shape = ClayShapes.Button,
                background = if (enabled) container else container.copy(alpha = container.alpha * alpha),
                outline = WeMadeColors.Outline.copy(alpha = alpha),
                offset = effectiveOffset,
                pressed = isPressed,
                borderWidth = ClayBorder.Medium,
                innerShade = style != ClayButtonStyle.Ghost
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
            .padding(contentPadding),
        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        leading?.invoke()
        Text(
            text = text,
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
            color = label.copy(alpha = alpha)
        )
    }
}

/**
 * Varian slot bebas, untuk toolbar yang isinya bukan sekadar teks (mis. ikon + hitungan + panah).
 */
@Composable
fun ClayActionSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = WeMadeColors.Surface,
    outlineColor: Color = WeMadeColors.Outline,
    offset: Dp = ClayOffset.Small,
    selected: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    content: @Composable RowScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    Row(
        modifier = modifier
            .claySurface(
                shape = ClayShapes.Button,
                background = containerColor,
                outline = outlineColor,
                offset = offset,
                pressed = isPressed || selected,
                borderWidth = ClayBorder.Medium
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(contentPadding),
        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

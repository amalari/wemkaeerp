package com.eventverse.app.presentation.designsystem

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Kartu clay — pengganti blok `Card(shape=…, colors=…, border=…, elevation=…)` yang sebelumnya
 * disalin ulang di ~32 call site.
 *
 * Perbedaan perilaku dari `Card` Material yang perlu diketahui:
 *
 * - **Ripple dimatikan.** Umpan balik sentuhnya adalah kartu yang bergerak masuk ke bayangannya.
 *   Ripple di atas itu membuat dua bahasa interaksi bertabrakan.
 * - **[selected] dipetakan ke state tertekan.** Kartu terpilih tampak menetap di posisi "masuk",
 *   sehingga seleksi terbaca dari bentuk, bukan cuma dari warna outline.
 * - **Kartu tidak memenuhi seluruh kotak yang dialokasikan** — [ClayOffset] di kanan dan bawah
 *   disisihkan untuk bayangan. Ini disengaja; lihat [claySurface].
 */
@Composable
fun ClayCard(
    modifier: Modifier = Modifier,
    shape: Shape = ClayShapes.Card,
    containerColor: Color = WeMadeColors.Surface,
    outlineColor: Color = WeMadeColors.Outline,
    shadowColor: Color = outlineColor,
    offset: Dp = ClayOffset.Rest,
    borderWidth: Dp = ClayBorder.Thick,
    selected: Boolean = false,
    innerShade: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(ClaySpacing.Xl),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val surface = modifier.claySurface(
        shape = shape,
        background = containerColor,
        outline = outlineColor,
        shadowColor = shadowColor,
        offset = offset,
        pressed = isPressed || selected,
        borderWidth = borderWidth,
        innerShade = innerShade
    )

    Column(
        modifier = if (onClick != null) {
            surface
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick
                )
                .padding(contentPadding)
        } else {
            surface.padding(contentPadding)
        },
        content = content
    )
}

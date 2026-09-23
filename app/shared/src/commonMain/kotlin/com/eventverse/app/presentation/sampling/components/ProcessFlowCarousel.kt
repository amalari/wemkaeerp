package com.eventverse.app.presentation.sampling.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.eventverse.app.presentation.designsystem.ClayIconButton
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.IconArrowBack
import com.eventverse.app.presentation.designsystem.IconArrowForward
import com.eventverse.app.presentation.theme.WeMadeColors
import kotlinx.coroutines.launch

/** Porsi viewport yang digeser per klik panah — sisa 20% menjaga konteks tahap sebelumnya. */
private const val PAGE_FRACTION = 0.8f

/**
 * Baris alur berbentuk carousel: isi tetap bisa di-scroll bebas, dan panah kiri/kanan menggeser
 * satu "halaman". Panah nonaktif di ujung, jadi pengguna tahu masih ada tahap tersembunyi atau tidak.
 */
@Composable
internal fun ProcessFlowCarousel(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val page = { (scrollState.viewportSize * PAGE_FRACTION).toInt() }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
    ) {
        CarouselArrow(enabled = scrollState.canScrollBackward, forward = false) {
            scope.launch { scrollState.animateScrollTo(scrollState.value - page()) }
        }
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(scrollState)
                .padding(vertical = ClaySpacing.Xs),
            horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xxs),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
        CarouselArrow(enabled = scrollState.canScrollForward, forward = true) {
            scope.launch { scrollState.animateScrollTo(scrollState.value + page()) }
        }
    }
}

@Composable
private fun CarouselArrow(enabled: Boolean, forward: Boolean, onClick: () -> Unit) {
    val tint = if (enabled) WeMadeColors.OnSurface else WeMadeColors.OnSurfaceDisabled
    ClayIconButton(onClick = onClick, enabled = enabled, size = 28.dp) {
        if (forward) {
            IconArrowForward(color = tint, modifier = Modifier.size(14.dp))
        } else {
            IconArrowBack(color = tint, modifier = Modifier.size(14.dp))
        }
    }
}

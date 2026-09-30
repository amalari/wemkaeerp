package com.eventverse.app.presentation.tutorial

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.offset
import com.eventverse.app.domain.tutorial.CalloutPlacement
import com.eventverse.app.presentation.designsystem.ClaySpotlightScrim

/**
 * Lapisan coach mark: scrim bersorot di [targetInWindow] dan callout yang diletakkan di sisinya, dijepit ke tepi
 * layar. `targetInWindow == null` = callout di tengah (langkah tanpa anchor, atau anchor tidak ditemukan).
 */
@Composable
internal fun CoachMarkOverlay(
    run: TutorialRun,
    targetInWindow: Rect?,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
) {
    var origin by remember { mutableStateOf(Offset.Zero) }
    var container by remember { mutableStateOf(IntSize.Zero) }
    var callout by remember { mutableStateOf(IntSize.Zero) }
    val gap = with(LocalDensity.current) { 16.dp.toPx() }

    val target = targetInWindow?.translate(-origin.x, -origin.y)
    val placement = if (target == null) CalloutPlacement.CENTER else run.step.placement

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { origin = it.positionInWindow() }
            .onSizeChanged { container = it }
    ) {
        ClaySpotlightScrim(target = target, modifier = Modifier.fillMaxSize())
        CoachMarkCallout(
            run = run,
            onBack = onBack,
            onNext = onNext,
            onClose = onClose,
            modifier = Modifier
                .onSizeChanged { callout = it }
                .offset { calloutPosition(placement, target, container, callout, gap) }
        )
    }
}

/** Letak kiri-atas callout: di sisi [placement] dari [target], lalu dijepit agar seluruh kartu tetap di layar. */
internal fun calloutPosition(placement: CalloutPlacement, target: Rect?, container: IntSize, callout: IntSize, gap: Float): IntOffset {
    val w = callout.width.toFloat()
    val h = callout.height.toFloat()
    val (x, y) = when {
        target == null || placement == CalloutPlacement.CENTER -> (container.width - w) / 2 to (container.height - h) / 2
        placement == CalloutPlacement.BOTTOM -> target.center.x - w / 2 to target.bottom + gap
        placement == CalloutPlacement.TOP -> target.center.x - w / 2 to target.top - gap - h
        placement == CalloutPlacement.START -> target.left - gap - w to target.center.y - h / 2
        else -> target.right + gap to target.center.y - h / 2
    }
    val maxX = (container.width - w - gap).coerceAtLeast(gap)
    val maxY = (container.height - h - gap).coerceAtLeast(gap)
    return IntOffset(x.coerceIn(gap, maxX).toInt(), y.coerceIn(gap, maxY).toInt())
}

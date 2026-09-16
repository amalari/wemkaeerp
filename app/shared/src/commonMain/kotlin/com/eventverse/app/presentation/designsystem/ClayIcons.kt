package com.eventverse.app.presentation.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Enterprise-grade Canvas vector icons that render 100% reliably in
 * Skiko / WebAssembly / Compose Multiplatform without relying on missing OS fonts or emoji tofu.
 */

@Composable
fun IconSearch(modifier: Modifier = Modifier, color: Color = WeMadeColors.OnSurfaceMuted) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.8f * density
        val radius = w * 0.30f
        val center = Offset(w * 0.42f, h * 0.42f)

        // Magnifying glass lens
        drawCircle(
            color = color,
            radius = radius,
            center = center,
            style = Stroke(width = stroke)
        )

        // Magnifying glass handle
        val handleStart = Offset(
            x = center.x + radius * 0.7071f,
            y = center.y + radius * 0.7071f
        )
        drawLine(
            color = color,
            start = handleStart,
            end = Offset(w * 0.88f, h * 0.88f),
            strokeWidth = stroke * 1.2f,
            cap = StrokeCap.Round
        )
    }
}

@Composable
fun IconEdit(modifier: Modifier = Modifier, color: Color = WeMadeColors.OnSurface) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.8f * density

        // Pencil body tilted at 45 degrees
        val path = Path().apply {
            moveTo(w * 0.65f, h * 0.15f)
            lineTo(w * 0.85f, h * 0.35f)
            lineTo(w * 0.35f, h * 0.85f)
            lineTo(w * 0.15f, h * 0.85f)
            lineTo(w * 0.15f, h * 0.65f)
            close()
        }
        drawPath(path, color = color, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))

        // Pencil tip line
        drawLine(
            color = color,
            start = Offset(w * 0.55f, h * 0.25f),
            end = Offset(w * 0.75f, h * 0.45f),
            strokeWidth = stroke * 0.7f,
            cap = StrokeCap.Round
        )
    }
}

@Composable
fun IconArchive(modifier: Modifier = Modifier, color: Color = WeMadeColors.OnSurface) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.8f * density

        // Box lid (top rectangle)
        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.12f, h * 0.18f),
            size = Size(w * 0.76f, h * 0.22f),
            cornerRadius = CornerRadius(w * 0.05f, w * 0.05f),
            style = Stroke(width = stroke)
        )

        // Box body
        val body = Path().apply {
            moveTo(w * 0.18f, h * 0.40f)
            lineTo(w * 0.18f, h * 0.82f)
            lineTo(w * 0.82f, h * 0.82f)
            lineTo(w * 0.82f, h * 0.40f)
        }
        drawPath(body, color = color, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))

        // Front handle slot
        drawLine(
            color = color,
            start = Offset(w * 0.40f, h * 0.58f),
            end = Offset(w * 0.60f, h * 0.58f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
}

@Composable
fun IconZap(modifier: Modifier = Modifier, color: Color = WeMadeColors.Accent) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.58f, h * 0.10f)
            lineTo(w * 0.22f, h * 0.54f)
            lineTo(w * 0.48f, h * 0.54f)
            lineTo(w * 0.42f, h * 0.90f)
            lineTo(w * 0.78f, h * 0.44f)
            lineTo(w * 0.52f, h * 0.44f)
            close()
        }
        drawPath(path, color = color)
    }
}

@Composable
fun IconChevronDown(modifier: Modifier = Modifier, color: Color = WeMadeColors.OnSurfaceMuted) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 2.0f * density
        val path = Path().apply {
            moveTo(w * 0.22f, h * 0.35f)
            lineTo(w * 0.50f, h * 0.65f)
            lineTo(w * 0.78f, h * 0.35f)
        }
        drawPath(path, color = color, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
fun IconChevronUp(modifier: Modifier = Modifier, color: Color = WeMadeColors.OnSurfaceMuted) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 2.0f * density
        val path = Path().apply {
            moveTo(w * 0.22f, h * 0.65f)
            lineTo(w * 0.50f, h * 0.35f)
            lineTo(w * 0.78f, h * 0.65f)
        }
        drawPath(path, color = color, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
fun IconClose(modifier: Modifier = Modifier, color: Color = WeMadeColors.OnSurface) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 2.0f * density

        drawLine(
            color = color,
            start = Offset(w * 0.22f, h * 0.22f),
            end = Offset(w * 0.78f, h * 0.78f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = color,
            start = Offset(w * 0.78f, h * 0.22f),
            end = Offset(w * 0.22f, h * 0.78f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
}

@Composable
fun IconCheck(modifier: Modifier = Modifier, color: Color = WeMadeColors.Success) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 2.0f * density
        val path = Path().apply {
            moveTo(w * 0.18f, h * 0.52f)
            lineTo(w * 0.42f, h * 0.76f)
            lineTo(w * 0.82f, h * 0.26f)
        }
        drawPath(path, color = color, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
fun IconWarning(modifier: Modifier = Modifier, color: Color = WeMadeColors.Warning) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.8f * density

        val path = Path().apply {
            moveTo(w * 0.50f, h * 0.12f)
            lineTo(w * 0.90f, h * 0.86f)
            lineTo(w * 0.10f, h * 0.86f)
            close()
        }
        drawPath(path, color = color, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))

        // Exclamation mark line
        drawLine(
            color = color,
            start = Offset(w * 0.50f, h * 0.40f),
            end = Offset(w * 0.50f, h * 0.62f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )

        // Exclamation mark dot
        drawCircle(
            color = color,
            radius = stroke * 0.65f,
            center = Offset(w * 0.50f, h * 0.74f)
        )
    }
}

@Composable
fun IconRestore(modifier: Modifier = Modifier, color: Color = WeMadeColors.Primary) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.8f * density

        // Arc (3/4 circle)
        val path = Path().apply {
            arcTo(
                rect = androidx.compose.ui.geometry.Rect(w * 0.18f, h * 0.18f, w * 0.82f, h * 0.82f),
                startAngleDegrees = 45f,
                sweepAngleDegrees = 270f,
                forceMoveTo = false
            )
        }
        drawPath(path, color = color, style = Stroke(width = stroke, cap = StrokeCap.Round))

        // Arrow head on top end
        val head = Path().apply {
            moveTo(w * 0.50f, h * 0.05f)
            lineTo(w * 0.65f, h * 0.20f)
            lineTo(w * 0.48f, h * 0.28f)
        }
        drawPath(head, color = color, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
fun IconUser(modifier: Modifier = Modifier, color: Color = WeMadeColors.OnSurfaceMuted) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.8f * density

        // Head circle
        drawCircle(
            color = color,
            radius = w * 0.20f,
            center = Offset(w * 0.50f, h * 0.32f),
            style = Stroke(width = stroke)
        )

        // Body arc
        val body = Path().apply {
            arcTo(
                rect = androidx.compose.ui.geometry.Rect(w * 0.16f, h * 0.56f, w * 0.84f, h * 1.05f),
                startAngleDegrees = 180f,
                sweepAngleDegrees = 180f,
                forceMoveTo = false
            )
        }
        drawPath(body, color = color, style = Stroke(width = stroke, cap = StrokeCap.Round))
    }
}

@Composable
fun IconMail(modifier: Modifier = Modifier, color: Color = WeMadeColors.OnSurfaceMuted) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.8f * density

        // Envelope outline
        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.12f, h * 0.22f),
            size = Size(w * 0.76f, h * 0.56f),
            cornerRadius = CornerRadius(w * 0.06f, w * 0.06f),
            style = Stroke(width = stroke)
        )

        // V fold line
        val fold = Path().apply {
            moveTo(w * 0.15f, h * 0.26f)
            lineTo(w * 0.50f, h * 0.52f)
            lineTo(w * 0.85f, h * 0.26f)
        }
        drawPath(fold, color = color, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
fun IconPhone(modifier: Modifier = Modifier, color: Color = WeMadeColors.OnSurfaceMuted) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.8f * density

        // Handset shape
        val path = Path().apply {
            moveTo(w * 0.25f, h * 0.35f)
            lineTo(w * 0.38f, h * 0.22f)
            lineTo(w * 0.52f, h * 0.36f)
            lineTo(w * 0.44f, h * 0.44f)
            cubicTo(w * 0.48f, h * 0.54f, w * 0.56f, h * 0.62f, w * 0.66f, h * 0.66f)
            lineTo(w * 0.74f, h * 0.58f)
            lineTo(w * 0.88f, h * 0.72f)
            lineTo(w * 0.75f, h * 0.85f)
            cubicTo(w * 0.50f, h * 0.85f, w * 0.25f, h * 0.60f, w * 0.25f, h * 0.35f)
            close()
        }
        drawPath(path, color = color, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
fun IconShield(modifier: Modifier = Modifier, color: Color = WeMadeColors.Primary) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.8f * density

        val path = Path().apply {
            moveTo(w * 0.50f, h * 0.12f)
            lineTo(w * 0.84f, h * 0.24f)
            cubicTo(w * 0.84f, h * 0.58f, w * 0.70f, h * 0.78f, w * 0.50f, h * 0.90f)
            cubicTo(w * 0.30f, h * 0.78f, w * 0.16f, h * 0.58f, w * 0.16f, h * 0.24f)
            close()
        }
        drawPath(path, color = color, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
fun IconLayers(modifier: Modifier = Modifier, color: Color = WeMadeColors.Primary) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.8f * density

        // Top diamond/parallelogram
        val topPath = Path().apply {
            moveTo(w * 0.50f, h * 0.15f)
            lineTo(w * 0.85f, h * 0.32f)
            lineTo(w * 0.50f, h * 0.48f)
            lineTo(w * 0.15f, h * 0.32f)
            close()
        }
        drawPath(topPath, color = color, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))

        // Middle downward chevron
        val midPath = Path().apply {
            moveTo(w * 0.15f, h * 0.52f)
            lineTo(w * 0.50f, h * 0.68f)
            lineTo(w * 0.85f, h * 0.52f)
        }
        drawPath(midPath, color = color, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))

        // Bottom downward chevron
        val botPath = Path().apply {
            moveTo(w * 0.15f, h * 0.72f)
            lineTo(w * 0.50f, h * 0.88f)
            lineTo(w * 0.85f, h * 0.72f)
        }
        drawPath(botPath, color = color, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
fun IconGlobe(modifier: Modifier = Modifier, color: Color = WeMadeColors.Primary) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.8f * density

        // Outer circle
        drawCircle(
            color = color,
            radius = w * 0.38f,
            center = Offset(w * 0.50f, h * 0.50f),
            style = Stroke(width = stroke)
        )

        // Equator horizontal line
        drawLine(
            color = color,
            start = Offset(w * 0.12f, h * 0.50f),
            end = Offset(w * 0.88f, h * 0.50f),
            strokeWidth = stroke * 0.8f,
            cap = StrokeCap.Round
        )

        // Vertical meridian ellipse
        val meridian = Path().apply {
            arcTo(
                rect = androidx.compose.ui.geometry.Rect(w * 0.32f, h * 0.12f, w * 0.68f, h * 0.88f),
                startAngleDegrees = 0f,
                sweepAngleDegrees = 360f,
                forceMoveTo = false
            )
        }
        drawPath(meridian, color = color, style = Stroke(width = stroke * 0.8f))
    }
}

@Composable
fun IconTrash(modifier: Modifier = Modifier, color: Color = WeMadeColors.Error) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.8f * density

        // Lid bar
        drawLine(
            color = color,
            start = Offset(w * 0.15f, h * 0.25f),
            end = Offset(w * 0.85f, h * 0.25f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )

        // Lid handle
        val handle = Path().apply {
            moveTo(w * 0.38f, h * 0.25f)
            lineTo(w * 0.38f, h * 0.15f)
            lineTo(w * 0.62f, h * 0.15f)
            lineTo(w * 0.62f, h * 0.25f)
        }
        drawPath(handle, color = color, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))

        // Bin body
        val bin = Path().apply {
            moveTo(w * 0.24f, h * 0.28f)
            lineTo(w * 0.28f, h * 0.85f)
            lineTo(w * 0.72f, h * 0.85f)
            lineTo(w * 0.76f, h * 0.28f)
        }
        drawPath(bin, color = color, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))

        // Vertical slat
        drawLine(
            color = color,
            start = Offset(w * 0.50f, h * 0.38f),
            end = Offset(w * 0.50f, h * 0.75f),
            strokeWidth = stroke * 0.8f,
            cap = StrokeCap.Round
        )
    }
}

@Composable
fun IconPlus(modifier: Modifier = Modifier, color: Color = WeMadeColors.Primary) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 2.0f * density

        // Horizontal line
        drawLine(
            color = color,
            start = Offset(w * 0.20f, h * 0.50f),
            end = Offset(w * 0.80f, h * 0.50f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )

        // Vertical line
        drawLine(
            color = color,
            start = Offset(w * 0.50f, h * 0.20f),
            end = Offset(w * 0.50f, h * 0.80f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
}

// ─── Module-specific Icons ────────────────────────────────────────────────────

@Composable
fun IconHandshake(modifier: Modifier = Modifier, color: Color = WeMadeColors.Primary) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.8f * density

        // Left arm
        val leftArm = Path().apply {
            moveTo(w * 0.08f, h * 0.60f)
            lineTo(w * 0.30f, h * 0.42f)
            lineTo(w * 0.46f, h * 0.50f)
        }
        drawPath(leftArm, color = color, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))

        // Right arm
        val rightArm = Path().apply {
            moveTo(w * 0.92f, h * 0.60f)
            lineTo(w * 0.70f, h * 0.42f)
            lineTo(w * 0.54f, h * 0.50f)
        }
        drawPath(rightArm, color = color, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))

        // Clasped center
        drawCircle(
            color = color,
            radius = w * 0.10f,
            center = Offset(w * 0.50f, h * 0.52f),
            style = Stroke(width = stroke)
        )

        // Grip bar
        drawLine(
            color = color,
            start = Offset(w * 0.36f, h * 0.64f),
            end = Offset(w * 0.64f, h * 0.64f),
            strokeWidth = stroke * 0.8f,
            cap = StrokeCap.Round
        )
    }
}

@Composable
fun IconRuler(modifier: Modifier = Modifier, color: Color = WeMadeColors.Primary) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.8f * density

        // Ruler body
        val ruler = Path().apply {
            moveTo(w * 0.15f, h * 0.55f)
            lineTo(w * 0.45f, h * 0.85f)
            lineTo(w * 0.85f, h * 0.45f)
            lineTo(w * 0.55f, h * 0.15f)
            close()
        }
        drawPath(ruler, color = color, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))

        // Tick marks
        listOf(
            Pair(Offset(w * 0.32f, h * 0.45f), Offset(w * 0.40f, h * 0.38f)),
            Pair(Offset(w * 0.47f, h * 0.32f), Offset(w * 0.53f, h * 0.26f)),
            Pair(Offset(w * 0.60f, h * 0.22f), Offset(w * 0.68f, h * 0.15f))
        ).forEach { (s, e) ->
            drawLine(color = color, start = s, end = e, strokeWidth = stroke * 0.8f, cap = StrokeCap.Round)
        }
    }
}

@Composable
fun IconPackage(modifier: Modifier = Modifier, color: Color = WeMadeColors.Primary) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.8f * density

        // Box body
        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.14f, h * 0.30f),
            size = Size(w * 0.72f, h * 0.58f),
            cornerRadius = CornerRadius(w * 0.04f),
            style = Stroke(width = stroke)
        )

        // Top lid
        val lid = Path().apply {
            moveTo(w * 0.14f, h * 0.30f)
            lineTo(w * 0.50f, h * 0.15f)
            lineTo(w * 0.86f, h * 0.30f)
        }
        drawPath(lid, color = color, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))

        // Center seam
        drawLine(color = color, start = Offset(w * 0.50f, h * 0.30f), end = Offset(w * 0.50f, h * 0.88f), strokeWidth = stroke * 0.7f, cap = StrokeCap.Round)

        // Tape
        drawLine(color = color, start = Offset(w * 0.28f, h * 0.56f), end = Offset(w * 0.72f, h * 0.56f), strokeWidth = stroke, cap = StrokeCap.Round)
    }
}

@Composable
fun IconClipboard(modifier: Modifier = Modifier, color: Color = WeMadeColors.Primary) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.8f * density

        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.16f, h * 0.18f),
            size = Size(w * 0.68f, h * 0.72f),
            cornerRadius = CornerRadius(w * 0.05f),
            style = Stroke(width = stroke)
        )

        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.36f, h * 0.10f),
            size = Size(w * 0.28f, h * 0.16f),
            cornerRadius = CornerRadius(w * 0.04f),
            style = Stroke(width = stroke)
        )

        listOf(0.38f, 0.52f, 0.66f).forEach { y ->
            drawLine(color = color, start = Offset(w * 0.28f, h * y), end = Offset(w * 0.72f, h * y), strokeWidth = stroke * 0.7f, cap = StrokeCap.Round)
        }
    }
}

@Composable
fun IconCalculator(modifier: Modifier = Modifier, color: Color = WeMadeColors.Primary) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.8f * density

        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.16f, h * 0.10f),
            size = Size(w * 0.68f, h * 0.80f),
            cornerRadius = CornerRadius(w * 0.07f),
            style = Stroke(width = stroke)
        )

        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.24f, h * 0.18f),
            size = Size(w * 0.52f, h * 0.18f),
            cornerRadius = CornerRadius(w * 0.03f),
            style = Stroke(width = stroke * 0.7f)
        )

        listOf(0.28f, 0.50f, 0.72f).forEach { cx ->
            listOf(0.50f, 0.63f, 0.76f).forEach { cy ->
                drawCircle(color = color, radius = w * 0.05f, center = Offset(w * cx, h * cy), style = Stroke(width = stroke * 0.8f))
            }
        }
    }
}

@Composable
fun IconCalendarGrid(modifier: Modifier = Modifier, color: Color = WeMadeColors.Primary) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.8f * density

        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.12f, h * 0.20f),
            size = Size(w * 0.76f, h * 0.68f),
            cornerRadius = CornerRadius(w * 0.05f),
            style = Stroke(width = stroke)
        )

        drawLine(color = color, start = Offset(w * 0.12f, h * 0.38f), end = Offset(w * 0.88f, h * 0.38f), strokeWidth = stroke * 0.8f, cap = StrokeCap.Round)
        drawLine(color = color, start = Offset(w * 0.32f, h * 0.12f), end = Offset(w * 0.32f, h * 0.28f), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(color = color, start = Offset(w * 0.68f, h * 0.12f), end = Offset(w * 0.68f, h * 0.28f), strokeWidth = stroke, cap = StrokeCap.Round)

        listOf(0.30f, 0.50f, 0.70f).forEach { cx ->
            listOf(0.53f, 0.68f, 0.80f).forEach { cy ->
                drawCircle(color = color, radius = w * 0.04f, center = Offset(w * cx, h * cy))
            }
        }
    }
}

@Composable
fun IconActivity(modifier: Modifier = Modifier, color: Color = WeMadeColors.Primary) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 2.0f * density

        val path = Path().apply {
            moveTo(w * 0.08f, h * 0.50f)
            lineTo(w * 0.26f, h * 0.50f)
            lineTo(w * 0.36f, h * 0.22f)
            lineTo(w * 0.48f, h * 0.78f)
            lineTo(w * 0.58f, h * 0.38f)
            lineTo(w * 0.68f, h * 0.58f)
            lineTo(w * 0.92f, h * 0.58f)
        }
        drawPath(path, color = color, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
fun IconCheckCircle(modifier: Modifier = Modifier, color: Color = WeMadeColors.Success) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.8f * density

        drawCircle(
            color = color,
            radius = w * 0.38f,
            center = Offset(w * 0.50f, h * 0.50f),
            style = Stroke(width = stroke)
        )

        val check = Path().apply {
            moveTo(w * 0.28f, h * 0.52f)
            lineTo(w * 0.44f, h * 0.68f)
            lineTo(w * 0.72f, h * 0.36f)
        }
        drawPath(check, color = color, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
fun IconMenu(modifier: Modifier = Modifier, color: Color = WeMadeColors.OnSurfaceMuted) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 2.0f * density

        listOf(0.28f, 0.50f, 0.72f).forEach { y ->
            drawLine(
                color = color,
                start = Offset(w * 0.16f, h * y),
                end = Offset(w * 0.84f, h * y),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
fun IconTruck(modifier: Modifier = Modifier, color: Color = WeMadeColors.Primary) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.8f * density

        // Cargo body
        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.06f, h * 0.26f),
            size = Size(w * 0.58f, h * 0.44f),
            cornerRadius = CornerRadius(w * 0.03f),
            style = Stroke(width = stroke)
        )

        // Cab
        val cab = Path().apply {
            moveTo(w * 0.64f, h * 0.40f)
            lineTo(w * 0.64f, h * 0.70f)
            lineTo(w * 0.92f, h * 0.70f)
            lineTo(w * 0.92f, h * 0.52f)
            lineTo(w * 0.80f, h * 0.40f)
            close()
        }
        drawPath(cab, color = color, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))

        // Axle
        drawLine(color = color, start = Offset(w * 0.06f, h * 0.70f), end = Offset(w * 0.64f, h * 0.70f), strokeWidth = stroke, cap = StrokeCap.Round)

        // Wheels
        drawCircle(color = color, radius = w * 0.09f, center = Offset(w * 0.24f, h * 0.78f), style = Stroke(width = stroke))
        drawCircle(color = color, radius = w * 0.09f, center = Offset(w * 0.76f, h * 0.78f), style = Stroke(width = stroke))
    }
}


@Composable
fun IconLock(modifier: Modifier = Modifier, color: Color = WeMadeColors.OnSurfaceMuted) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.8f * density

        // Gagang gembok (busur setengah lingkaran di atas badan)
        val shackle = Path().apply {
            moveTo(w * 0.32f, h * 0.44f)
            lineTo(w * 0.32f, h * 0.26f)
            arcTo(
                rect = Rect(w * 0.32f, h * 0.10f, w * 0.68f, h * 0.42f),
                startAngleDegrees = 180f,
                sweepAngleDegrees = 180f,
                forceMoveTo = false
            )
            lineTo(w * 0.68f, h * 0.44f)
        }
        drawPath(shackle, color = color, style = Stroke(width = stroke, cap = StrokeCap.Round))

        // Badan gembok
        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.20f, h * 0.44f),
            size = Size(w * 0.60f, h * 0.46f),
            cornerRadius = CornerRadius(w * 0.08f, w * 0.08f)
        )
    }
}

@Composable
fun IconStar(modifier: Modifier = Modifier, color: Color = WeMadeColors.Success) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f
        val outerRadius = w * 0.42f
        val innerRadius = outerRadius * 0.44f
        val path = Path().apply {
            for (i in 0 until 10) {
                val radius = if (i % 2 == 0) outerRadius else innerRadius
                val angle = (i * 36 - 90) * (kotlin.math.PI / 180.0)
                val x = cx + (radius * kotlin.math.cos(angle)).toFloat()
                val y = cy + (radius * kotlin.math.sin(angle)).toFloat()
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }
        drawPath(path, color = color)
    }
}

@Composable
fun IconInbox(modifier: Modifier = Modifier, color: Color = WeMadeColors.Primary) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 2.0f * density
        val box = Path().apply {
            moveTo(w * 0.15f, h * 0.32f)
            lineTo(w * 0.85f, h * 0.32f)
            lineTo(w * 0.85f, h * 0.80f)
            lineTo(w * 0.65f, h * 0.80f)
            lineTo(w * 0.58f, h * 0.65f)
            lineTo(w * 0.42f, h * 0.65f)
            lineTo(w * 0.35f, h * 0.80f)
            lineTo(w * 0.15f, h * 0.80f)
            close()
        }
        drawPath(box, color = color, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))

        // Down arrow into inbox
        drawLine(color = color, start = Offset(w * 0.50f, h * 0.14f), end = Offset(w * 0.50f, h * 0.48f), strokeWidth = stroke, cap = StrokeCap.Round)
        val arrow = Path().apply {
            moveTo(w * 0.38f, h * 0.38f)
            lineTo(w * 0.50f, h * 0.50f)
            lineTo(w * 0.62f, h * 0.38f)
        }
        drawPath(arrow, color = color, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
fun IconBan(modifier: Modifier = Modifier, color: Color = WeMadeColors.Error) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 2.0f * density
        val r = w * 0.36f
        drawCircle(color = color, radius = r, center = Offset(w * 0.50f, h * 0.50f), style = Stroke(width = stroke))

        val offsetVal = r * 0.7071f
        drawLine(
            color = color,
            start = Offset(w * 0.50f - offsetVal, h * 0.50f - offsetVal),
            end = Offset(w * 0.50f + offsetVal, h * 0.50f + offsetVal),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
}

@Composable
fun IconDatabase(modifier: Modifier = Modifier, color: Color = WeMadeColors.OnSurface) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.8f * density

        val left = w * 0.20f
        val right = w * 0.80f
        val top = h * 0.16f
        val ellipseH = h * 0.18f

        // Top full ellipse
        drawOval(
            color = color,
            topLeft = Offset(left, top),
            size = Size(right - left, ellipseH),
            style = Stroke(width = stroke)
        )

        // Middle curved rim
        val middleArc = Path().apply {
            moveTo(left, h * 0.48f)
            cubicTo(
                left, h * 0.48f + ellipseH * 0.6f,
                right, h * 0.48f + ellipseH * 0.6f,
                right, h * 0.48f
            )
        }
        drawPath(middleArc, color = color, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))

        // Bottom cylinder base & sides
        val baseAndSides = Path().apply {
            moveTo(left, top + ellipseH / 2f)
            lineTo(left, h * 0.76f)
            cubicTo(
                left, h * 0.76f + ellipseH * 0.6f,
                right, h * 0.76f + ellipseH * 0.6f,
                right, h * 0.76f
            )
            lineTo(right, top + ellipseH / 2f)
        }
        drawPath(baseAndSides, color = color, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
fun IconReceipt(modifier: Modifier = Modifier, color: Color = WeMadeColors.Primary) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.8f * density

        val left = w * 0.22f
        val right = w * 0.78f
        val top = h * 0.14f
        val bottom = h * 0.86f

        val path = Path().apply {
            moveTo(left, top)
            lineTo(right, top)
            lineTo(right, bottom)
            val step = (right - left) / 4f
            lineTo(right - step * 0.5f, bottom - h * 0.05f)
            lineTo(right - step * 1.0f, bottom)
            lineTo(right - step * 1.5f, bottom - h * 0.05f)
            lineTo(right - step * 2.0f, bottom)
            lineTo(right - step * 2.5f, bottom - h * 0.05f)
            lineTo(right - step * 3.0f, bottom)
            lineTo(right - step * 3.5f, bottom - h * 0.05f)
            lineTo(left, bottom)
            close()
        }
        drawPath(path, color = color, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))

        drawLine(color, Offset(left + w * 0.12f, h * 0.32f), Offset(right - w * 0.12f, h * 0.32f), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(color, Offset(left + w * 0.12f, h * 0.46f), Offset(right - w * 0.12f, h * 0.46f), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(color, Offset(left + w * 0.12f, h * 0.60f), Offset(right - w * 0.24f, h * 0.60f), strokeWidth = stroke, cap = StrokeCap.Round)
    }
}

@Composable
fun IconChat(modifier: Modifier = Modifier, color: Color = WeMadeColors.OnSurface) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.8f * density
        val path = Path().apply {
            moveTo(w * 0.20f, h * 0.16f)
            lineTo(w * 0.80f, h * 0.16f)
            quadraticTo(w * 0.90f, h * 0.16f, w * 0.90f, h * 0.28f)
            lineTo(w * 0.90f, h * 0.60f)
            quadraticTo(w * 0.90f, h * 0.72f, w * 0.80f, h * 0.72f)
            lineTo(w * 0.48f, h * 0.72f)
            lineTo(w * 0.25f, h * 0.88f)
            lineTo(w * 0.28f, h * 0.72f)
            lineTo(w * 0.20f, h * 0.72f)
            quadraticTo(w * 0.10f, h * 0.72f, w * 0.10f, h * 0.60f)
            lineTo(w * 0.10f, h * 0.28f)
            quadraticTo(w * 0.10f, h * 0.16f, w * 0.20f, h * 0.16f)
            close()
        }
        drawPath(path, color = color, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))

        val dotY = h * 0.44f
        val dotR = w * 0.045f
        drawCircle(color = color, radius = dotR, center = Offset(w * 0.32f, dotY))
        drawCircle(color = color, radius = dotR, center = Offset(w * 0.50f, dotY))
        drawCircle(color = color, radius = dotR, center = Offset(w * 0.68f, dotY))
    }
}

@Composable
fun IconNote(modifier: Modifier = Modifier, color: Color = WeMadeColors.OnSurfaceMuted) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.8f * density
        val docPath = Path().apply {
            moveTo(w * 0.20f, h * 0.12f)
            lineTo(w * 0.60f, h * 0.12f)
            lineTo(w * 0.80f, h * 0.32f)
            lineTo(w * 0.80f, h * 0.88f)
            lineTo(w * 0.20f, h * 0.88f)
            close()
        }
        drawPath(docPath, color = color, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))

        val foldPath = Path().apply {
            moveTo(w * 0.60f, h * 0.12f)
            lineTo(w * 0.60f, h * 0.32f)
            lineTo(w * 0.80f, h * 0.32f)
        }
        drawPath(foldPath, color = color, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))

        drawLine(color, Offset(w * 0.32f, h * 0.46f), Offset(w * 0.68f, h * 0.46f), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(color, Offset(w * 0.32f, h * 0.60f), Offset(w * 0.68f, h * 0.60f), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(color, Offset(w * 0.32f, h * 0.74f), Offset(w * 0.52f, h * 0.74f), strokeWidth = stroke, cap = StrokeCap.Round)
    }
}

@Composable
fun IconArrowBack(modifier: Modifier = Modifier, color: Color = WeMadeColors.OnSurface) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.8f * density
        val midY = h * 0.50f

        drawLine(
            color = color,
            start = Offset(w * 0.18f, midY),
            end = Offset(w * 0.84f, midY),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )

        val head = Path().apply {
            moveTo(w * 0.18f, midY)
            lineTo(w * 0.42f, h * 0.26f)
            moveTo(w * 0.18f, midY)
            lineTo(w * 0.42f, h * 0.74f)
        }
        drawPath(head, color = color, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
fun IconArrowForward(modifier: Modifier = Modifier, color: Color = WeMadeColors.OnSurface) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.8f * density
        val midY = h * 0.50f

        drawLine(
            color = color,
            start = Offset(w * 0.16f, midY),
            end = Offset(w * 0.82f, midY),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )

        val head = Path().apply {
            moveTo(w * 0.82f, midY)
            lineTo(w * 0.58f, h * 0.26f)
            moveTo(w * 0.82f, midY)
            lineTo(w * 0.58f, h * 0.74f)
        }
        drawPath(head, color = color, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** Panah kursor — alat "pilih & pindahkan elemen" pada kanvas template. */
@Composable
fun IconCursor(modifier: Modifier = Modifier, color: Color = WeMadeColors.Primary) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.6f * density

        val pointer = Path().apply {
            moveTo(w * 0.26f, h * 0.12f)
            lineTo(w * 0.26f, h * 0.82f)
            lineTo(w * 0.45f, h * 0.63f)
            lineTo(w * 0.58f, h * 0.90f)
            lineTo(w * 0.72f, h * 0.83f)
            lineTo(w * 0.59f, h * 0.57f)
            lineTo(w * 0.82f, h * 0.53f)
            close()
        }
        drawPath(pointer, color = color, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** Tangan terbuka — alat "geser tampilan kanvas" (pan). */
@Composable
fun IconHandMove(modifier: Modifier = Modifier, color: Color = WeMadeColors.Primary) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.6f * density

        // Telapak tangan: tiga ruas jari sejajar + ibu jari yang menekuk ke samping.
        for (i in 0..2) {
            val x = w * (0.36f + i * 0.14f)
            drawLine(
                color = color,
                start = Offset(x, h * 0.16f),
                end = Offset(x, h * 0.55f),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
        }

        val palm = Path().apply {
            moveTo(w * 0.34f, h * 0.45f)
            lineTo(w * 0.30f, h * 0.62f)
            lineTo(w * 0.20f, h * 0.56f)
            moveTo(w * 0.34f, h * 0.50f)
            lineTo(w * 0.30f, h * 0.70f)
            lineTo(w * 0.44f, h * 0.86f)
            lineTo(w * 0.78f, h * 0.86f)
            lineTo(w * 0.80f, h * 0.58f)
            lineTo(w * 0.78f, h * 0.42f)
        }
        drawPath(palm, color = color, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}


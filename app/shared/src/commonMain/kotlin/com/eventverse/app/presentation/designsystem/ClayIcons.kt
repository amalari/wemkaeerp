package com.eventverse.app.presentation.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
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

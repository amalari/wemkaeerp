package com.eventverse.app.presentation.pipeline.components

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
 * Skiko / WebAssembly without relying on missing OS emoji fonts (no tofu boxes).
 */

@Composable
fun IconHealth(modifier: Modifier = Modifier, color: Color = WeMadeColors.Success) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.8f * density

        val path = Path().apply {
            moveTo(w * 0.1f, h * 0.55f)
            lineTo(w * 0.3f, h * 0.55f)
            lineTo(w * 0.42f, h * 0.2f)
            lineTo(w * 0.58f, h * 0.85f)
            lineTo(w * 0.7f, h * 0.45f)
            lineTo(w * 0.8f, h * 0.55f)
            lineTo(w * 0.95f, h * 0.55f)
        }
        drawPath(path, color = color, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
fun IconWip(modifier: Modifier = Modifier, color: Color = WeMadeColors.Primary) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.8f * density

        // 3 stacked layers (layers of fabric / WIP)
        for (i in 0..2) {
            val yOffset = h * (0.28f + i * 0.24f)
            val path = Path().apply {
                moveTo(w * 0.15f, yOffset)
                lineTo(w * 0.5f, yOffset - h * 0.12f)
                lineTo(w * 0.85f, yOffset)
                lineTo(w * 0.5f, yOffset + h * 0.12f)
                close()
            }
            drawPath(path, color = color, style = Stroke(width = stroke, join = StrokeJoin.Round))
        }
    }
}

@Composable
fun IconWarning(modifier: Modifier = Modifier, color: Color = WeMadeColors.Warning) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.8f * density

        // Triangle
        val path = Path().apply {
            moveTo(w * 0.5f, h * 0.15f)
            lineTo(w * 0.88f, h * 0.85f)
            lineTo(w * 0.12f, h * 0.85f)
            close()
        }
        drawPath(path, color = color, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))

        // Exclamation mark line
        drawLine(
            color = color,
            start = Offset(w * 0.5f, h * 0.42f),
            end = Offset(w * 0.5f, h * 0.62f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )

        // Exclamation dot
        drawCircle(
            color = color,
            radius = stroke * 0.9f,
            center = Offset(w * 0.5f, h * 0.74f)
        )
    }
}

@Composable
fun IconClock(modifier: Modifier = Modifier, color: Color = WeMadeColors.Purple) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.8f * density

        // Circle clock face
        drawCircle(
            color = color,
            radius = w * 0.4f,
            center = Offset(w * 0.5f, h * 0.5f),
            style = Stroke(width = stroke)
        )

        // Hour & Minute hands
        val path = Path().apply {
            moveTo(w * 0.5f, h * 0.26f)
            lineTo(w * 0.5f, h * 0.5f)
            lineTo(w * 0.72f, h * 0.5f)
        }
        drawPath(path, color = color, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
fun IconPresentation(modifier: Modifier = Modifier, color: Color = Color.White) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.8f * density

        // Screen frame
        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.12f, h * 0.15f),
            size = Size(w * 0.76f, h * 0.54f),
            cornerRadius = CornerRadius(w * 0.08f, w * 0.08f),
            style = Stroke(width = stroke)
        )

        // Stand neck & base
        drawLine(
            color = color,
            start = Offset(w * 0.5f, h * 0.69f),
            end = Offset(w * 0.5f, h * 0.85f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = color,
            start = Offset(w * 0.32f, h * 0.85f),
            end = Offset(w * 0.68f, h * 0.85f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
}

@Composable
fun IconSwimlane(modifier: Modifier = Modifier, color: Color = WeMadeColors.Primary) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.5f * density

        // 3 vertical column bars
        val colWidth = w * 0.22f
        for (i in 0..2) {
            val x = w * (0.1f + i * 0.32f)
            drawRoundRect(
                color = color,
                topLeft = Offset(x, h * 0.15f),
                size = Size(colWidth, h * 0.7f),
                cornerRadius = CornerRadius(w * 0.05f, w * 0.05f),
                style = Stroke(width = stroke)
            )
        }
    }
}

@Composable
fun IconFlowGraph(modifier: Modifier = Modifier, color: Color = WeMadeColors.Primary) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.5f * density
        val nodeRadius = w * 0.12f

        // 3 circles connected with lines
        val centers = listOf(
            Offset(w * 0.2f, h * 0.5f),
            Offset(w * 0.5f, h * 0.25f),
            Offset(w * 0.8f, h * 0.5f)
        )

        drawLine(color = color, start = centers[0], end = centers[1], strokeWidth = stroke)
        drawLine(color = color, start = centers[1], end = centers[2], strokeWidth = stroke)

        centers.forEach { center ->
            drawCircle(color = color, radius = nodeRadius, center = center)
        }
    }
}

@Composable
fun IconList(modifier: Modifier = Modifier, color: Color = WeMadeColors.Primary) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.8f * density

        for (i in 0..2) {
            val y = h * (0.25f + i * 0.26f)
            // bullet
            drawCircle(color = color, radius = stroke * 0.8f, center = Offset(w * 0.18f, y))
            // line
            drawLine(
                color = color,
                start = Offset(w * 0.35f, y),
                end = Offset(w * 0.85f, y),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
fun IconUsers(modifier: Modifier = Modifier, color: Color = WeMadeColors.Primary) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.6f * density

        // Primary person head & body
        drawCircle(
            color = color,
            radius = w * 0.18f,
            center = Offset(w * 0.42f, h * 0.32f),
            style = Stroke(width = stroke)
        )
        val bodyPath = Path().apply {
            moveTo(w * 0.15f, h * 0.85f)
            arcTo(
                rect = androidx.compose.ui.geometry.Rect(w * 0.15f, h * 0.55f, w * 0.7f, h * 0.95f),
                startAngleDegrees = 180f,
                sweepAngleDegrees = 180f,
                forceMoveTo = false
            )
        }
        drawPath(bodyPath, color = color, style = Stroke(width = stroke, cap = StrokeCap.Round))

        // Secondary small person shoulder
        drawLine(
            color = color,
            start = Offset(w * 0.68f, h * 0.65f),
            end = Offset(w * 0.88f, h * 0.85f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
}

@Composable
fun IconInlet(modifier: Modifier = Modifier, color: Color = WeMadeColors.Primary) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.8f * density

        // Box
        val path = Path().apply {
            moveTo(w * 0.15f, h * 0.45f)
            lineTo(w * 0.15f, h * 0.85f)
            lineTo(w * 0.85f, h * 0.85f)
            lineTo(w * 0.85f, h * 0.45f)
        }
        drawPath(path, color = color, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))

        // Arrow pointing down into box
        drawLine(color = color, start = Offset(w * 0.5f, h * 0.15f), end = Offset(w * 0.5f, h * 0.62f), strokeWidth = stroke, cap = StrokeCap.Round)
        val arrowHead = Path().apply {
            moveTo(w * 0.32f, h * 0.46f)
            lineTo(w * 0.5f, h * 0.62f)
            lineTo(w * 0.68f, h * 0.46f)
        }
        drawPath(arrowHead, color = color, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
fun IconOutlet(modifier: Modifier = Modifier, color: Color = WeMadeColors.Success) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.8f * density

        // Box
        val path = Path().apply {
            moveTo(w * 0.15f, h * 0.45f)
            lineTo(w * 0.15f, h * 0.85f)
            lineTo(w * 0.85f, h * 0.85f)
            lineTo(w * 0.85f, h * 0.45f)
        }
        drawPath(path, color = color, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))

        // Arrow pointing up out of box
        drawLine(color = color, start = Offset(w * 0.5f, h * 0.65f), end = Offset(w * 0.5f, h * 0.18f), strokeWidth = stroke, cap = StrokeCap.Round)
        val arrowHead = Path().apply {
            moveTo(w * 0.32f, h * 0.34f)
            lineTo(w * 0.5f, h * 0.18f)
            lineTo(w * 0.68f, h * 0.34f)
        }
        drawPath(arrowHead, color = color, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
fun IconSearch(modifier: Modifier = Modifier, color: Color = WeMadeColors.Primary) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.8f * density

        drawCircle(
            color = color,
            radius = w * 0.3f,
            center = Offset(w * 0.42f, h * 0.42f),
            style = Stroke(width = stroke)
        )
        drawLine(
            color = color,
            start = Offset(w * 0.62f, h * 0.62f),
            end = Offset(w * 0.86f, h * 0.86f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
}

@Composable
fun IconCheck(modifier: Modifier = Modifier, color: Color = WeMadeColors.Success) {
    Canvas(modifier = modifier) {
        val stroke = 2.0f * density
        val path = Path().apply {
            moveTo(size.width * 0.2f, size.height * 0.52f)
            lineTo(size.width * 0.44f, size.height * 0.76f)
            lineTo(size.width * 0.82f, size.height * 0.28f)
        }
        drawPath(path, color = color, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
fun IconLightning(modifier: Modifier = Modifier, color: Color = WeMadeColors.Primary) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.55f, h * 0.1f)
            lineTo(w * 0.22f, h * 0.55f)
            lineTo(w * 0.48f, h * 0.55f)
            lineTo(w * 0.42f, h * 0.9f)
            lineTo(w * 0.78f, h * 0.42f)
            lineTo(w * 0.52f, h * 0.42f)
            close()
        }
        drawPath(path, color = color)
    }
}

@Composable
fun IconArrowRight(modifier: Modifier = Modifier, color: Color = WeMadeColors.Primary) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 2.0f * density
        // Shaft
        drawLine(
            color = color,
            start = Offset(w * 0.15f, h * 0.5f),
            end = Offset(w * 0.85f, h * 0.5f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        // Arrowhead
        val head = Path().apply {
            moveTo(w * 0.52f, h * 0.22f)
            lineTo(w * 0.85f, h * 0.5f)
            lineTo(w * 0.52f, h * 0.78f)
        }
        drawPath(head, color = color, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
fun IconArrowDown(modifier: Modifier = Modifier, color: Color = WeMadeColors.Primary) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 2.0f * density
        // Shaft
        drawLine(
            color = color,
            start = Offset(w * 0.5f, h * 0.15f),
            end = Offset(w * 0.5f, h * 0.85f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        // Arrowhead
        val head = Path().apply {
            moveTo(w * 0.22f, h * 0.52f)
            lineTo(w * 0.5f, h * 0.85f)
            lineTo(w * 0.78f, h * 0.52f)
        }
        drawPath(head, color = color, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
fun IconChevronRight(modifier: Modifier = Modifier, color: Color = WeMadeColors.Primary) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 2.0f * density
        val path = Path().apply {
            moveTo(w * 0.35f, h * 0.2f)
            lineTo(w * 0.65f, h * 0.5f)
            lineTo(w * 0.35f, h * 0.8f)
        }
        drawPath(path, color = color, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
fun IconBuilding(modifier: Modifier = Modifier, color: Color = WeMadeColors.Primary) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.6f * density

        // Main building
        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.2f, h * 0.15f),
            size = Size(w * 0.6f, h * 0.75f),
            cornerRadius = CornerRadius(w * 0.05f, w * 0.05f),
            style = Stroke(width = stroke)
        )
        // Windows
        for (row in 0..2) {
            val y = h * (0.28f + row * 0.18f)
            for (col in 0..1) {
                val x = w * (0.34f + col * 0.22f)
                drawRect(
                    color = color,
                    topLeft = Offset(x, y),
                    size = Size(w * 0.1f, h * 0.09f)
                )
            }
        }
    }
}

@Composable
fun IconEye(modifier: Modifier = Modifier, color: Color = WeMadeColors.Primary) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.5f * density

        val eyePath = Path().apply {
            moveTo(w * 0.1f, h * 0.5f)
            quadraticTo(w * 0.5f, h * 0.15f, w * 0.9f, h * 0.5f)
            quadraticTo(w * 0.5f, h * 0.85f, w * 0.1f, h * 0.5f)
            close()
        }
        drawPath(eyePath, color = color, style = Stroke(width = stroke, join = StrokeJoin.Round))

        drawCircle(
            color = color,
            radius = w * 0.16f,
            center = Offset(w * 0.5f, h * 0.5f)
        )
    }
}

@Composable
fun IconEyeOff(modifier: Modifier = Modifier, color: Color = WeMadeColors.Primary) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.5f * density

        val eyePath = Path().apply {
            moveTo(w * 0.1f, h * 0.5f)
            quadraticTo(w * 0.5f, h * 0.15f, w * 0.9f, h * 0.5f)
            quadraticTo(w * 0.5f, h * 0.85f, w * 0.1f, h * 0.5f)
            close()
        }
        drawPath(eyePath, color = color.copy(alpha = 0.55f), style = Stroke(width = stroke, join = StrokeJoin.Round))

        drawLine(
            color = color,
            start = Offset(w * 0.15f, h * 0.15f),
            end = Offset(w * 0.85f, h * 0.85f),
            strokeWidth = stroke * 1.25f,
            cap = StrokeCap.Round
        )
    }
}

@Composable
fun IconPerson(modifier: Modifier = Modifier, color: Color = WeMadeColors.Primary) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.6f * density

        // Head circle
        drawCircle(
            color = color,
            radius = w * 0.22f,
            center = Offset(w * 0.5f, h * 0.28f),
            style = Stroke(width = stroke)
        )

        // Torso / shoulders
        val bodyPath = Path().apply {
            moveTo(w * 0.16f, h * 0.88f)
            cubicTo(
                w * 0.18f, h * 0.58f,
                w * 0.32f, h * 0.56f,
                w * 0.5f, h * 0.56f
            )
            cubicTo(
                w * 0.68f, h * 0.56f,
                w * 0.82f, h * 0.58f,
                w * 0.84f, h * 0.88f
            )
        }
        drawPath(bodyPath, color = color, style = Stroke(width = stroke, cap = StrokeCap.Round))
    }
}

@Composable
fun IconZap(modifier: Modifier = Modifier, color: Color = WeMadeColors.Primary) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.5f * density

        val zapPath = Path().apply {
            moveTo(w * 0.55f, h * 0.1f)
            lineTo(w * 0.22f, h * 0.55f)
            lineTo(w * 0.48f, h * 0.55f)
            lineTo(w * 0.42f, h * 0.92f)
            lineTo(w * 0.78f, h * 0.45f)
            lineTo(w * 0.52f, h * 0.45f)
            close()
        }
        drawPath(zapPath, color = color, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
fun IconNodePort(modifier: Modifier = Modifier, color: Color = WeMadeColors.Primary) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.5f * density

        // Outer circular socket
        drawCircle(
            color = color,
            radius = w * 0.38f,
            center = Offset(w * 0.5f, h * 0.5f),
            style = Stroke(width = stroke)
        )
        // Inner connector pin
        drawCircle(
            color = color,
            radius = w * 0.18f,
            center = Offset(w * 0.5f, h * 0.5f)
        )
    }
}

@Composable
fun IconArrowRightFlow(modifier: Modifier = Modifier, color: Color = WeMadeColors.Primary) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.6f * density

        // Line
        drawLine(
            color = color,
            start = Offset(w * 0.1f, h * 0.5f),
            end = Offset(w * 0.85f, h * 0.5f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        // Arrow head
        val headPath = Path().apply {
            moveTo(w * 0.6f, h * 0.25f)
            lineTo(w * 0.88f, h * 0.5f)
            lineTo(w * 0.6f, h * 0.75f)
        }
        drawPath(headPath, color = color, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
fun IconClose(modifier: Modifier = Modifier, color: Color = WeMadeColors.OnSurfaceMuted) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.8f * density

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



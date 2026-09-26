package com.eventverse.app.presentation.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * State papan tanda tangan: daftar goresan, masing-masing rangkaian titik ternormalisasi
 * (0..1 relatif terhadap kotak papan).
 *
 * Goresan disimpan ternormalisasi supaya bentuk tanda tangannya tidak berubah saat kotaknya
 * digambar ulang dengan ukuran berbeda — dan supaya data yang diunggah ke server ringkas,
 * platform-agnostik, dan bisa dirender ulang di tempat mana pun tanpa dekoder gambar.
 */
class SignaturePadState {
    val strokes = mutableListOf<List<Offset>>()
    var current: List<Offset> = emptyList()
        private set

    val isEmpty: Boolean get() = strokes.isEmpty() && current.isEmpty()

    fun startAt(position: Offset, canvasSize: Size) {
        current = listOf(normalize(position, canvasSize))
    }

    fun dragTo(position: Offset, canvasSize: Size) {
        current = current + normalize(position, canvasSize)
    }

    fun endStroke() {
        if (current.size > 1) strokes.add(current)
        current = emptyList()
    }

    fun clear() {
        strokes.clear()
        current = emptyList()
    }

    private fun normalize(position: Offset, size: Size): Offset = Offset(
        x = (position.x / size.width).coerceIn(0f, 1f),
        y = (position.y / size.height).coerceIn(0f, 1f)
    )
}

@Composable
fun rememberSignaturePadState(): SignaturePadState = remember { SignaturePadState() }

/**
 * Papan tanda tangan — kotak gambar dengan jari/stylus, menggantikan lembar TTD kertas.
 *
 * Render digambar ulang dari titik ternormalisasi setiap frame, jadi resize jendela tidak
 * mendistorsi goresan. Encoding PNG lintas platform belum seragam di kelima target KMP, jadi
 * bukti diunggah sebagai vektor JSON (`application/json`) — bentuk tanda tangannya tetap
 * tersimpan utuh, bukan sekadar penanda "sudah ditandatangani".
 */
@Composable
fun SignaturePad(
    state: SignaturePadState,
    onCleared: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .claySurface(
                    shape = ClayShapes.Chip,
                    background = WeMadeColors.SurfaceMuted,
                    outline = WeMadeColors.Outline,
                    offset = ClayOffset.Pressed,
                    borderWidth = ClayBorder.Medium,
                    innerShade = false
                )
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            state.startAt(offset, Size(size.width.toFloat(), size.height.toFloat()))
                        },
                        onDrag = { change, _ ->
                            state.dragTo(change.position, Size(size.width.toFloat(), size.height.toFloat()))
                            change.consume()
                        },
                        onDragEnd = { state.endStroke() },
                        onDragCancel = { state.endStroke() }
                    )
                }
        ) {
            (state.strokes + listOf(state.current)).forEach { stroke ->
                if (stroke.size < 2) return@forEach
                val path = Path().apply {
                    moveTo(stroke.first().x * size.width, stroke.first().y * size.height)
                    stroke.drop(1).forEach { lineTo(it.x * size.width, it.y * size.height) }
                }
                drawPath(
                    path = path,
                    color = WeMadeColors.OnSurface,
                    style = Stroke(width = 4f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = ClaySpacing.Xs),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Tanda tangan di kotak atas dengan jari",
                fontSize = 10.sp,
                color = WeMadeColors.OnSurfaceMuted
            )
            if (!state.isEmpty) {
                Text(
                    text = "Hapus",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = WeMadeColors.Error,
                    modifier = Modifier
                        .clickable { state.clear(); onCleared() }
                        .padding(horizontal = ClaySpacing.Sm)
                )
            }
        }
    }
}

package com.eventverse.app.presentation.deal.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect as ComposeRect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.eventverse.app.presentation.designsystem.*
import com.eventverse.app.presentation.theme.WeMadeColors
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Paint
import org.jetbrains.skia.PathBuilder as SkiaPathBuilder
import org.jetbrains.skia.PathDirection
import org.jetbrains.skia.Rect as SkiaRect
import org.jetbrains.skia.SamplingMode
import org.jetbrains.skia.Surface
import org.jetbrains.skia.Image as SkiaImage

/** Sisi panjang output crop dalam piksel — cukup tajam untuk mockup kartu & preview invoice. */
private const val OUTPUT_LONG_SIDE = 1024

/** Batas zoom maksimum di atas skala cover. */
private const val MAX_ZOOM = 5f

/** Batas rotasi bebas (derajat) — di luar ini pengguna cukup memakai rotasi 90°. */
private const val MAX_FREE_ANGLE = 45f

/** Sisi panjang viewport preview dalam dp. */
private const val VIEWPORT_SIDE_DP = 340

/**
 * Preset rasio aspek crop — sepadan dengan `CropImageView.setAspectRatio` pada CanHub cropper.
 * Sisi panjang viewport tetap [VIEWPORT_SIDE_DP]; sisi pendeknya mengikuti rasio.
 */
internal enum class CropRatioPreset(val label: String, val x: Int, val y: Int) {
    R1_1("1:1", 1, 1),
    R4_3("4:3", 4, 3),
    R3_4("3:4", 3, 4),
    R16_9("16:9", 16, 9),
    R9_16("9:16", 9, 16)
}

/** Bentuk crop — sepadan dengan `CropShape.RECTANGLE` / `CropShape.OVAL` pada CanHub cropper. */
internal enum class CropShapePreset(val label: String) {
    RECTANGLE("Persegi"),
    OVAL("Oval")
}

/**
 * Membakar orientasi (putaran 90° + flip) ke bitmap baru.
 *
 * Dilakukan di luar pipeline pan/zoom supaya seluruh matematika clamping tetap bekerja di atas
 * persegi panjang axis-aligned — persis strategi "Matrix pre-rotate" yang dipakai cropper native.
 * Gagal orientasi (memori tipis) jatuh kembali ke gambar asli, bukan membatalkan crop.
 */
private fun reorientImage(image: SkiaImage, quarterTurns: Int, flipH: Boolean, flipV: Boolean): SkiaImage {
    val turns = ((quarterTurns % 4) + 4) % 4
    if (turns == 0 && !flipH && !flipV) return image
    val swap = turns % 2 == 1
    val outW = if (swap) image.height else image.width
    val outH = if (swap) image.width else image.height
    return runCatching {
        val surface = Surface.makeRasterN32Premul(outW, outH)
        val canvas = surface.canvas
        canvas.save()
        canvas.translate(outW / 2f, outH / 2f)
        canvas.rotate(turns * 90f)
        canvas.scale(if (flipH) -1f else 1f, if (flipV) -1f else 1f)
        canvas.drawImage(image, -image.width / 2f, -image.height / 2f)
        canvas.restore()
        surface.makeImageSnapshot()
    }.getOrDefault(image)
}

/**
 * Cropper foto interaktif dengan paritas fitur CanHub/Android-Image-Cropper yang diporting ke
 * Compose Multiplatform murni (Compose Canvas + Skia): rasio aspek preset (1:1, 4:3, 3:4, 16:9,
 * 9:16), rotasi 90° kiri/kanan, rotasi bebas ±45° dengan auto-fit, flip horizontal/vertikal,
 * bentuk crop persegi/oval (oval = output PNG transparan), toggle garis bantu rule-of-thirds,
 * dan tombol reset.
 *
 * Paradigmanya fixed-viewport: bingkai crop diam di tengah modal, foto di bawahnya yang
 * digeser/di-zoom/di-putar — sehingga seluruh koordinat preview bisa direplikasi 1:1 ke
 * pipeline ekspor Skia tanpa perhitungan crop-rect terpisah.
 */
@Composable
internal fun MockupCropDialog(
    bytes: ByteArray,
    onConfirm: (ByteArray) -> Unit,
    onDismiss: () -> Unit
) {
    val source = remember(bytes) { runCatching { SkiaImage.makeFromEncoded(bytes) }.getOrNull() }

    // ── State transformasi ──
    var quarterTurns by remember { mutableStateOf(0) }
    var flipH by remember { mutableStateOf(false) }
    var flipV by remember { mutableStateOf(false) }
    var freeAngle by remember { mutableStateOf(0f) }
    var ratio by remember { mutableStateOf(CropRatioPreset.R1_1) }
    var shape by remember { mutableStateOf(CropShapePreset.RECTANGLE) }
    var showGuidelines by remember { mutableStateOf(true) }
    var zoom by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val oriented = remember(source, quarterTurns, flipH, flipV) {
        source?.let { runCatching { reorientImage(it, quarterTurns, flipH, flipV) }.getOrNull() }
    }
    val orientedBitmap = remember(oriented) { oriented?.toComposeImageBitmap() }

    val density = LocalDensity.current
    val longSide = VIEWPORT_SIDE_DP.dp
    val vpWdp = if (ratio.x >= ratio.y) longSide else longSide * ratio.x / ratio.y
    val vpHdp = if (ratio.x >= ratio.y) longSide * ratio.y / ratio.x else longSide
    val vw = with(density) { vpWdp.toPx() }
    val vh = with(density) { vpHdp.toPx() }
    val imgW = oriented?.width?.toFloat() ?: 1f
    val imgH = oriented?.height?.toFloat() ?: 1f

    /**
     * Skala efektif (px viewport per px gambar). Sudut miring memperbesar skala cover dasar
     * supaya bounding box hasil rotasi tetap menutup penuh viewport (auto-fit ala CanHub:
     * `bw = w·cosθ + h·sinθ`, `bh = w·sinθ + h·cosθ`).
     */
    fun scaleFor(zoomValue: Float, angleDeg: Float): Float {
        val rad = abs(angleDeg) * (PI.toFloat() / 180f)
        val c = cos(rad)
        val s = sin(rad)
        val bboxW = imgW * c + imgH * s
        val bboxH = imgW * s + imgH * c
        return zoomValue * maxOf(vw / bboxW, vh / bboxH)
    }

    /** Offset di-clamp supaya bounding box gambar tidak pernah menyingkap celah viewport. */
    fun clamped(candidate: Offset, zoomValue: Float, angleDeg: Float): Offset {
        val rad = abs(angleDeg) * (PI.toFloat() / 180f)
        val c = cos(rad)
        val s = sin(rad)
        val f = scaleFor(zoomValue, angleDeg)
        val bboxW = (imgW * c + imgH * s) * f
        val bboxH = (imgW * s + imgH * c) * f
        return Offset(
            x = candidate.x.coerceIn(vw - bboxW, 0f),
            y = candidate.y.coerceIn(vh - bboxH, 0f)
        )
    }

    fun centeredOffset(zoomValue: Float): Offset {
        val f = scaleFor(zoomValue, freeAngle)
        return clamped(
            Offset((vw - imgW * f) / 2f, (vh - imgH * f) / 2f),
            zoomValue,
            freeAngle
        )
    }

    fun recentre() {
        zoom = 1f
        offset = centeredOffset(1f)
    }

    // Bitmap berorientasi baru (rotate/flip) atau rasio baru → kembali ke zoom 1 & posisi tengah.
    LaunchedEffect(oriented, ratio) { recentre() }

    /** Update zoom dengan titik tumpu di pusat viewport (pivot zoom), lalu di-clamp. */
    fun updateZoom(newZoom: Float) {
        val oldZoom = zoom
        val clampedZoom = newZoom.coerceIn(1f, MAX_ZOOM)
        if (clampedZoom == oldZoom) return
        val oldS = scaleFor(oldZoom, freeAngle)
        val newS = scaleFor(clampedZoom, freeAngle)
        val centerX = vw / 2f
        val centerY = vh / 2f
        val nx = centerX - (centerX - offset.x) * (newS / oldS)
        val ny = centerY - (centerY - offset.y) * (newS / oldS)
        zoom = clampedZoom
        offset = clamped(Offset(nx, ny), clampedZoom, freeAngle)
    }

    /**
     * Ekspor: seluruh transformasi preview direplikasi di atas Surface Skia berukuran output.
     * Urutan matriksnya (scale viewport→output) → (clip oval) → (rotate bebas, pivot pusat
     * viewport) → (translate offset) → drawImage — sama persis dengan urutan gambar preview,
     * jadi apa yang dilihat pengguna adalah apa yang tersimpan.
     */
    fun cropToBytes(): ByteArray {
        val img = oriented ?: return bytes
        val f = scaleFor(zoom, freeAngle)
        val outW: Int
        val outH: Int
        if (ratio.x >= ratio.y) {
            outW = OUTPUT_LONG_SIDE
            outH = OUTPUT_LONG_SIDE * ratio.y / ratio.x
        } else {
            outW = OUTPUT_LONG_SIDE * ratio.x / ratio.y
            outH = OUTPUT_LONG_SIDE
        }
        val k = outW.toFloat() / vw
        return runCatching {
            val surface = Surface.makeRasterN32Premul(outW, outH)
            val canvas = surface.canvas
            canvas.scale(k, k)
            if (shape == CropShapePreset.OVAL) {
                // Oval dipotong lewat clip — piksel di luar oval tetap transparan di PNG.
                val oval = SkiaPathBuilder()
                    .addOval(0f, 0f, vw, vh, PathDirection.CLOCKWISE)
                    .snapshot()
                canvas.clipPath(oval)
            }
            canvas.rotate(freeAngle, vw / 2f, vh / 2f)
            canvas.translate(offset.x, offset.y)
            canvas.drawImageRect(
                img,
                SkiaRect.makeXYWH(0f, 0f, imgW, imgH),
                SkiaRect.makeXYWH(0f, 0f, imgW * f, imgH * f),
                SamplingMode.LINEAR,
                Paint(),
                false
            )
            surface.makeImageSnapshot().encodeToData(EncodedImageFormat.PNG, 92)?.bytes ?: bytes
        }.getOrDefault(bytes)
    }

    Dialog(onDismissRequest = onDismiss) {
        ClayCard(
            modifier = Modifier.widthIn(min = 480.dp, max = 560.dp),
            shape = ClayShapes.Panel,
            contentPadding = PaddingValues(ClaySpacing.Xxl)
        ) {
            // ── Header: judul + rasio aktif ──
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Potong Foto",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = WeMadeColors.OnSurface,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(Modifier.width(ClaySpacing.Sm))
                ClayTag(text = "Rasio ${ratio.label}", tint = WeMadeColors.Primary)
            }
            Spacer(Modifier.height(ClaySpacing.Xs))
            Text(
                text = "Geser untuk memposisikan, cubit/slider untuk zoom. Area di dalam bingkai adalah hasil crop.",
                fontSize = 12.sp,
                color = WeMadeColors.OnSurfaceMuted
            )
            Spacer(Modifier.height(ClaySpacing.Md))

            if (source == null || orientedBitmap == null) {
                Text(
                    text = "Foto tidak dapat dibuka. Gunakan berkas PNG/JPG/WebP yang valid.",
                    fontSize = 12.sp,
                    color = WeMadeColors.Error
                )
            } else {
                // ── Viewport: bingkai crop tetap, foto di bawahnya yang bergerak ──
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(vpWdp, vpHdp)
                        .clayFlat(
                            shape = ClayShapes.Tile,
                            background = WeMadeColors.OnSurface,
                            outline = WeMadeColors.Outline
                        )
                        .pointerInput(oriented, vw, vh, freeAngle) {
                            detectTransformGestures { centroid, pan, gestureZoom, _ ->
                                if (gestureZoom != 1f) {
                                    val newZoom = (zoom * gestureZoom).coerceIn(1f, MAX_ZOOM)
                                    val oldS = scaleFor(zoom, freeAngle)
                                    val newS = scaleFor(newZoom, freeAngle)
                                    val nx = centroid.x - (centroid.x - offset.x) * (newS / oldS) + pan.x
                                    val ny = centroid.y - (centroid.y - offset.y) * (newS / oldS) + pan.y
                                    zoom = newZoom
                                    offset = clamped(Offset(nx, ny), newZoom, freeAngle)
                                } else {
                                    offset = clamped(offset + pan, zoom, freeAngle)
                                }
                            }
                        }
                ) {
                    Canvas(modifier = Modifier.matchParentSize()) {
                        val f = scaleFor(zoom, freeAngle)
                        // Foto: rotasi bebas diputar terhadap pusat viewport, lalu digeser offset.
                        withTransform({
                            rotate(freeAngle, pivot = Offset(size.width / 2f, size.height / 2f))
                            translate(offset.x, offset.y)
                        }) {
                            drawImage(
                                image = orientedBitmap,
                                dstSize = IntSize(
                                    (imgW * f).roundToInt().coerceAtLeast(1),
                                    (imgH * f).roundToInt().coerceAtLeast(1)
                                ),
                                filterQuality = FilterQuality.Medium
                            )
                        }

                        // Peredup di luar bentuk oval — sinyal visual area yang akan dibuang.
                        if (shape == CropShapePreset.OVAL) {
                            val ovalPath = Path().apply {
                                addOval(ComposeRect(0f, 0f, size.width, size.height))
                            }
                            clipPath(ovalPath, ClipOp.Difference) {
                                drawRect(Color.Black.copy(alpha = 0.55f))
                            }
                        }

                        // ── Overlay panduan crop ──
                        val dashBorder = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
                        val dashGrid = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)

                        if (showGuidelines) {
                            val gridColor = Color.White.copy(alpha = 0.45f)
                            val gridWidth = 1.dp.toPx()
                            drawLine(gridColor, Offset(size.width / 3f, 0f), Offset(size.width / 3f, size.height), gridWidth, pathEffect = dashGrid)
                            drawLine(gridColor, Offset(size.width * 2f / 3f, 0f), Offset(size.width * 2f / 3f, size.height), gridWidth, pathEffect = dashGrid)
                            drawLine(gridColor, Offset(0f, size.height / 3f), Offset(size.width, size.height / 3f), gridWidth, pathEffect = dashGrid)
                            drawLine(gridColor, Offset(0f, size.height * 2f / 3f), Offset(size.width, size.height * 2f / 3f), gridWidth, pathEffect = dashGrid)
                        }

                        if (shape == CropShapePreset.OVAL) {
                            drawOval(
                                color = Color.White.copy(alpha = 0.90f),
                                topLeft = Offset.Zero,
                                size = size,
                                style = Stroke(width = 2.dp.toPx(), pathEffect = dashBorder)
                            )
                        } else {
                            drawRect(
                                color = Color.White.copy(alpha = 0.90f),
                                style = Stroke(width = 2.dp.toPx(), pathEffect = dashBorder)
                            )
                        }

                        // Siku sudut (corner brackets) di bounding box — visual anchor ala CanHub.
                        val cornerLen = 22.dp.toPx()
                        val cornerWidth = 4.dp.toPx()
                        val cornerColor = WeMadeColors.Primary
                        drawLine(cornerColor, Offset(0f, 0f), Offset(cornerLen, 0f), cornerWidth)
                        drawLine(cornerColor, Offset(0f, 0f), Offset(0f, cornerLen), cornerWidth)
                        drawLine(cornerColor, Offset(size.width, 0f), Offset(size.width - cornerLen, 0f), cornerWidth)
                        drawLine(cornerColor, Offset(size.width, 0f), Offset(size.width, cornerLen), cornerWidth)
                        drawLine(cornerColor, Offset(0f, size.height), Offset(cornerLen, size.height), cornerWidth)
                        drawLine(cornerColor, Offset(0f, size.height), Offset(0f, size.height - cornerLen), cornerWidth)
                        drawLine(cornerColor, Offset(size.width, size.height), Offset(size.width - cornerLen, size.height), cornerWidth)
                        drawLine(cornerColor, Offset(size.width, size.height), Offset(size.width, size.height - cornerLen), cornerWidth)
                    }
                }

                Spacer(Modifier.height(ClaySpacing.Md))

                // ── Toolbar transformasi: rotasi 90°, flip H/V, reset (ala CanHub) ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ClayIconButton(onClick = { quarterTurns -= 1 }, shape = ClayShapes.Tile) {
                        IconRotateCcw(Modifier.size(14.dp))
                    }
                    ClayIconButton(onClick = { quarterTurns += 1 }, shape = ClayShapes.Tile) {
                        IconRotateCw(Modifier.size(14.dp))
                    }
                    ClayIconButton(
                        onClick = { flipH = !flipH },
                        shape = ClayShapes.Tile,
                        containerColor = if (flipH) WeMadeColors.Primary else WeMadeColors.SurfaceMuted
                    ) {
                        IconFlipHorizontal(Modifier.size(14.dp), color = if (flipH) Color.White else WeMadeColors.OnSurface)
                    }
                    ClayIconButton(
                        onClick = { flipV = !flipV },
                        shape = ClayShapes.Tile,
                        containerColor = if (flipV) WeMadeColors.Primary else WeMadeColors.SurfaceMuted
                    ) {
                        IconFlipVertical(Modifier.size(14.dp), color = if (flipV) Color.White else WeMadeColors.OnSurface)
                    }
                    ClayIconButton(
                        onClick = {
                            quarterTurns = 0
                            flipH = false
                            flipV = false
                            freeAngle = 0f
                            zoom = 1f
                            offset = centeredOffset(1f)
                        },
                        shape = ClayShapes.Tile
                    ) {
                        IconRestore(Modifier.size(14.dp))
                    }
                }

                Spacer(Modifier.height(ClaySpacing.Sm))

                // ── Zoom interaktif (+, -, slider, persentase & pusatkan) ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
                ) {
                    ClayIconButton(onClick = { updateZoom(zoom - 0.25f) }, enabled = zoom > 1f) {
                        IconMinus(Modifier.size(13.dp))
                    }
                    Slider(
                        value = zoom,
                        onValueChange = { updateZoom(it) },
                        valueRange = 1f..MAX_ZOOM,
                        modifier = Modifier.weight(1f)
                    )
                    ClayIconButton(onClick = { updateZoom(zoom + 0.25f) }, enabled = zoom < MAX_ZOOM) {
                        IconPlus(Modifier.size(13.dp))
                    }
                    ClayTag(text = "${(zoom * 100).roundToInt()}%", tint = WeMadeColors.Primary)
                    ClayButton(
                        text = "Pusatkan",
                        onClick = {
                            zoom = 1f
                            offset = centeredOffset(1f)
                        },
                        style = ClayButtonStyle.Secondary,
                        fontSize = 11.sp
                    )
                }

                Spacer(Modifier.height(ClaySpacing.Sm))

                // ── Rotasi bebas ±45° dengan auto-fit (pivot: pusat viewport) ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
                ) {
                    Text(text = "Miring", fontSize = 11.sp, color = WeMadeColors.OnSurfaceMuted)
                    Slider(
                        value = freeAngle,
                        onValueChange = { newAngle ->
                            // Jaga pusat viewport tetap diam selama sudut berubah (pivot rotation).
                            val oldS = scaleFor(zoom, freeAngle)
                            val newS = scaleFor(zoom, newAngle)
                            val cx = vw / 2f
                            val cy = vh / 2f
                            val nx = cx - (cx - offset.x) * (newS / oldS)
                            val ny = cy - (cy - offset.y) * (newS / oldS)
                            freeAngle = newAngle
                            offset = clamped(Offset(nx, ny), zoom, newAngle)
                        },
                        valueRange = -MAX_FREE_ANGLE..MAX_FREE_ANGLE,
                        modifier = Modifier.weight(1f)
                    )
                    ClayTag(text = "${freeAngle.roundToInt()}°", tint = WeMadeColors.Primary)
                }

                Spacer(Modifier.height(ClaySpacing.Sm))

                // ── Rasio aspek preset (CanHub: setAspectRatio) ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)
                ) {
                    CropRatioPreset.values().forEach { preset ->
                        ClayButton(
                            text = preset.label,
                            onClick = { ratio = preset },
                            style = if (ratio == preset) ClayButtonStyle.Primary else ClayButtonStyle.Secondary,
                            fontSize = 10.sp,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(Modifier.height(ClaySpacing.Sm))

                // ── Bentuk crop & garis bantu (CanHub: CropShape & Guidelines) ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CropShapePreset.values().forEach { preset ->
                        ClayButton(
                            text = preset.label,
                            onClick = { shape = preset },
                            style = if (shape == preset) ClayButtonStyle.Primary else ClayButtonStyle.Secondary,
                            fontSize = 10.sp,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    ClayButton(
                        text = if (showGuidelines) "Grid Nyala" else "Grid Mati",
                        onClick = { showGuidelines = !showGuidelines },
                        style = ClayButtonStyle.Secondary,
                        fontSize = 10.sp,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            Spacer(Modifier.height(ClaySpacing.Lg))

            // ── Tombol Batal & Gunakan Foto ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                ClayButton(
                    text = "Batal",
                    onClick = onDismiss,
                    style = ClayButtonStyle.Ghost,
                    fontSize = 12.sp
                )
                Spacer(Modifier.width(ClaySpacing.Sm))
                ClayButton(
                    text = "Gunakan Foto",
                    onClick = { onConfirm(cropToBytes()) },
                    enabled = source != null,
                    style = ClayButtonStyle.Primary,
                    fontSize = 12.sp
                )
            }
        }
    }
}






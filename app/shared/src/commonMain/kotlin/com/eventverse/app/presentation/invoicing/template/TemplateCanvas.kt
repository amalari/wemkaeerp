package com.eventverse.app.presentation.invoicing.template

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign as ComposeTextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.domain.common.Money
import com.eventverse.app.domain.invoicing.Invoice
import com.eventverse.app.domain.invoicing.template.*
import com.eventverse.app.presentation.designsystem.ClayBorder
import com.eventverse.app.presentation.designsystem.ClayOffset
import com.eventverse.app.presentation.designsystem.ClayShapes
import com.eventverse.app.presentation.designsystem.claySurface
import com.eventverse.app.presentation.theme.WeMadeColors
import kotlin.math.roundToInt

/**
 * Kanvas A4 tempat template faktur disusun.
 *
 * Dua hal yang membuat kanvas ini bisa dipakai, dan keduanya sengaja dipisah tegas:
 *
 * 1. **Titik nol koordinat = sudut kiri-atas lembar.** Lapisan dekorasi (kertas + hard shadow)
 *    dan lapisan isi (grid + elemen) adalah dua `Box` terpisah. Kalau digabung, `padding`
 *    reservasi bayangan milik [claySurface] ikut menggeser titik nol elemen sejauh 6dp — kanvas
 *    akan menampilkan elemen 2 mm lebih ke kanan-bawah daripada hasil cetak PDF-nya.
 * 2. **Posisi elemen selalu berasal dari state.** Tidak ada salinan posisi lokal yang dipegang
 *    selama digeser; setiap frame tarikan mengirim rect absolut hasil [TemplateRect.movedBy],
 *    lalu Compose menggambar dari state terbaru. Salinan lokal yang tidak pernah diperbarui
 *    inilah yang dulu membuat elemen selalu mental kembali ke titik awalnya.
 */
@Composable
fun TemplateCanvas(
    state: TemplateDesignerUiState,
    onEvent: (TemplateDesignerUiEvent) -> Unit,
    horizontalScroll: ScrollState,
    verticalScroll: ScrollState,
    modifier: Modifier = Modifier
) {
    val zoomFactor = state.zoomPercent / 100f
    val mmToDp = 3f * zoomFactor // 1mm = 3dp at 100% zoom
    val paperSize = state.template.paperSize
    val paperWidthDp = (paperSize.width.value / 10f * mmToDp).dp
    val paperHeightDp = (paperSize.height.value / 10f * mmToDp).dp
    val selectedElementId = state.selectedElementId
    val snapMm10 = state.snapGridMm * 10
    val invoice = state.previewInvoice
    val isSelectTool = state.canvasTool == CanvasTool.SELECT

    val focusRequester = remember { FocusRequester() }

    // Kertas menerima fokus setiap kali pilihan berubah, supaya tombol panah langsung bisa dipakai
    // untuk menggeser elemen terpilih tanpa perlu mengeklik kertas lebih dulu.
    //
    // Kunci efek ini juga memuat `canvasTool`. Menekan tombol apa pun di toolbar memindahkan fokus
    // Compose ke tombol itu, dan selama fokus di sana seluruh pintasan papan-tik (panah, Delete,
    // Escape) mati diam-diam — pengguna hanya melihat "tombol panah tidak jalan". Mengembalikan
    // fokus setiap kali alat berganti membuat pintasan itu hidup lagi tanpa perlu mengeklik kertas.
    LaunchedEffect(selectedElementId, state.canvasTool) {
        if (selectedElementId != null) runCatching { focusRequester.requestFocus() }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(ClayOffset.Rest)
            .pointerInput(Unit) {
                // Tarikan yang tidak dikonsumsi elemen = menggeser viewport kanvas (pan).
                //
                // Pada mode PAN elemen tidak memasang handler apa pun, jadi tarikan di atas elemen
                // pun sampai ke sini. Pada mode SELECT, detektor elemen lebih dulu mengonsumsi
                // gerakan (urutan dispatch Compose berjalan dari anak ke induk), dan
                // `detectDragGestures` otomatis membatalkan drag-nya begitu mendeteksi konsumsi.
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    horizontalScroll.dispatchRawDelta(-dragAmount.x)
                    verticalScroll.dispatchRawDelta(-dragAmount.y)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        // Kotak pembungkus dilebihkan sebesar jarak bayangan: claySurface memakai dp pertama
        // sebagai ruang gambar bayangan, bukan sebagai bagian dari lembar kertasnya.
        Box(modifier = Modifier.size(paperWidthDp + ClayOffset.Rest, paperHeightDp + ClayOffset.Rest)) {
            // 1. Dekorasi: lembar kertas + hard shadow. Diletakkan lebih dulu agar di belakang.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .claySurface(
                        shape = ClayShapes.Paper,
                        background = WeMadeColors.Surface,
                        outline = WeMadeColors.Outline,
                        offset = ClayOffset.Rest,
                        borderWidth = ClayBorder.Medium
                    )
            )

            // 2. Isi kertas: ukuran persis 210 × 297 mm, titik nol (0,0) = sudut kiri-atas lembar.
            Box(
                modifier = Modifier
                    .size(paperWidthDp, paperHeightDp)
                    // Ketuk bidang kosong = lepas pilihan, sehingga panel kanan kembali ke
                    // Pengaturan Template. Tarikan tidak dihitung ketuk: pan di atas sudah
                    // mengonsumsi geraknya dan detektor ketuk batal dengan sendirinya.
                    //
                    // Ketukan di mode Geser Kanvas sengaja **tidak** melepas pilihan: alat itu
                    // dipakai untuk menggeser pandangan, bukan untuk mengubah apa yang sedang
                    // disunting. Pilihan yang hilang karena menyentuh kanvas akan terasa seperti
                    // panel properti yang tiba-tiba mengosongkan diri.
                    .pointerInput(isSelectTool) {
                        detectTapGestures {
                            runCatching { focusRequester.requestFocus() }
                            if (isSelectTool) {
                                onEvent(TemplateDesignerUiEvent.SelectElement(null))
                            }
                        }
                    }
                    .focusRequester(focusRequester)
                    .focusable()
                    .onPreviewKeyEvent { event -> handleCanvasKeyEvent(event, state, onEvent) }
            ) {
                // Background Grid (10mm squares) — hanya kalau diminta. Bawaannya mati: mesh
                // 21 × 30 kotak terbaca lebih ramai daripada isi fakturnya sendiri.
                if (state.showGrid) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val stepPx = 10f * mmToDp * density
                        var x = stepPx
                        while (x < size.width) {
                            drawLine(
                                color = WeMadeColors.Border,
                                start = Offset(x, 0f),
                                end = Offset(x, size.height),
                                strokeWidth = 1f
                            )
                            x += stepPx
                        }
                        var y = stepPx
                        while (y < size.height) {
                            drawLine(
                                color = WeMadeColors.Border,
                                start = Offset(0f, y),
                                end = Offset(size.width, y),
                                strokeWidth = 1f
                            )
                            y += stepPx
                        }
                    }
                }

                // Elements Layer
                state.template.elements.forEach { element ->
                    key(element.elementId) {
                        CanvasElementNode(
                            element = element,
                            isSelected = element.elementId == selectedElementId,
                            // Mode Geser mematikan handler elemen supaya tarikan di titik mana pun
                            // menjadi pan, bukan pemindahan elemen.
                            isInteractive = isSelectTool,
                            mmToDp = mmToDp,
                            zoomFactor = zoomFactor,
                            invoice = invoice,
                            snapMm10 = snapMm10,
                            paperSize = paperSize,
                            onEvent = onEvent
                        )
                    }
                }
            }
        }
    }
}

/**
 * Pemetaan tombol panah & tombol hapus pada kanvas.
 *
 * Nudge lewat tombol panah sengaja tidak ditumpangkan pada `detectDragGestures`: setelah satu
 * klik pada elemen, presisi 1 mm tidak lagi bergantung pada kestabilan tangan saat menyeret mouse.
 * `Shift` + panah = lompatan 10× untuk memindahkan blok besar tanpa puluhan penekanan.
 */
private fun handleCanvasKeyEvent(
    event: KeyEvent,
    state: TemplateDesignerUiState,
    onEvent: (TemplateDesignerUiEvent) -> Unit
): Boolean {
    if (event.type != KeyEventType.KeyDown) return false

    val selected = state.selectedElement

    when (event.key) {
        Key.Escape -> {
            onEvent(TemplateDesignerUiEvent.SelectElement(null))
            return true
        }
        Key.Delete -> {
            if (selected == null) return false
            onEvent(TemplateDesignerUiEvent.DeleteElement(selected.elementId))
            return true
        }
        else -> Unit
    }

    if (selected == null) return false

    val baseStep = state.snapGridMm.coerceAtLeast(1) * 10
    val step = if (event.isShiftPressed) baseStep * 10 else baseStep

    val dx: Int
    val dy: Int
    when (event.key) {
        Key.DirectionLeft -> {
            dx = -step; dy = 0
        }
        Key.DirectionRight -> {
            dx = step; dy = 0
        }
        Key.DirectionUp -> {
            dx = 0; dy = -step
        }
        Key.DirectionDown -> {
            dx = 0; dy = step
        }
        else -> return false
    }

    onEvent(TemplateDesignerUiEvent.MoveElementBy(selected.elementId, dx, dy))
    return true
}

/**
 * Satu elemen di atas kertas: posisi, garis pilihan, dan gestur tarik.
 *
 * `dragBase` diambil dari posisi terbaru **saat tarikan dimulai** dan tidak pernah dibaca ulang
 * dari state selama tarikan berlangsung. Inilah kunci agar perpindahan tidak berbalik arah:
 * menghitung dari rect yang sudah basi membuat setiap langkah menimpa langkah sebelumnya, dan
 * elemen hanya bergerak beberapa milimeter lalu mental kembali — persis gejala "tidak bisa
 * digeser" yang dulu terlihat di kanvas.
 *
 * Snap grid **dimatikan selama tarikan** dan baru diterapkan saat jari/mouse dilepas. Hasilnya
 * gerakan mengikuti kursor 1:1 (mulus), lalu "menempel" ke grid dengan satu lompatan magnetik.
 */
@Composable
private fun CanvasElementNode(
    element: TemplateElement,
    isSelected: Boolean,
    isInteractive: Boolean,
    mmToDp: Float,
    zoomFactor: Float,
    invoice: Invoice,
    snapMm10: Int,
    paperSize: PaperSize,
    onEvent: (TemplateDesignerUiEvent) -> Unit
) {
    val renderRect by rememberUpdatedState(element.rect)

    val widthDp = (element.rect.width.value / 10f * mmToDp).dp
    val heightDp = (element.rect.height.value / 10f * mmToDp).dp

    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    x = (renderRect.x.value / 10f * mmToDp).dp.roundToPx(),
                    y = (renderRect.y.value / 10f * mmToDp).dp.roundToPx()
                )
            }
            .size(width = widthDp, height = heightDp)
            .then(
                if (isSelected) {
                    Modifier.border(ClayBorder.Thick, WeMadeColors.Primary, ClayShapes.Element)
                } else {
                    Modifier.border(
                        ClayBorder.Hairline,
                        WeMadeColors.OnSurfaceDisabled.copy(alpha = 0.35f),
                        ClayShapes.Element
                    )
                }
            )
            .then(
                if (isInteractive) {
                    Modifier.elementDragModifier(element.elementId, mmToDp, snapMm10, paperSize, onEvent) { renderRect }
                } else {
                    Modifier
                }
            )
            .then(
                if (isInteractive) {
                    Modifier.clickable { onEvent(TemplateDesignerUiEvent.SelectElement(element.elementId)) }
                } else {
                    Modifier
                }
            )
            .padding(2.dp)
    ) {
        RenderElementContent(
            element = element,
            invoice = invoice,
            zoomFactor = zoomFactor
        )

        // Penanda sudut saat elemen terpilih.
        if (isSelected) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .align(Alignment.BottomEnd)
                    .background(WeMadeColors.Primary, ClayShapes.Element)
            )
        }
    }
}

/**
 * Menerjemahkan perpindahan piksel (dari `PointerInputChange`) menjadi rect milimeter.
 *
 * Aturan snap dan penjepitan ke kertas dipinjam dari [TemplateRect.movedBy] di lapisan domain,
 * sehingga jalur drag dan jalur tombol panah tidak dapat menghasilkan posisi yang berbeda untuk
 * perpindahan yang sama.
 */
private fun TemplateRect.advancedByPixels(
    delta: Offset,
    mmToDp: Float,
    snapMm10: Int,
    paperSize: PaperSize
): TemplateRect = movedBy(
    dx = Mm10((delta.x / mmToDp * 10f).roundToInt()),
    dy = Mm10((delta.y / mmToDp * 10f).roundToInt()),
    snapMm10 = snapMm10,
    paperWidth = paperSize.width,
    paperHeight = paperSize.height
)

/**
 * Modifier tarik satu elemen.
 *
 * Dipisah dari [CanvasElementNode] karena `pointerInput` menyimpan lambda beserta closure-nya
 * selama kuncinya tidak berubah. Posisi awal tarikan disimpan **di dalam** closure ini
 * (`dragBase`) dan aslinya dibaca lewat [latestRect] — pembaca yang selalu menunjuk state
 * terbaru. Membaca rect dari nilai yang di-capture saat komposisi pertama adalah akar bug
 * "elemen tidak bisa digeser".
 */
private fun Modifier.elementDragModifier(
    elementId: String,
    mmToDp: Float,
    snapMm10: Int,
    paperSize: PaperSize,
    onEvent: (TemplateDesignerUiEvent) -> Unit,
    latestRect: () -> TemplateRect
): Modifier = this.pointerInput(elementId, mmToDp, snapMm10, paperSize) {
    var dragBase = latestRect()
    var dragTotal = Offset.Zero

    detectDragGestures(
        onDragStart = {
            dragBase = latestRect()
            dragTotal = Offset.Zero
            onEvent(TemplateDesignerUiEvent.SelectElement(elementId))
        },
        onDrag = { change, dragAmount ->
            change.consume()
            dragTotal += dragAmount
            // Snap dimatikan selama tarikan supaya gerakan mengikuti kursor 1:1.
            onEvent(
                TemplateDesignerUiEvent.UpdateElementRect(
                    elementId = elementId,
                    newBounds = dragBase.advancedByPixels(dragTotal, mmToDp, snapMm10 = 0, paperSize = paperSize)
                )
            )
        },
        onDragEnd = {
            // Satu lompatan magnetik terakhir: posisi akhir dikunci ke grid & dijepit ke kertas.
            onEvent(
                TemplateDesignerUiEvent.UpdateElementRect(
                    elementId = elementId,
                    newBounds = dragBase.advancedByPixels(dragTotal, mmToDp, snapMm10, paperSize)
                )
            )
            dragTotal = Offset.Zero
        },
        onDragCancel = { dragTotal = Offset.Zero }
    )
}

@Composable
private fun RenderElementContent(
    element: TemplateElement,
    invoice: Invoice,
    zoomFactor: Float
) {
    val totalPaid = Money.idr(0)

    when (element) {
        is TemplateElement.StaticText -> {
            Text(
                text = element.text,
                fontSize = (element.style.fontSizePt * zoomFactor).sp,
                fontWeight = if (element.style.isBold) FontWeight.Bold else FontWeight.Normal,
                color = Color(element.style.colorHex),
                textAlign = toComposeTextAlign(element.style.align),
                modifier = Modifier.fillMaxSize()
            )
        }
        is TemplateElement.BoundField -> {
            val resolved = InvoiceBindingResolver.resolve(element.binding, invoice, null, totalPaid)
            val valueText = when (resolved) {
                is ResolvedBindingValue.Text -> resolved.value
                is ResolvedBindingValue.Image -> "[Logo]"
                ResolvedBindingValue.Empty -> "{{${element.binding.value}}}"
            }
            val displayText = "${element.prefix}$valueText${element.suffix}"
            Text(
                text = displayText,
                fontSize = (element.style.fontSizePt * zoomFactor).sp,
                fontWeight = if (element.style.isBold) FontWeight.Bold else FontWeight.Normal,
                color = Color(element.style.colorHex),
                textAlign = toComposeTextAlign(element.style.align),
                modifier = Modifier.fillMaxSize()
            )
        }
        is TemplateElement.RectShape -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (element.strokeHex != null && element.strokeMm10 > 0) {
                            Modifier.border(1.dp, Color(element.strokeHex!!))
                        } else Modifier
                    )
                    .then(
                        if (element.fillHex != null) {
                            Modifier.background(Color(element.fillHex!!))
                        } else Modifier
                    )
            )
        }
        is TemplateElement.LineShape -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(Color(element.strokeHex))
            )
        }
        is TemplateElement.ImageBox -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(WeMadeColors.SurfaceMuted)
                    .border(1.dp, WeMadeColors.Border),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "LOGO PERUSAHAAN",
                    fontSize = (9 * zoomFactor).sp,
                    fontWeight = FontWeight.Bold,
                    color = WeMadeColors.OnSurfaceMuted
                )
            }
        }
        is TemplateElement.ItemTable -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(WeMadeColors.Surface)
                    .border(1.dp, WeMadeColors.Border)
            ) {
                // Table Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(WeMadeColors.PrimaryContainer)
                        .padding(vertical = 4.dp, horizontal = 6.dp)
                ) {
                    element.columns.forEach { col ->
                        val weight = (col.widthRatio.numerator.toFloat() / col.widthRatio.denominator.toFloat()).coerceAtLeast(0.05f)
                        Text(
                            text = col.header,
                            fontSize = (element.headerStyle.fontSizePt * zoomFactor).sp,
                            fontWeight = if (element.headerStyle.isBold) FontWeight.Bold else FontWeight.Normal,
                            color = Color(element.headerStyle.colorHex),
                            textAlign = toComposeTextAlign(col.align),
                            modifier = Modifier.weight(weight)
                        )
                    }
                }

                // Sample Rows (from preview invoice)
                invoice.lines.forEach { line ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp, horizontal = 6.dp)
                    ) {
                        element.columns.forEach { col ->
                            val weight = (col.widthRatio.numerator.toFloat() / col.widthRatio.denominator.toFloat()).coerceAtLeast(0.05f)
                            val res = InvoiceBindingResolver.resolve(col.binding, invoice, line, totalPaid)
                            val value = when (res) {
                                is ResolvedBindingValue.Text -> res.value
                                else -> "-"
                            }
                            Text(
                                text = value,
                                fontSize = (element.bodyStyle.fontSizePt * zoomFactor).sp,
                                fontWeight = if (element.bodyStyle.isBold) FontWeight.Bold else FontWeight.Normal,
                                color = Color(element.bodyStyle.colorHex),
                                textAlign = toComposeTextAlign(col.align),
                                modifier = Modifier.weight(weight)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun toComposeTextAlign(align: TextAlign): ComposeTextAlign = when (align) {
    TextAlign.LEFT -> ComposeTextAlign.Left
    TextAlign.CENTER -> ComposeTextAlign.Center
    TextAlign.RIGHT -> ComposeTextAlign.Right
}

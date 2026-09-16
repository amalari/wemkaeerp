package com.eventverse.app.presentation.invoicing.template

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign as ComposeTextAlign
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.domain.common.Money
import com.eventverse.app.domain.invoicing.Invoice
import com.eventverse.app.domain.invoicing.template.*
import com.eventverse.app.presentation.designsystem.ClayBorder
import com.eventverse.app.presentation.designsystem.ClayOffset
import com.eventverse.app.presentation.designsystem.ClayShapes
import com.eventverse.app.presentation.designsystem.clayFlat
import com.eventverse.app.presentation.designsystem.claySurface
import com.eventverse.app.presentation.designsystem.rememberFredokaFamily
import com.eventverse.app.presentation.designsystem.rememberNunitoFamily
import com.eventverse.app.presentation.theme.WeMadeColors
import androidx.compose.ui.text.font.FontFamily
import kotlin.math.roundToInt

/**
 * Kanvas A4 tempat template faktur disusun.
 *
 * Empat hal yang membuat kanvas ini bisa dipercaya, dan semuanya sengaja dipisah tegas:
 *
 * 1. **Titik nol koordinat = sudut kiri-atas lembar.** Lapisan dekorasi (kertas + hard shadow) dan
 *    lapisan isi (grid + elemen) adalah dua `Box` terpisah. Kalau digabung, `padding` reservasi
 *    bayangan milik [claySurface] ikut menggeser titik nol elemen sejauh 6dp — kanvas akan
 *    menunjukkan elemen 2 mm lebih ke kanan-bawah daripada hasil cetak PDF-nya.
 * 2. **Geometri berasal dari [InvoiceDocumentLayout], bukan dari `element.rect` mentah.** Tinggi teks
 *    diturunkan dari isinya, tabel item tumbuh mengikuti jumlah baris faktur, dan elemen ber-anchor
 *    ikut bergeser. Renderer PDF memakai penyelesai yang sama, sehingga yang terlihat di sini adalah
 *    yang akan tercetak.
 * 3. **Baris teks digambar apa adanya dari penyelesai**, bukan diserahkan ke mesin teks Compose.
 *    Kalau Compose memutus baris dengan metrik Skia-nya sendiri, jumlah baris di kanvas bisa berbeda
 *    dari PDF untuk isi yang sama.
 * 4. **Posisi elemen selalu berasal dari state.** Tidak ada salinan posisi lokal yang dipegang selama
 *    digeser; setiap frame tarikan mengirim rect absolut hasil [TemplateRect.movedBy], lalu Compose
 *    menggambar dari state terbaru.
 *
 * [viewportSize] dikirim dari layar induk yang sudah mengukur area gulir, karena `BoxWithConstraints`
 * yang diletakkan **di dalam** wadah bergulir selalu melihat lebar/tinggi tak hingga dan tidak akan
 * pernah bisa memusatkan kertas.
 */
@Composable
fun TemplateCanvas(
    state: TemplateDesignerUiState,
    onEvent: (TemplateDesignerUiEvent) -> Unit,
    horizontalScroll: ScrollState,
    verticalScroll: ScrollState,
    viewportSize: DpSize,
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

    // Geometri efektif dihitung sekali per perubahan template/faktur, bukan per elemen: menariknya
    // ke dalam loop membuat pengukuran teks berjalan belasan kali di setiap recomposition.
    val laidOutElements = remember(state.template, invoice) {
        InvoiceDocumentLayout.solve(state.template, invoice)
    }

    // Kertas yang lebih kecil dari viewport dimusatkan dengan memberi konten ukuran minimum sebesar
    // viewport; kalau kertas lebih besar, ukuran kertaspun yang menang dan area bergulir bekerja.
    val contentWidth = maxOf(paperWidthDp + ClayOffset.Rest, viewportSize.width)
    val contentHeight = maxOf(paperHeightDp + ClayOffset.Rest, viewportSize.height)

    val focusRequester = remember { FocusRequester() }

    // Kertas menerima fokus setiap kali pilihan berubah, supaya tombol panah langsung bisa dipakai
    // untuk menggeser elemen terpilih tanpa perlu mengeklik kertas lebih dulu.
    //
    // Kunci efek ini juga memuat `canvasTool`. Menekan tombol apa pun di panel/palet memindahkan fokus
    // Compose ke tombol itu, dan selama fokus di sana seluruh pintasan papan-tik (panah, Delete,
    // Escape) mati diam-diam — pengguna hanya melihat "tombol panah tidak jalan". Mengembalikan fokus
    // setiap kali alat berganti membuat pintasan itu hidup lagi tanpa perlu mengeklik kertas.
    LaunchedEffect(selectedElementId, state.canvasTool, state.editingTextElementId) {
        if (selectedElementId != null && state.editingTextElementId == null) {
            runCatching { focusRequester.requestFocus() }
        }
    }

    Box(
        modifier = modifier
            .size(contentWidth, contentHeight)
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

            ProvideInvoiceFontFaces {
                PaperContent(
                    state = state,
                    laidOutElements = laidOutElements,
                    paperWidthDp = paperWidthDp,
                    paperHeightDp = paperHeightDp,
                    mmToDp = mmToDp,
                    zoomFactor = zoomFactor,
                    invoice = invoice,
                    snapMm10 = snapMm10,
                    paperSize = paperSize,
                    isSelectTool = isSelectTool,
                    focusRequester = focusRequester,
                    onEvent = onEvent
                )
            }
        }
    }
}

/**
 * Isi lembar: grid, elemen, dan editor langsung.
 *
 * Dipisah dari [TemplateCanvas] karena dua lapisan ini punya dua hal yang tidak boleh tercampur:
 * dekorasi memakai ukuran kotak + bayangan, sedangkan isi memakai koordinat milimeter absolut.
 */
@Composable
private fun PaperContent(
    state: TemplateDesignerUiState,
    laidOutElements: List<LaidOutElement>,
    paperWidthDp: androidx.compose.ui.unit.Dp,
    paperHeightDp: androidx.compose.ui.unit.Dp,
    mmToDp: Float,
    zoomFactor: Float,
    invoice: Invoice,
    snapMm10: Int,
    paperSize: PaperSize,
    isSelectTool: Boolean,
    focusRequester: FocusRequester,
    onEvent: (TemplateDesignerUiEvent) -> Unit
) {
    Box(
        modifier = Modifier
            .size(paperWidthDp, paperHeightDp)
            // Ketuk bidang kosong = lepas pilihan, sehingga panel kanan kembali ke Pengaturan
            // Template. Tarikan tidak dihitung ketuk: pan di atas sudah mengonsumsi geraknya dan
            // detektor ketuk batal dengan sendirinya.
            //
            // Ketukan di mode Geser Kanvas sengaja **tidak** melepas pilihan: alat itu dipakai untuk
            // menggeser pandangan, bukan untuk mengubah apa yang sedang disunting. Pilihan yang hilang
            // karena menyentuh kanvas akan terasa seperti panel properti yang mengosongkan diri.
            .pointerInput(isSelectTool) {
                detectTapGestures {
                    runCatching { focusRequester.requestFocus() }
                    if (isSelectTool) {
                        onEvent(TemplateDesignerUiEvent.EndTextEdit)
                        onEvent(TemplateDesignerUiEvent.SelectElement(null))
                    }
                }
            }
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event -> handleCanvasKeyEvent(event, state, onEvent) }
    ) {
        if (state.showGrid) {
            GridOverlay(mmToDp = mmToDp)
        }

        laidOutElements.forEach { laid ->
            key(laid.element.elementId) {
                CanvasElementNode(
                    laid = laid,
                    isSelected = laid.element.elementId == state.selectedElementId,
                    isEditing = laid.element.elementId == state.editingTextElementId,
                    // Mode Geser mematikan handler elemen supaya tarikan di titik mana pun menjadi
                    // pan, bukan pemindahan elemen.
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

        // Editor langsung digambar paling akhir supaya tidak tertutup elemen lain — termasuk oleh
        // elemen yang posisinya bertumpuk di atas teks yang sedang diedit.
        state.editingElement?.let { editing ->
            val laid = laidOutElements.find { it.element.elementId == editing.elementId }
            if (laid != null) {
                InlineTextEditor(
                    elementId = editing.elementId,
                    rect = laid.rect,
                    mmToDp = mmToDp,
                    zoomFactor = zoomFactor,
                    initialText = editing.text,
                    style = editing.style,
                    onTextChange = { text ->
                        onEvent(TemplateDesignerUiEvent.UpdateElementText(editing.elementId, text))
                    },
                    onFinish = { onEvent(TemplateDesignerUiEvent.EndTextEdit) }
                )
            }
        }
    }
}

/** Mesh 10 mm di atas kertas. Bawaannya mati: mesh 21 × 30 kotak terbaca lebih ramai dari isinya. */
@Composable
private fun GridOverlay(mmToDp: Float) {
    val density = LocalDensity.current.density
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

/**
 * Pemetaan tombol panah & tombol hapus pada kanvas.
 *
 * Nudge lewat tombol panah sengaja tidak ditumpangkan pada `detectDragGestures`: setelah satu klik
 * pada elemen, presisi 1 mm tidak lagi bergantung pada kestabilan tangan saat menyeret mouse.
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
            // Esc menutup editor langsung lebih dulu; menekannya saat tidak sedang mengedit melepas
            // pilihan, seperti sebelumnya.
            if (state.editingTextElementId != null) {
                onEvent(TemplateDesignerUiEvent.EndTextEdit)
            } else {
                onEvent(TemplateDesignerUiEvent.SelectElement(null))
            }
            return true
        }
        Key.Delete -> {
            if (selected == null || state.editingTextElementId != null) return false
            onEvent(TemplateDesignerUiEvent.DeleteElement(selected.elementId))
            return true
        }
        else -> Unit
    }

    // Selama editor teks terbuka, tombol panah milik kursor di dalam teks — bukan perintah geser.
    if (selected == null || state.editingTextElementId != null) return false

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
 * Menempatkan elemen pada koordinat milimeter absolut di atas kertas.
 *
 * Satu-satunya tempat konversi mm → piksel untuk penempatan elemen. Sebelumnya rumus ini ditulis
 * ulang di tiga tempat (elemen, editor langsung, penanda) dan setiap salinan adalah kesempatan
 * untuk berbeda beberapa piksel dari hasil cetak.
 */
private fun Modifier.absoluteMmRect(rect: TemplateRect, mmToDp: Float): Modifier = this.offset {
    IntOffset(
        x = (rect.x.value / 10f * mmToDp).dp.roundToPx(),
        y = (rect.y.value / 10f * mmToDp).dp.roundToPx()
    )
}

/**
 * Satu elemen di atas kertas: posisi, garis pilihan, gestur tarik, dan gagang ubah lebar.
 *
 * `dragBase` diambil dari posisi terbaru **saat tarikan dimulai** dan tidak pernah dibaca ulang dari
 * state selama tarikan berlangsung. Inilah kunci agar perpindahan tidak berbalik arah: menghitung dari
 * rect yang sudah basi membuat setiap langkah menimpa langkah sebelumnya, dan elemen hanya bergerak
 * beberapa milimeter lalu mental kembali.
 *
 * Snap grid **dimatikan selama tarikan** dan baru diterapkan saat jari/mouse dilepas. Hasilnya gerakan
 * mengikuti kursor 1:1 (mulus), lalu "menempel" ke grid dengan satu lompatan magnetik.
 */
@Composable
private fun CanvasElementNode(
    laid: LaidOutElement,
    isSelected: Boolean,
    isEditing: Boolean,
    isInteractive: Boolean,
    mmToDp: Float,
    zoomFactor: Float,
    invoice: Invoice,
    snapMm10: Int,
    paperSize: PaperSize,
    onEvent: (TemplateDesignerUiEvent) -> Unit
) {
    val element = laid.element
    val renderRect by rememberUpdatedState(laid.rect)

    val widthDp = (laid.rect.width.value / 10f * mmToDp).dp
    val heightDp = (laid.rect.height.value / 10f * mmToDp).dp

    Box(
        modifier = Modifier
            .absoluteMmRect(laid.rect, mmToDp)
            .size(width = widthDp, height = heightDp)
            .then(
                // Ketebalan outline dijaga tetap per peran (Kontrak 8 design system); yang
                // membedakan state adalah warnanya. Luapan diberi warna peringatan karena ia satu-
                // satunya keadaan di mana yang terlihat di kanvas tidak akan sama dengan hasil
                // cetak — PDF menjepit offset perataannya ke nol dan teksnya menjulur keluar kotak.
                when {
                    isSelected -> Modifier.border(ClayBorder.Thick, WeMadeColors.Primary, ClayShapes.Element)
                    laid.hasOverflow -> Modifier.border(ClayBorder.Thick, WeMadeColors.Warning, ClayShapes.Element)
                    else -> Modifier.border(
                        ClayBorder.Hairline,
                        WeMadeColors.OnSurfaceDisabled.copy(alpha = 0.35f),
                        ClayShapes.Element
                    )
                }
            )
            .then(
                if (isInteractive && !isEditing) {
                    Modifier.elementDragModifier(element.elementId, mmToDp, snapMm10, paperSize, onEvent) { renderRect }
                } else {
                    Modifier
                }
            )
            .then(
                if (isInteractive && !isEditing) {
                    Modifier.tapSelectModifier(
                        isTextElement = element is TemplateElement.StaticText,
                        onSelect = { onEvent(TemplateDesignerUiEvent.SelectElement(element.elementId)) },
                        onEditText = { onEvent(TemplateDesignerUiEvent.BeginTextEdit(element.elementId)) }
                    )
                } else {
                    Modifier
                }
            )
            .padding(2.dp)
    ) {
        // Teks yang sedang diedit tidak digambar: editor langsung menggantikannya, dan menggambar
        // keduanya membuat huruf terlihat dobel di belakang kursor.
        if (!isEditing) {
            RenderElementContent(
                laid = laid,
                invoice = invoice,
                zoomFactor = zoomFactor
            )
        }

        if (isSelected && !isEditing) {
            SelectionFrame(
                element = element,
                mmToDp = mmToDp,
                snapMm10 = snapMm10,
                paperSize = paperSize,
                onEvent = onEvent
            )
        }
    }
}

/**
 * Bingkai pilihan: label ukuran dan gagang ubah lebar.
 *
 * Ubah lebar sengaja hanya tersedia untuk elemen yang lebarnya memang bermakna (teks, tabel, kotak).
 * Gagang pada elemen yang tingginya mengikuti isi akan langsung "dilawan" perhitungan ulang tinggi dan
 * terasa seperti kanvas yang rusak.
 */
@Composable
private fun BoxScope.SelectionFrame(
    element: TemplateElement,
    mmToDp: Float,
    snapMm10: Int,
    paperSize: PaperSize,
    onEvent: (TemplateDesignerUiEvent) -> Unit
) {
    val canResize = element !is TemplateElement.LineShape && element !is TemplateElement.ImageBox

    if (canResize) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(10.dp)
                .background(WeMadeColors.Primary, ClayShapes.Element)
                .widthResizeModifier(
                    baseWidthMm10 = element.rect.width.value,
                    mmToDp = mmToDp,
                    snapMm10 = snapMm10,
                    paperSize = paperSize,
                    onEvent = { newWidth ->
                        onEvent(
                            TemplateDesignerUiEvent.ResizeElementWidth(element.elementId, newWidth)
                        )
                    }
                )
        )
    }

    Row(
        modifier = Modifier
            .align(Alignment.TopStart)
            .offset(y = (-16).dp)
            .clayFlat(
                shape = ClayShapes.Element,
                background = WeMadeColors.Primary,
                outline = WeMadeColors.PrimaryDark,
                borderWidth = ClayBorder.Hairline
            )
            .padding(horizontal = 5.dp, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Lebar ${element.rect.width.value / 10} mm",
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = WeMadeColors.Surface,
            maxLines = 1
        )
    }
}

/**
 * Detektor ketuk elemen: satu ketuk memilih, dua ketuk membuka editor langsung untuk teks statis.
 *
 * `clickable` tidak dipakai karena ia tidak mengenal ketukan ganda — memakainya bersama ketukan ganda
 * membuat pemilihan elemen ikut terpicu dua kali pada setiap pengeditan, dan pilihan berpindah di
 * tengah pengeditan.
 */
private fun Modifier.tapSelectModifier(
    isTextElement: Boolean,
    onSelect: () -> Unit,
    onEditText: () -> Unit
): Modifier = this.pointerInput(isTextElement) {
    detectTapGestures(
        onTap = { onSelect() },
        onDoubleTap = {
            onSelect()
            if (isTextElement) onEditText()
        }
    )
}

/**
 * Modifier tarik untuk mengubah **lebar** elemen.
 *
 * Lebar dasar diambil saat tarikan dimulai, lalu setiap frame mengirim lebar absolut. Sama seperti
 * pemindahan: menghitung dari lebar terbaru yang sudah berubah membuat setiap langkah menumpuk
 * perubahan sebelumnya dan elemen membengkak jauh lebih cepat dari gerakan mouse.
 */
private fun Modifier.widthResizeModifier(
    baseWidthMm10: Int,
    mmToDp: Float,
    snapMm10: Int,
    paperSize: PaperSize,
    onEvent: (Int) -> Unit
): Modifier = this.pointerInput(baseWidthMm10, mmToDp, snapMm10, paperSize) {
    var dragBase = baseWidthMm10
    var dragTotalX = 0f

    detectDragGestures(
        onDragStart = {
            dragBase = baseWidthMm10
            dragTotalX = 0f
        },
        onDrag = { change, dragAmount ->
            change.consume()
            dragTotalX += dragAmount.x
            onEvent(dragBase + (dragTotalX / mmToDp * 10f).roundToInt())
        },
        onDragEnd = {
            val raw = dragBase + (dragTotalX / mmToDp * 10f).roundToInt()
            // Snap hanya di akhir tarikan supaya gerakan terasa mulus lalu menempel ke grid.
            onEvent(if (snapMm10 > 0) (raw / snapMm10) * snapMm10 else raw)
            dragTotalX = 0f
        },
        onDragCancel = { dragTotalX = 0f }
    )
}

/**
 * Editor teks langsung di atas kanvas.
 *
 * ## Kenapa tinggi editor dibiarkan tumbuh
 *
 * Kotak editor tidak dikunci pada tinggi elemen yang sedang diedit: begitu pengguna menambah baris,
 * tinggi turunan elemen ikut bertambah (lewat [InvoiceDocumentLayout]) dan editor harus terlihat
 * mengikuti. Kalau tinggi editor dipatok, baris keempat dan seterusnya mengetik "di luar kotak".
 *
 * ## Kenapa pemenggalan baris di editor tidak memakai [InvoiceTextLayout]
 *
 * Berbeda dari lapisan gambar, editor memang **harus** memakai mesin teks platform: kursor, seleksi,
 * dan IME tidak bisa bekerja di atas daftar baris hasil hitungan. Karena itu lebar baris di dalam
 * editor bisa berbeda beberapa persen dari hasil akhir — dan itu wajar, karena yang penting adalah
 * isi teksnya, bukan titik potongnya saat mengetik.
 */
@Composable
private fun InlineTextEditor(
    elementId: String,
    rect: TemplateRect,
    mmToDp: Float,
    zoomFactor: Float,
    initialText: String,
    style: TextStyleSpec,
    onTextChange: (String) -> Unit,
    onFinish: () -> Unit
) {
    // Teks ditahan sebagai state lokal agar kursor tidak meloncat saat ViewModel mengirim balik hasil
    // perubahan lewat state global. Kuncinya `elementId`, bukan isi teks: mengganti kunci dengan isi
    // membuat setiap ketikan membangun ulang kotak editor dan menghapus posisi kursor.
    var text by remember(elementId) { mutableStateOf(initialText) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(elementId) {
        runCatching { focusRequester.requestFocus() }
    }

    val fontSizeSp = (style.fontSizePt * zoomFactor).sp
    val lineHeightSp = (InvoiceTextLayout.lineHeightMm10(style) / 10f * mmToDp).sp
    // Editor memakai font yang sama dengan lapisan gambar. Kalau berbeda, huruf akan "meloncat"
    // bentuknya begitu editor ditutup, dan pengguna mengira perubahannya tidak tersimpan.
    val (family, weight) = fontFor(style)

    Box(
        modifier = Modifier
            .absoluteMmRect(rect, mmToDp)
            .widthIn(min = (rect.width.value / 10f * mmToDp).dp)
            .heightIn(min = (rect.height.value / 10f * mmToDp).dp)
            .background(WeMadeColors.Surface, ClayShapes.Element)
            .border(ClayBorder.Medium, WeMadeColors.Primary, ClayShapes.Element)
            .padding(2.dp)
    ) {
        BasicTextField(
            value = text,
            onValueChange = { updated ->
                text = updated
                onTextChange(updated)
            },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .onPreviewKeyEvent { keyEvent ->
                    if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.Escape) {
                        onFinish()
                        true
                    } else {
                        false
                    }
                },
            textStyle = TextStyle(
                color = Color(style.colorHex),
                fontSize = fontSizeSp,
                lineHeight = lineHeightSp,
                fontFamily = family,
                fontWeight = weight
            ),
            cursorBrush = SolidColor(WeMadeColors.Primary)
        )
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
 * Dipisah dari [CanvasElementNode] karena `pointerInput` menyimpan lambda beserta closure-nya selama
 * kuncinya tidak berubah. Posisi awal tarikan disimpan **di dalam** closure ini (`dragBase`) dan
 * aslinya dibaca lewat [latestRect] — pembaca yang selalu menunjuk state terbaru. Membaca rect dari
 * nilai yang di-capture saat komposisi pertama adalah akar bug "elemen tidak bisa digeser".
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

/**
 * Menggambar isi satu elemen.
 *
 * Teks **tidak** diserahkan ke pembungkusan otomatis Compose: baris yang datang dari
 * [LaidOutElement.textLines] digambar apa adanya (`softWrap = false`), sehingga titik potong baris di
 * kanvas identik dengan titik potong di PDF. Pembungkusan otomatis Compose memakai metrik Skia yang
 * berbeda dari PDFBox, dan perbedaannya baru terlihat setelah faktur dicetak.
 */
@Composable
private fun BoxScope.RenderElementContent(
    laid: LaidOutElement,
    invoice: Invoice,
    zoomFactor: Float
) {
    val element = laid.element
    val totalPaid = Money.idr(0)

    when (element) {
        is TemplateElement.StaticText -> {
            val (family, weight) = fontFor(element.style)
            Text(
                text = laid.textLines.joinToString("\n"),
                fontSize = (element.style.fontSizePt * zoomFactor).sp,
                lineHeight = (InvoiceTextLayout.lineHeightMm10(element.style) / 10f * 3f * zoomFactor).sp,
                fontFamily = family,
                fontWeight = weight,
                color = Color(element.style.colorHex),
                textAlign = toComposeTextAlign(element.style.align),
                softWrap = false,
                modifier = Modifier.fillMaxWidth()
            )
        }

        is TemplateElement.BoundField -> {
            val resolved = InvoiceBindingResolver.resolve(element.binding, invoice, null, totalPaid)
            val isUnmapped = resolved is ResolvedBindingValue.Empty
            val (family, weight) = fontFor(element.style)
            Text(
                // Token yang belum bisa diresolusi tetap ditampilkan sebagai penanda agar pengguna
                // melihat "ada yang salah" di kanvas, bukan kotak kosong tanpa penjelasan.
                text = if (isUnmapped) "{{${element.binding.value}}}" else laid.textLines.joinToString("\n"),
                fontSize = (element.style.fontSizePt * zoomFactor).sp,
                lineHeight = (InvoiceTextLayout.lineHeightMm10(element.style) / 10f * 3f * zoomFactor).sp,
                fontFamily = family,
                fontWeight = weight,
                color = if (isUnmapped) WeMadeColors.Warning else Color(element.style.colorHex),
                textAlign = toComposeTextAlign(element.style.align),
                softWrap = false,
                modifier = Modifier.fillMaxWidth()
            )
        }

        is TemplateElement.RectShape -> {
            // Hex ditangkap ke variabel lokal lebih dulu: properti `Long?` dari modul lain tidak bisa
            // di-smart-cast di dalam lambda modifier.
            val strokeHex = element.strokeHex
            val fillHex = element.fillHex

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (strokeHex != null && element.strokeMm10 > 0) {
                            Modifier.border(1.dp, Color(strokeHex))
                        } else {
                            Modifier
                        }
                    )
                    .then(
                        if (fillHex != null) {
                            Modifier.background(Color(fillHex))
                        } else {
                            Modifier
                        }
                    )
            )
        }

        is TemplateElement.LineShape -> {
            // Digambar di tengah tinggi kotaknya, sama seperti renderer PDF yang menempatkan garis di
            // `topPt - height/2`. Menggambarnya di tepi atas membuat posisi garis meleset 1 mm dari
            // hasil cetak hanya karena perbedaan titik jangkar.
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxWidth()
                    .height((element.strokeMm10 / 10f * 3f * zoomFactor).dp.coerceAtLeast(1.dp))
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

        is TemplateElement.ItemTable -> ItemTablePreview(
            table = element,
            invoice = invoice,
            zoomFactor = zoomFactor
        )
    }
}

/**
 * Pratinjau tabel item.
 *
 * Kolom memakai `weight` dari rasio lebar domain — rasio yang sama yang dipakai renderer PDF menghitung
 * lebar kolom dalam poin, sehingga proporsi kolom di kanvas dan di kertas berasal dari satu angka.
 */
@Composable
private fun BoxScope.ItemTablePreview(
    table: TemplateElement.ItemTable,
    invoice: Invoice,
    zoomFactor: Float,
    totalPaid: Money = Money.idr(0)
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .border(1.dp, WeMadeColors.Border)
    ) {
        if (table.showHeader) {
            val (headerFamily, headerWeight) = fontFor(table.headerStyle)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(WeMadeColors.PrimaryContainer)
                    .padding(vertical = 2.dp, horizontal = 4.dp)
            ) {
                table.columns.forEach { col ->
                    Text(
                        text = col.header,
                        fontSize = (table.headerStyle.fontSizePt * zoomFactor).sp,
                        fontFamily = headerFamily,
                        fontWeight = headerWeight,
                        color = Color(table.headerStyle.colorHex),
                        textAlign = toComposeTextAlign(col.align),
                        maxLines = 1,
                        modifier = Modifier.weight(col.columnWeight)
                    )
                }
            }
        }

        val (bodyFamily, bodyWeight) = fontFor(table.bodyStyle)
        invoice.lines.forEachIndexed { index, line ->
            val zebraFillHex = table.zebraFillHex
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (index % 2 == 1 && zebraFillHex != null) {
                            Modifier.background(Color(zebraFillHex))
                        } else {
                            Modifier
                        }
                    )
                    .padding(vertical = 2.dp, horizontal = 4.dp)
            ) {
                table.columns.forEach { col ->
                    val value = when (val res = InvoiceBindingResolver.resolve(col.binding, invoice, line, totalPaid)) {
                        is ResolvedBindingValue.Text -> res.value
                        is ResolvedBindingValue.Image -> res.assetUrl ?: ""
                        is ResolvedBindingValue.Empty -> "-"
                    }
                    Text(
                        text = value,
                        fontSize = (table.bodyStyle.fontSizePt * zoomFactor).sp,
                        fontFamily = bodyFamily,
                        fontWeight = bodyWeight,
                        color = Color(table.bodyStyle.colorHex),
                        textAlign = toComposeTextAlign(col.align),
                        maxLines = 1,
                        modifier = Modifier.weight(col.columnWeight)
                    )
                }
            }
        }
    }
}

private val TableColumn.columnWeight: Float
    get() = (widthRatio.numerator.toFloat() / widthRatio.denominator.toFloat()).coerceAtLeast(0.05f)

private fun toComposeTextAlign(align: TextAlign): ComposeTextAlign = when (align) {
    TextAlign.LEFT -> ComposeTextAlign.Left
    TextAlign.CENTER -> ComposeTextAlign.Center
    TextAlign.RIGHT -> ComposeTextAlign.Right
}

/**
 * Keluarga font faktur yang sudah dirakit, siap dipetakan dari [InvoiceFont].
 *
 * Dipegang sebagai satu objek dan dibagikan lewat [LocalInvoiceFontFaces] karena
 * [rememberNunitoFamily] dan [rememberFredokaFamily] **membangun** `FontFamily` baru setiap kali
 * dipanggil — memanggilnya di dalam perulangan elemen berarti merakit ulang dua keluarga font pada
 * setiap elemen di setiap recomposition.
 */
@Immutable
private class InvoiceFontFaces(val nunito: FontFamily, val fredoka: FontFamily) {

    fun familyFor(font: InvoiceFont): FontFamily = when (font) {
        InvoiceFont.NUNITO_REGULAR, InvoiceFont.NUNITO_BOLD -> nunito
        InvoiceFont.FREDOKA_MEDIUM, InvoiceFont.FREDOKA_BOLD -> fredoka
    }

    fun weightFor(font: InvoiceFont): FontWeight = when (font) {
        InvoiceFont.NUNITO_REGULAR -> FontWeight.Normal
        InvoiceFont.NUNITO_BOLD -> FontWeight.Bold
        // Fredoka hanya dibundel dari Medium ke atas; meminta Normal akan memaksa Compose
        // mensintesis bobot yang tidak ada dan lebarnya meleset dari PDF.
        InvoiceFont.FREDOKA_MEDIUM -> FontWeight.Medium
        InvoiceFont.FREDOKA_BOLD -> FontWeight.Bold
    }
}

/**
 * Wadah font kanvas.
 *
 * Nilai bawaannya sengaja melempar: setiap teks faktur **harus** digambar dengan font yang sama
 * dengan yang dipakai PDF. Kalau ada pemanggil yang lupa memasang penyedianya, jatuh diam-diam ke
 * font bawaan platform adalah persis bug yang sedang diperbaiki di sini, dan bug itu hanya terlihat
 * setelah faktur dicetak.
 */
private val LocalInvoiceFontFaces = staticCompositionLocalOf<InvoiceFontFaces> {
    error("LocalInvoiceFontFaces belum dipasang — bungkus kanvas dengan ProvideInvoiceFontFaces.")
}

@Composable
private fun ProvideInvoiceFontFaces(content: @Composable () -> Unit) {
    val nunito = rememberNunitoFamily()
    val fredoka = rememberFredokaFamily()
    val faces = remember(nunito, fredoka) { InvoiceFontFaces(nunito, fredoka) }
    CompositionLocalProvider(LocalInvoiceFontFaces provides faces, content = content)
}

/** Keluarga + bobot font untuk sebuah gaya teks, memakai aturan yang sama dengan renderer PDF. */
@Composable
private fun fontFor(style: TextStyleSpec): Pair<FontFamily, FontWeight> {
    val faces = LocalInvoiceFontFaces.current
    val font = InvoiceFontResolver.resolve(style)
    return faces.familyFor(font) to faces.weightFor(font)
}






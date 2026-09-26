package com.eventverse.app.presentation.invoicing.template

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.domain.invoicing.InvoiceId
import com.eventverse.app.domain.invoicing.template.PaperSize
import com.eventverse.app.presentation.designsystem.*
import com.eventverse.app.presentation.invoicing.InvoicePrefillData
import com.eventverse.app.presentation.invoicing.components.InvoicePdfPreviewModal
import com.eventverse.app.presentation.theme.WeMadeColors
import kotlin.math.min
import kotlin.math.roundToInt

/** Lebar perpustakaan elemen di kiri. Cukup untuk nama modul terpanjang tanpa elipsis. */
private val PALETTE_WIDTH = 248.dp

/** Lebar panel properti di kanan. Dipertahankan dari tata letak sebelumnya. */
private val INSPECTOR_WIDTH = 340.dp

/**
 * Layar desainer template faktur: palet elemen, kanvas A4, dan panel properti.
 *
 * Susunannya mengikuti alur kerja penyusun dokumen: **pilih** bahan di kiri, **atur** di kanan, dan
 * **lihat** hasilnya di tengah. Sebelumnya elemen ditambahkan dari toolbar di atas kanvas, sehingga
 * "menambah" dan "mengatur" berdesakan di dua tempat yang jauh dari objeknya.
 *
 * Catatan densitas: dua panel samping memakan ~600dp. Pada layar 1280dp, sisa untuk A4 tinggal ~640dp
 * — hanya sedikit lebih lebar dari kertas itu sendiri (630dp pada zoom 100%). Karena itu tombol
 * "Muat Layar" di toolbar penting: ia menghitung ulang zoom agar kertas utuh terlihat.
 */
@Composable
fun InvoiceTemplateDesignerScreen(
    tenantSlug: String,
    templateId: String?,
    initialPrefill: InvoicePrefillData? = null,
    onClose: () -> Unit,
    onInvoiceCreated: (InvoiceId) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val viewModel = remember(tenantSlug, templateId, initialPrefill) {
        TemplateDesignerViewModel(
            tenantSlug = tenantSlug,
            initialTemplateId = templateId?.takeIf { it.isNotBlank() },
            initialPrefill = initialPrefill
        )
    }
    val state by viewModel.uiState.collectAsState()

    // Zoom "Muat Layar" dihitung di dalam area kanvas (satu-satunya tempat yang tahu lebar sisanya),
    // lalu diteruskan ke toolbar yang berada di atasnya. Satu frame pertama tombolnya belum muncul;
    // itu disengaja, karena menebak lebar dari matematika layout luar akan salah begitu panel kiri
    // berubah lebar.
    var fitZoomPercent by remember { mutableStateOf<Int?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(ClaySpacing.Md),
        verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
    ) {
        DesignerToolbar(
            state = state,
            onEvent = { event ->
                if (event is TemplateDesignerUiEvent.SaveAndCreateInvoice) {
                    viewModel.onEvent(
                        TemplateDesignerUiEvent.SaveAndCreateInvoice { createdId ->
                            onInvoiceCreated(createdId)
                        }
                    )
                } else {
                    viewModel.onEvent(event)
                }
            },
            onClose = onClose,
            fitZoomPercent = fitZoomPercent
        )

        state.error?.let { err ->
            NotificationBanner(
                text = "Kesalahan: $err",
                tone = BannerTone.Error,
                onDismiss = { viewModel.onEvent(TemplateDesignerUiEvent.DismissMessage) }
            )
        }

        state.successMessage?.let { msg ->
            NotificationBanner(
                text = msg,
                tone = BannerTone.Success,
                onDismiss = { viewModel.onEvent(TemplateDesignerUiEvent.DismissMessage) }
            )
        }

        DataSourceStrip(state = state)

        Row(
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
        ) {
            ElementPalette(
                state = state,
                onEvent = viewModel::onEvent,
                modifier = Modifier.width(PALETTE_WIDTH)
            )

            BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxHeight()) {
                val viewport = DpSize(maxWidth, maxHeight)

                LaunchedEffect(viewport, state.template.paperSize) {
                    fitZoomPercent = fitZoomFor(
                        viewportWidth = viewport.width,
                        viewportHeight = viewport.height,
                        paperSize = state.template.paperSize
                    )
                }

                // State scroll diangkat ke sini karena kanvas memakainya juga untuk pan: tarikan di
                // area kosong memanggil `dispatchRawDelta` pada state yang sama dengan yang dipakai
                // roda mouse, sehingga kedua jalur tidak pernah bertengkar soal posisi.
                val horizontalScroll = rememberScrollState()
                val verticalScroll = rememberScrollState()

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .horizontalScroll(horizontalScroll)
                        .verticalScroll(verticalScroll)
                ) {
                    TemplateCanvas(
                        state = state,
                        onEvent = viewModel::onEvent,
                        horizontalScroll = horizontalScroll,
                        verticalScroll = verticalScroll,
                        viewportSize = viewport
                    )
                }
            }

            DesignerPropertyInspector(
                state = state,
                onEvent = viewModel::onEvent,
                modifier = Modifier.width(INSPECTOR_WIDTH)
            )
        }
    }

    if (state.isPdfPreviewOpen && state.createdInvoiceId != null) {
        InvoicePdfPreviewModal(
            invoice = state.previewInvoice,
            pdfUrl = viewModel.getPdfUrl(state.createdInvoiceId!!),
            onClose = { viewModel.onEvent(TemplateDesignerUiEvent.ClosePdfPreview) }
        )
    }
}

/** Nada pesan: menentukan warnanya, bukan ukuran atau ikonnya. */
private enum class BannerTone { Error, Success }

@Composable
private fun NotificationBanner(
    text: String,
    tone: BannerTone,
    onDismiss: () -> Unit
) {
    val background = if (tone == BannerTone.Error) WeMadeColors.ErrorBg else WeMadeColors.SuccessBg
    val outline = if (tone == BannerTone.Error) WeMadeColors.Error else WeMadeColors.Success
    val content = if (tone == BannerTone.Error) WeMadeColors.Error else WeMadeColors.Success

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clayFlat(
                shape = ClayShapes.Card,
                background = background,
                outline = outline,
                borderWidth = ClayBorder.Medium
            )
            .padding(ClaySpacing.Md),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            modifier = Modifier.weight(1f, fill = false),
            color = content,
            fontSize = 12.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.width(ClaySpacing.Sm))
        ClayButton(
            text = "Tutup",
            onClick = onDismiss,
            style = ClayButtonStyle.Ghost,
            fontSize = 11.sp
        )
    }
}

/**
 * Strip asal data.
 *
 * Ditambahkan sebagai pengganti panel "Isi Data Faktur Live": sejak nilai di kanvas selalu datang dari
 * luar desainer, pengguna berhak tahu **dari mana** dan **atas dasar apa** angka itu muncul — tanpa
 * diberi kesempatan mengubahnya di sini.
 */
@Composable
private fun DataSourceStrip(state: TemplateDesignerUiState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clayFlat(
                shape = ClayShapes.Card,
                background = WeMadeColors.SurfaceMuted,
                outline = WeMadeColors.Border,
                borderWidth = ClayBorder.Hairline
            )
            .padding(horizontal = ClaySpacing.Md, vertical = ClaySpacing.Sm),
        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconReceipt(Modifier.size(14.dp), color = WeMadeColors.Primary)

        Text(
            text = "Pratinjau Tata Letak & Mapping Dokumen:",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = WeMadeColors.OnSurface
        )

        ClayBadge(
            text = state.template.targetKind.displayName,
            tint = WeMadeColors.Primary,
            dot = true
        )

        Text(
            text = "•",
            fontSize = 11.sp,
            color = WeMadeColors.OnSurfaceMuted
        )

        Text(
            text = "Field transaksi (Client Name, Contact, No Invoice, Table Product, Total) otomatis terpetakan ke data transaksi Deal.",
            modifier = Modifier.weight(1f, fill = false),
            fontSize = 10.sp,
            color = WeMadeColors.OnSurfaceMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.weight(1f))

        ClayTag(
            text = "MAPPING PDF AKTIF",
            tint = WeMadeColors.Success
        )
    }
}

/**
 * Zoom yang membuat seluruh kertas muat di area kanvas.
 *
 * Skala acuan kanvas adalah 3 dp per milimeter pada zoom 100% — angka yang sama dengan yang dipakai
 * [TemplateCanvas] untuk menggambar, sehingga nilai ini tidak bisa menyimpang dari kenyataan.
 */
private fun fitZoomFor(viewportWidth: Dp, viewportHeight: Dp, paperSize: PaperSize): Int {
    val paperWidthAt100 = (paperSize.width.value / 10f) * MM_TO_DP_AT_100_PERCENT
    val paperHeightAt100 = (paperSize.height.value / 10f) * MM_TO_DP_AT_100_PERCENT
    val availableWidth = (viewportWidth.value - SHADOW_RESERVE_DP).coerceAtLeast(1f)
    val availableHeight = (viewportHeight.value - SHADOW_RESERVE_DP).coerceAtLeast(1f)

    val ratio = min(availableWidth / paperWidthAt100, availableHeight / paperHeightAt100)
    return (ratio * 100f).roundToInt().coerceIn(MIN_CANVAS_ZOOM, MAX_CANVAS_ZOOM)
}

/** 1 mm = 3 dp pada zoom 100%. */
private const val MM_TO_DP_AT_100_PERCENT = 3f

/** Ruang untuk hard shadow kertas (6dp) plus sedikit udara agar tepi kertas tidak menempel. */
private const val SHADOW_RESERVE_DP = 8f


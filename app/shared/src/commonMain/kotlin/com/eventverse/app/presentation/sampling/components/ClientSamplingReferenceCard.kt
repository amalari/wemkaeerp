package com.eventverse.app.presentation.sampling.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.eventverse.app.domain.sampling.FinishingPath
import com.eventverse.app.domain.sampling.SamplingOrder
import com.eventverse.app.domain.sampling.SizeChartRow
import com.eventverse.app.domain.sampling.STANDARD_SAMPLING_SIZE_COLUMNS
import com.eventverse.app.domain.sampling.calculateTotalSampleQuantity
import com.eventverse.app.domain.sampling.isQtyRow
import com.eventverse.app.presentation.designsystem.ClayBadge
import com.eventverse.app.presentation.designsystem.ClayBorder
import com.eventverse.app.presentation.designsystem.ClayCard
import com.eventverse.app.presentation.designsystem.ClayShapes
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.ClayTag
import com.eventverse.app.presentation.designsystem.IconCalendarGrid
import com.eventverse.app.presentation.designsystem.IconClose
import com.eventverse.app.presentation.designsystem.IconImage
import com.eventverse.app.presentation.designsystem.IconPackage
import com.eventverse.app.presentation.designsystem.IconRuler
import com.eventverse.app.presentation.designsystem.clayFlat
import com.eventverse.app.presentation.deal.components.rememberMockupBitmap
import com.eventverse.app.presentation.theme.WeMadeColors

/** Lebar & tinggi satu slot mockup — slot depan & belakang ditumpuk vertikal di kiri tabel. */
private val MOCKUP_SLOT_WIDTH = 190.dp
private val MOCKUP_SLOT_HEIGHT = 185.dp

/** Referensi mockup hanya layak dimuat bila berupa data URI atau presigned URL dari server. */
private fun loadableMockupRef(key: String?): String? =
    key?.takeIf { it.startsWith("http") || it.startsWith("data:") }

/**
 * Kartu "Referensi dari Klien (Deal)" pada dialog Detail SPK — tahap SPK Baru / Penentuan Alur.
 *
 * Menyatukan seluruh data yang disepakati saat Deal (mockup depan/belakang, matriks ukuran POM,
 * alokasi qty per ukuran, jalur finishing, deadline, catatan khusus) agar operator sampling
 * tidak perlu membuka dialog Deal hanya untuk melihat referensi desain.
 */
@Composable
fun ClientSamplingReferenceCard(order: SamplingOrder) {
    val frontRef = loadableMockupRef(order.mockupFrontKey)
    val backRef = loadableMockupRef(order.mockupBackKey)
    val totalQty = calculateTotalSampleQuantity(order.sizeMatrix, order.sampleQuantity)
    val deadline = order.deadlineDelivery ?: order.deadlineProgram

    // Slot yang sedang di-zoom: pasangan (label, referensi gambar).
    var zoomTarget by remember { mutableStateOf<Pair<String, String>?>(null) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clayFlat(
                shape = ClayShapes.Card,
                background = WeMadeColors.Surface,
                outline = WeMadeColors.Outline,
                borderWidth = ClayBorder.Medium
            )
            .padding(ClaySpacing.Md),
        verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
    ) {
        // Header kartu
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconPackage(modifier = Modifier.size(16.dp), color = WeMadeColors.Primary)
            Text(
                text = "REFERENSI DARI KLIEN (DEAL)",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = WeMadeColors.OnSurface,
                modifier = Modifier.weight(1f, fill = false),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (order.isCustomFlow) {
                ClayBadge(text = "Alur Kustom", tint = WeMadeColors.Accent)
            }
        }

        // Tag ringkasan kesepakatan deal
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f, fill = false),
                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)
            ) {
                ClayTag(
                    text = order.sizeMode.displayName,
                    tint = WeMadeColors.Primary,
                    leading = { IconRuler(modifier = Modifier.size(11.dp), color = WeMadeColors.Primary) }
                )
                ClayBadge(text = "$totalQty Pcs", tint = WeMadeColors.Info)
                ClayTag(
                    text = if (order.finishingPath == FinishingPath.MAKLOON_VENDOR) "Makloon Finishing" else "Internal Finishing",
                    tint = WeMadeColors.Success
                )
            }
            deadline?.let {
                Spacer(Modifier.width(ClaySpacing.Xs))
                ClayTag(
                    text = "Deadline $it",
                    tint = WeMadeColors.Accent,
                    leading = { IconCalendarGrid(modifier = Modifier.size(11.dp), color = WeMadeColors.Accent) }
                )
            }
        }

        // Slot mockup depan & belakang ditumpuk vertikal (atas-bawah), tabel ukuran di kanan
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
                MockupSlot(
                    label = "Tampak Depan",
                    reference = frontRef,
                    modifier = Modifier.width(MOCKUP_SLOT_WIDTH),
                    onClick = frontRef?.let { ref -> { zoomTarget = "Tampak Depan" to ref } }
                )
                MockupSlot(
                    label = "Tampak Belakang",
                    reference = backRef,
                    modifier = Modifier.width(MOCKUP_SLOT_WIDTH),
                    onClick = backRef?.let { ref -> { zoomTarget = "Tampak Belakang" to ref } }
                )
            }
            SizeChartTable(
                matrix = order.sizeMatrix,
                modifier = Modifier.weight(1f)
            )
        }

        // Catatan khusus dari client
        if (order.notes.isNotBlank()) {
            Text(
                text = "Catatan Client: ${order.notes}",
                fontSize = 10.sp,
                color = WeMadeColors.OnSurfaceMuted,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }

    zoomTarget?.let { (label, ref) ->
        MockupZoomPreviewDialog(label = label, reference = ref, onDismiss = { zoomTarget = null })
    }
}

/**
 * Satu slot mockup clay. Klik membuka preview zoom; slot kosong menampilkan placeholder
 * netral supaya operator tahu foto belum terlampir tanpa merasa UI-nya rusak.
 */
@Composable
private fun MockupSlot(
    label: String,
    reference: String?,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)?
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = WeMadeColors.OnSurfaceMuted
        )
        val bitmap = rememberMockupBitmap(reference)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(MOCKUP_SLOT_HEIGHT)
                .clayFlat(
                    shape = ClayShapes.Tile,
                    background = WeMadeColors.SurfaceMuted,
                    outline = WeMadeColors.Outline,
                    borderWidth = ClayBorder.Medium
                )
                .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
            contentAlignment = Alignment.Center
        ) {
            when {
                bitmap != null -> Image(
                    bitmap = bitmap,
                    contentDescription = label,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(MOCKUP_SLOT_HEIGHT)
                        .clip(ClayShapes.Tile),
                    contentScale = ContentScale.Crop
                )
                reference != null -> Text(
                    text = "Memuat…",
                    fontSize = 10.sp,
                    color = WeMadeColors.OnSurfaceMuted
                )
                else -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)
                ) {
                    IconImage(modifier = Modifier.size(22.dp), color = WeMadeColors.OnSurfaceMuted)
                    Text(
                        text = "Belum ada foto",
                        fontSize = 9.sp,
                        color = WeMadeColors.OnSurfaceMuted
                    )
                }
            }
        }
    }
}

/** Kolom ukuran yang benar-benar dipakai deal; fallback ke daftar standar bila tabel kosong. */
private fun usedSizeColumns(matrix: List<SizeChartRow>): List<String> =
    STANDARD_SAMPLING_SIZE_COLUMNS
        .filter { col -> matrix.any { it.values[col]?.isNotBlank() == true } }
        .ifEmpty { STANDARD_SAMPLING_SIZE_COLUMNS }

/**
 * Tabel matriks ukuran (POM) read-only. Baris "Jumlah Sampel (pcs)" disorot karena merupakan
 * baris alokasi qty — pembeda state lewat warna, bukan ketebalan (design-system Kontrak 8).
 */
@Composable
private fun SizeChartTable(
    matrix: List<SizeChartRow>,
    modifier: Modifier = Modifier
) {
    val columns = usedSizeColumns(matrix)
    Column(
        modifier = modifier.clayFlat(
            shape = ClayShapes.Tile,
            background = WeMadeColors.SurfaceMuted,
            outline = WeMadeColors.Outline,
            borderWidth = ClayBorder.Hairline
        )
    ) {
        // Header kolom ukuran
        SizeChartLine(
            label = "POM",
            values = columns.associateWith { it },
            columns = columns,
            isQty = false,
            isHeader = true
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(WeMadeColors.Outline)
        )
        matrix.forEach { row ->
            SizeChartLine(
                label = row.pomName,
                values = row.values,
                columns = columns,
                isQty = row.isQtyRow,
                isHeader = false
            )
        }
    }
}

@Composable
private fun SizeChartLine(
    label: String,
    values: Map<String, String>,
    columns: List<String>,
    isQty: Boolean,
    isHeader: Boolean
) {
    val emphasized = isHeader || isQty
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (isQty) Modifier.background(WeMadeColors.Primary.copy(alpha = 0.08f)) else Modifier)
            .padding(horizontal = ClaySpacing.Xs, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            modifier = Modifier.width(104.dp),
            fontSize = 10.sp,
            fontWeight = if (emphasized) FontWeight.Bold else FontWeight.Medium,
            color = if (emphasized) WeMadeColors.Primary else WeMadeColors.OnSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        columns.forEach { col ->
            Text(
                text = values[col]?.takeIf { it.isNotBlank() } ?: "-",
                modifier = Modifier.weight(1f),
                fontSize = 10.sp,
                fontWeight = if (emphasized) FontWeight.Bold else FontWeight.Normal,
                color = if (emphasized) WeMadeColors.Primary else WeMadeColors.OnSurfaceMuted,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}

/** Preview mockup ukuran penuh — dibuka saat slot mockup diklik. */
@Composable
private fun MockupZoomPreviewDialog(
    label: String,
    reference: String,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        ClayCard(
            modifier = Modifier.fillMaxWidth(0.6f),
            contentPadding = PaddingValues(ClaySpacing.Lg)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = label,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = WeMadeColors.OnSurface
                    )
                    androidx.compose.material3.IconButton(onClick = onDismiss) {
                        IconClose(modifier = Modifier.size(18.dp))
                    }
                }
                val bitmap = rememberMockupBitmap(reference)
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = label,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(420.dp)
                            .clip(ClayShapes.Tile),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(320.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Memuat gambar…",
                            fontSize = 11.sp,
                            color = WeMadeColors.OnSurfaceMuted
                        )
                    }
                }
            }
        }
    }
}

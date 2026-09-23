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
import com.eventverse.app.domain.sampling.SizeMode
import com.eventverse.app.domain.sampling.STANDARD_SAMPLING_SIZE_COLUMNS
import com.eventverse.app.domain.sampling.calculateTotalSampleQuantity
import com.eventverse.app.domain.sampling.isQtyRow
import com.eventverse.app.presentation.deal.components.rememberMockupBitmap
import com.eventverse.app.presentation.designsystem.ClayBadge
import com.eventverse.app.presentation.designsystem.ClayBorder
import com.eventverse.app.presentation.designsystem.ClayCard
import com.eventverse.app.presentation.designsystem.ClayShapes
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.ClayTag
import com.eventverse.app.presentation.designsystem.IconCalendarGrid
import com.eventverse.app.presentation.designsystem.IconClose
import com.eventverse.app.presentation.designsystem.IconImage
import com.eventverse.app.presentation.designsystem.IconNote
import com.eventverse.app.presentation.designsystem.IconPackage
import com.eventverse.app.presentation.designsystem.IconRuler
import com.eventverse.app.presentation.designsystem.IconSearch
import com.eventverse.app.presentation.designsystem.clayFlat
import com.eventverse.app.presentation.theme.WeMadeColors

/** Referensi mockup hanya layak dimuat bila berupa data URI atau presigned URL dari server. */
private fun loadableMockupRef(key: String?): String? =
    key?.takeIf { it.startsWith("http") || it.startsWith("data:") }

/**
 * Kartu "Referensi dari Klien (Deal)" pada dialog Detail SPK — tahap SPK Baru / Penentuan Alur.
 *
 * Menyatukan seluruh data yang disepakati saat Deal (mockup depan/belakang, matriks ukuran POM,
 * alokasi qty per ukuran, jalur finishing, deadline, catatan khusus) dalam tata letak Bento Grid
 * berestetika Claymorphism + Neo-Brutalism.
 */
@Composable
fun ClientSamplingReferenceCard(order: SamplingOrder) {
    val frontRef = loadableMockupRef(order.mockupFrontKey)
    val backRef = loadableMockupRef(order.mockupBackKey)
    val totalQty = calculateTotalSampleQuantity(order.sizeMatrix, order.sampleQuantity)
    val deadline = order.deadlineDelivery ?: order.deadlineProgram

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
        verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
    ) {
        // Tag ringkasan kesepakatan deal
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f, fill = false),
                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ClayTag(
                    text = order.sizeMode.displayName,
                    tint = WeMadeColors.Primary,
                    leading = { IconRuler(modifier = Modifier.size(11.dp), color = WeMadeColors.Primary) }
                )
                ClayBadge(text = "$totalQty Pcs Sample", tint = WeMadeColors.Info)
                ClayTag(
                    text = if (order.finishingPath == FinishingPath.MAKLOON_VENDOR) "Makloon Finishing" else "Internal Finishing",
                    tint = WeMadeColors.Success
                )
                if (order.isCustomFlow) {
                    ClayBadge(text = "Alur Kustom", tint = WeMadeColors.Accent)
                }
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

        // Layout 2 Kolom Seimbang: Visual Desain (Kiri) dan Spesifikasi POM + Memo (Kanan)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
        ) {
            // Kolom Kiri: Visual Desain (Mockup Polaroid Side-by-Side)
            Column(
                modifier = Modifier.weight(1.15f),
                verticalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconPackage(modifier = Modifier.size(13.dp), color = WeMadeColors.Primary)
                    Text(
                        text = "Visual Desain (Mockup)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = WeMadeColors.OnSurface
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
                ) {
                    MockupPolaroidCard(
                        label = "Tampak Depan",
                        reference = frontRef,
                        modifier = Modifier.weight(1f),
                        onClick = frontRef?.let { ref -> { zoomTarget = "Tampak Depan" to ref } }
                    )
                    MockupPolaroidCard(
                        label = "Tampak Belakang",
                        reference = backRef,
                        modifier = Modifier.weight(1f),
                        onClick = backRef?.let { ref -> { zoomTarget = "Tampak Belakang" to ref } }
                    )
                }
            }

            // Kolom Kanan: Spesifikasi Ukuran (POM Matrix) + Catatan Klien
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconRuler(modifier = Modifier.size(13.dp), color = WeMadeColors.Primary)
                    Text(
                        text = "Spesifikasi Ukuran (POM Matrix)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = WeMadeColors.OnSurface
                    )
                }
                SizeChartTable(
                    matrix = order.sizeMatrix,
                    sizeMode = order.sizeMode,
                    totalQty = totalQty,
                    modifier = Modifier.fillMaxWidth()
                )
                if (order.notes.isNotBlank()) {
                    ClientNotesMemo(notes = order.notes)
                }
            }
        }
    }

    zoomTarget?.let { (label, ref) ->
        MockupZoomPreviewDialog(label = label, reference = ref, onDismiss = { zoomTarget = null })
    }
}

/**
 * Satu slot mockup bergaya Polaroid Clay.
 * Menggunakan ContentScale.Fit agar proporsi pakaian utuh dan tombol zoom di pojok atas.
 */
@Composable
private fun MockupPolaroidCard(
    label: String,
    reference: String?,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)?
) {
    val bitmap = rememberMockupBitmap(reference)
    Column(
        modifier = modifier
            .clayFlat(
                shape = ClayShapes.Card,
                background = WeMadeColors.Surface,
                outline = WeMadeColors.Outline,
                borderWidth = ClayBorder.Medium
            )
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(ClaySpacing.Xs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(155.dp)
                .clip(ClayShapes.Tile)
                .background(WeMadeColors.SurfaceMuted),
            contentAlignment = Alignment.Center
        ) {
            when {
                bitmap != null -> {
                    Image(
                        bitmap = bitmap,
                        contentDescription = label,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(155.dp),
                        contentScale = ContentScale.Fit
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .size(24.dp)
                            .clayFlat(
                                shape = ClayShapes.Tile,
                                background = WeMadeColors.Surface.copy(alpha = 0.92f),
                                outline = WeMadeColors.Outline,
                                borderWidth = ClayBorder.Hairline
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        IconSearch(modifier = Modifier.size(12.dp), color = WeMadeColors.OnSurface)
                    }
                }
                reference != null -> {
                    Text(
                        text = "Memuat…",
                        fontSize = 10.sp,
                        color = WeMadeColors.OnSurfaceMuted
                    )
                }
                else -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)
                    ) {
                        IconImage(modifier = Modifier.size(24.dp), color = WeMadeColors.OnSurfaceMuted)
                        Text(
                            text = "Belum ada foto",
                            fontSize = 9.sp,
                            color = WeMadeColors.OnSurfaceMuted
                        )
                    }
                }
            }
        }
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = WeMadeColors.OnSurface,
            modifier = Modifier.padding(bottom = 2.dp)
        )
    }
}

/** Kolom ukuran yang relevan. Jika ALL_SIZE, hanya kolom ALL SIZE yang ditampilkan. */
private fun usedSizeColumns(matrix: List<SizeChartRow>, sizeMode: SizeMode): List<String> {
    if (sizeMode == SizeMode.ALL_SIZE) {
        return listOf("ALL SIZE")
    }
    val used = STANDARD_SAMPLING_SIZE_COLUMNS.filter { col ->
        matrix.any { it.values[col]?.isNotBlank() == true }
    }
    return used.ifEmpty { listOf("ALL SIZE") }
}

/**
 * Tabel matriks ukuran (POM) kompak bergaya Claymorphism.
 */
@Composable
private fun SizeChartTable(
    matrix: List<SizeChartRow>,
    sizeMode: SizeMode,
    totalQty: Int,
    modifier: Modifier = Modifier
) {
    val columns = usedSizeColumns(matrix, sizeMode)
    Column(
        modifier = modifier.clayFlat(
            shape = ClayShapes.Card,
            background = WeMadeColors.Surface,
            outline = WeMadeColors.Outline,
            borderWidth = ClayBorder.Medium
        )
    ) {
        // Header Tabel
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(WeMadeColors.SurfaceMuted)
                .padding(horizontal = ClaySpacing.Sm, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Parameter POM",
                modifier = Modifier.weight(1.3f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = WeMadeColors.OnSurface
            )
            columns.forEach { col ->
                Text(
                    text = col,
                    modifier = Modifier.weight(1f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = WeMadeColors.Primary,
                    textAlign = TextAlign.Center
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(WeMadeColors.Outline)
        )
        // Baris Tabel
        matrix.forEachIndexed { index, row ->
            val isQty = row.isQtyRow
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (isQty) {
                            Modifier.background(WeMadeColors.Primary.copy(alpha = 0.08f))
                        } else if (index % 2 == 1) {
                            Modifier.background(WeMadeColors.SurfaceMuted.copy(alpha = 0.35f))
                        } else {
                            Modifier
                        }
                    )
                    .padding(horizontal = ClaySpacing.Sm, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isQty) "Jumlah Sampel" else row.pomName,
                    modifier = Modifier.weight(1.3f),
                    fontSize = 10.sp,
                    fontWeight = if (isQty) FontWeight.Bold else FontWeight.Medium,
                    color = if (isQty) WeMadeColors.Primary else WeMadeColors.OnSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                columns.forEach { col ->
                    val rawVal = row.values[col]?.trim().orEmpty()
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isQty) {
                            val qtyVal = rawVal.ifEmpty { if (columns.size == 1) "$totalQty" else "-" }
                            if (qtyVal != "-") {
                                ClayBadge(text = "$qtyVal Pcs", tint = WeMadeColors.Primary)
                            } else {
                                Text(
                                    text = "-",
                                    fontSize = 10.sp,
                                    color = WeMadeColors.OnSurfaceMuted,
                                    textAlign = TextAlign.Center
                                )
                            }
                        } else {
                            val displayVal = when {
                                rawVal.isEmpty() || rawVal == "-" -> "-"
                                rawVal.all { it.isDigit() || it == '.' || it == ',' } -> "$rawVal cm"
                                else -> rawVal
                            }
                            Text(
                                text = displayVal,
                                fontSize = 10.sp,
                                fontWeight = if (displayVal != "-") FontWeight.SemiBold else FontWeight.Normal,
                                color = if (displayVal != "-") WeMadeColors.OnSurface else WeMadeColors.OnSurfaceMuted,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
            if (index < matrix.lastIndex) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(0.5.dp)
                        .background(WeMadeColors.Outline.copy(alpha = 0.25f))
                )
            }
        }
    }
}

/** Sticky memo catatan klien dengan sentuhan neo-brutalisme. */
@Composable
private fun ClientNotesMemo(
    notes: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clayFlat(
                shape = ClayShapes.Tile,
                background = WeMadeColors.WarningBg,
                outline = WeMadeColors.Warning.copy(alpha = 0.5f),
                borderWidth = ClayBorder.Hairline
            )
            .padding(ClaySpacing.Sm),
        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xs),
        verticalAlignment = Alignment.Top
    ) {
        IconNote(modifier = Modifier.size(14.dp), color = WeMadeColors.Warning)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = "Catatan Klien",
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = WeMadeColors.Warning
            )
            Text(
                text = notes,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = WeMadeColors.OnSurface,
                lineHeight = 14.sp
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


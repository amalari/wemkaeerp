package com.eventverse.app.presentation.designsystem

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Status satu berkas field (TRD-FIELD-002 FR-6). Progres `null` = determinate belum tersedia dari
 * lapisan transport (v1) — komponen menampilkan bar aktifitas bergerak, bukan persentase palsu.
 */
sealed interface ClayFileFieldState {
    /** Belum ada aksi berjalan. */
    data object Idle : ClayFileFieldState

    /** Unggah berjalan; [progress] 0..1 bila diketahui, `null` bila tak terukur. */
    data class Uploading(val progress: Float?) : ClayFileFieldState

    /** Berkas tersimpan (ada referensinya). */
    data object Ready : ClayFileFieldState

    /** Unggah/unduh gagal — [message] sudah berbahasa manusia, bukan kode HTTP mentah. */
    data class Error(val message: String) : ClayFileFieldState
}

/**
 * Komponen dasar field berkas (TRD-FIELD-002 FR-6) — **buta domain**: menerima [String], state,
 * dan lambda saja; pemanggil (form prototype, tabel/kanban, CRM) yang menerjemahkan referensi
 * berkas dan memanggil API unggah/unduhnya.
 *
 * Bahasa visual clay: chip berkas `clayFlat`, tombol [ClayButton], bar progres bergaya clay
 * (tanpa `LinearProgressIndicator` Material yang membawa ungu default). Progres digerakkan
 * Compose state — tanpa library tambahan.
 *
 * @param fileName nama berkas yang tampil; string kosong berarti slot masih kosong.
 * @param sizeLabel keterangan batas/ukuran, mis. "Maks 10 MB" — ditampilkan di bawah chip.
 * @param state status unggah saat ini.
 * @param onPick memilih berkas (unggah pertama atau ganti); pemanggil yang membuka picker platform.
 * @param onDownload aksi unduh; null = tidak tersedia (mis. referensi belum ada atau tanpa izin).
 * @param onRemove aksi hapus referensi dari sel; null = tidak tersedia.
 * @param isError menandai galat validasi dari pemanggil (outline chip memakai warna galat).
 * @param label label field opsional — pemanggil yang sudah menggambar labelnya sendiri mengosongkannya.
 */
@Composable
fun ClayFileField(
    fileName: String,
    sizeLabel: String,
    state: ClayFileFieldState,
    onPick: () -> Unit,
    modifier: Modifier = Modifier,
    onDownload: (() -> Unit)? = null,
    onRemove: (() -> Unit)? = null,
    enabled: Boolean = true,
    isError: Boolean = false,
    label: String = "",
    pickLabel: String = "Pilih Berkas",
) {
    val isBusy = state is ClayFileFieldState.Uploading

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)) {
        if (label.isNotBlank()) {
            Text(
                text = label,
                style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                color = WeMadeColors.OnSurface
            )
        }

        if (fileName.isBlank()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
                ClayButton(
                    text = pickLabel,
                    style = ClayButtonStyle.Secondary,
                    onClick = onPick,
                    enabled = enabled && !isBusy,
                    leading = { IconFile(Modifier.size(14.dp), color = WeMadeColors.OnSurfaceMuted) }
                )
                if (sizeLabel.isNotBlank()) {
                    Text(
                        text = sizeLabel,
                        style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                        color = WeMadeColors.OnSurfaceMuted
                    )
                }
            }
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
            ) {
                // Chip berkas: elemen yang boleh mengalah diberi weight(1f, fill = false) + ellipsis (Kontrak 13).
                Row(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .clayFlat(
                            shape = ClayShapes.Chip,
                            background = if (isError) WeMadeColors.ErrorBg else WeMadeColors.SurfaceMuted,
                            outline = if (isError) WeMadeColors.Error else WeMadeColors.Outline,
                            borderWidth = ClayBorder.Medium
                        )
                        .padding(horizontal = ClaySpacing.Md, vertical = ClaySpacing.Sm),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
                ) {
                    IconFile(Modifier.size(14.dp), color = WeMadeColors.OnSurfaceMuted)
                    Text(
                        text = fileName,
                        style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = WeMadeColors.OnSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (onDownload != null) {
                    ClayIconButton(
                        onClick = onDownload,
                        enabled = enabled && !isBusy,
                        size = 30.dp,
                        shape = ClayShapes.Tile
                    ) {
                        IconDownload(Modifier.size(15.dp), color = WeMadeColors.Primary)
                    }
                }
                if (onRemove != null) {
                    ClayIconButton(
                        onClick = onRemove,
                        enabled = enabled && !isBusy,
                        size = 30.dp,
                        shape = ClayShapes.Tile
                    ) {
                        IconClose(Modifier.size(13.dp), color = WeMadeColors.Error)
                    }
                }
            }
        }

        when (state) {
            is ClayFileFieldState.Uploading -> ClayFileProgressBar(state.progress)
            is ClayFileFieldState.Error -> Text(
                text = state.message,
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                color = WeMadeColors.Defect
            )
            ClayFileFieldState.Idle, ClayFileFieldState.Ready -> if (sizeLabel.isNotBlank() && fileName.isNotBlank()) {
                Text(
                    text = sizeLabel,
                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                    color = WeMadeColors.OnSurfaceMuted
                )
            }
        }
    }
}

/**
 * Bar progres bergaya clay: rel `clayFlat` + isian [WeMadeColors.Primary]. [progress] null =
 * segmen bergerak bolak-balik (aktifitas), angka = terisi penuh sepanjang pecahannya.
 */
@Composable
private fun ClayFileProgressBar(progress: Float?) {
    var fillStart = 0f
    var fillEnd = 0f
    if (progress == null) {
        val transition = rememberInfiniteTransition(label = "clayFileUpload")
        val segmentStart by transition.animateFloat(
            initialValue = -0.35f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1100, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "clayFileUploadSegment"
        )
        fillStart = segmentStart
        fillEnd = segmentStart + 0.35f
    } else {
        fillEnd = progress.coerceIn(0f, 1f)
    }
    val fraction = (fillEnd - fillStart).coerceIn(0f, 1f)
    if (fraction <= 0f) return
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clayFlat(
                shape = ClayShapes.Chip,
                background = WeMadeColors.SurfaceMuted,
                outline = WeMadeColors.Outline,
                borderWidth = ClayBorder.Hairline
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction)
                .background(WeMadeColors.Primary)
        )
    }
}

/** Chip berkas mini satu baris (nama saja) untuk konteks padat — pembacaan, bukan aksi. */
@Composable
fun ClayFileChip(
    fileName: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    tint: Color = WeMadeColors.Primary
) {
    Row(
        modifier = modifier
            .clayFlat(
                shape = ClayShapes.Chip,
                background = tint.copy(alpha = 0.12f),
                outline = tint.copy(alpha = 0.45f),
                borderWidth = ClayBorder.Hairline
            )
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = ClaySpacing.Md, vertical = ClaySpacing.Xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)
    ) {
        IconDownload(Modifier.size(11.dp), color = tint)
        Text(
            text = fileName,
            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = tint,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}

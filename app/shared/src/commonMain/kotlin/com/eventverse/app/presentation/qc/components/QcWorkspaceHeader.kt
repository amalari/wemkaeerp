package com.eventverse.app.presentation.qc.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.eventverse.app.domain.sampling.QcInspectionKind
import com.eventverse.app.presentation.designsystem.ClayBadge
import com.eventverse.app.presentation.designsystem.ClayButton
import com.eventverse.app.presentation.designsystem.ClayButtonStyle
import com.eventverse.app.presentation.designsystem.ClayCard
import com.eventverse.app.presentation.designsystem.ClayFlowRow
import com.eventverse.app.presentation.designsystem.ClayShapes
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.qc.QcQueueBucket
import com.eventverse.app.presentation.qc.QcQueueItem
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Kepala stasiun QC: identitas meja, siapa petugasnya, dan berapa yang mengantre.
 *
 * Dipisah dari [com.eventverse.app.presentation.qc.QcInspectorWorkspaceScreen] karena ia punya
 * dua susunan yang berbeda — sebaris di layar lebar, bertumpuk di telepon — dan menaruh
 * percabangan itu di dalam berkas layar membuat berkas layar berhenti hanya merakit.
 */
@Composable
fun QcWorkspaceHeader(
    queue: List<QcQueueItem>,
    activeKind: QcInspectionKind,
    inspectorName: String,
    isCompact: Boolean,
    onKindChange: (QcInspectionKind) -> Unit,
    modifier: Modifier = Modifier
) {
    // Dihitung dari antrean yang sama dengan yang dirender — angka badge yang tidak cocok
    // dengan jumlah baris terbaca sebagai sistem rusak.
    val waiting = queue.count { it.bucket == QcQueueBucket.WAITING }
    val rework = queue.count { it.bucket == QcQueueBucket.REWORK }

    val subtitle = if (inspectorName.isBlank()) {
        "Sesi tidak mengenali petugas — lembar tidak bisa ditandatangani."
    } else if (isCompact) {
        "Petugas: $inspectorName"
    } else {
        "Petugas: $inspectorName  •  satu lembar = satu pcs"
    }

    val badges: @Composable () -> Unit = {
        ClayFlowRow {
            if (waiting > 0) {
                ClayBadge(text = "$waiting Menunggu", tint = WeMadeColors.Warning, dot = true)
            }
            if (rework > 0) {
                ClayBadge(text = "$rework Perlu Rework", tint = WeMadeColors.Accent)
            }
        }
    }

    ClayCard(
        modifier = modifier.fillMaxWidth(),
        shape = ClayShapes.Card,
        contentPadding = PaddingValues(ClaySpacing.Lg)
    ) {
        // Judul panjang "KONTROL KUALITAS (QUALITY CONTROL)" di telepon hanya muat sebagai
        // "KONTROL KUALITAS (QU…" — kurungnya terpotong dan tampak seperti teks rusak. Di
        // lebar sempit judulnya dipendekkan, bukan dielipsis.
        val title = if (isCompact) "KONTROL KUALITAS" else "KONTROL KUALITAS (QUALITY CONTROL)"

        if (isCompact) {
            Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)) {
                QcHeaderTitle(title = title, subtitle = subtitle, isError = inspectorName.isBlank())
                badges()
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f, fill = false)) {
                    QcHeaderTitle(title = title, subtitle = subtitle, isError = inspectorName.isBlank())
                }
                Spacer(modifier = Modifier.width(ClaySpacing.Md))
                badges()
            }
        }

        Spacer(modifier = Modifier.height(ClaySpacing.Md))

        // Dua meja adalah sakelar utama layar ini; di telepon tombolnya dibuat selebar
        // separuh layar masing-masing supaya bisa ditekan dengan ibu jari bersarung tangan.
        if (isCompact) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
            ) {
                QcInspectionKind.entries.forEach { kind ->
                    ClayButton(
                        text = kind.shortLabel,
                        style = if (kind == activeKind) ClayButtonStyle.Primary else ClayButtonStyle.Secondary,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = ClaySpacing.Md, vertical = ClaySpacing.Lg),
                        onClick = { onKindChange(kind) }
                    )
                }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
                QcInspectionKind.entries.forEach { kind ->
                    ClayButton(
                        text = kind.shortLabel,
                        style = if (kind == activeKind) ClayButtonStyle.Primary else ClayButtonStyle.Secondary,
                        onClick = { onKindChange(kind) }
                    )
                }
            }
        }
    }
}

@Composable
private fun QcHeaderTitle(title: String, subtitle: String, isError: Boolean) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = WeMadeColors.OnSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
    Text(
        text = subtitle,
        style = MaterialTheme.typography.bodySmall,
        color = if (isError) WeMadeColors.Error else WeMadeColors.OnSurfaceMuted,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
    )
}

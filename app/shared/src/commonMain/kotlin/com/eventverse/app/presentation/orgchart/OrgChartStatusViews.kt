package com.eventverse.app.presentation.orgchart

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.eventverse.app.presentation.designsystem.ClayButton
import com.eventverse.app.presentation.designsystem.ClayButtonStyle
import com.eventverse.app.presentation.designsystem.ClayCard
import com.eventverse.app.presentation.designsystem.ClayOffset
import com.eventverse.app.presentation.designsystem.ClayShapes
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.theme.WeMadeColors

/*
 * Tampilan keadaan pemuatan Org Chart (TRD-PLAT-010). Buta domain: hanya menerima String dan lambda.
 * Teks sengaja ASCII/Latin-1 (font Nunito tidak punya glyph di luar itu).
 */

/** Keadaan kosong: tenant belum punya data. Contoh hanya lewat tombol, dan dieksekusi server. */
@Composable
internal fun OrgChartEmptyState(
    message: String,
    createLabel: String,
    canCreate: Boolean,
    canLoadSample: Boolean,
    isLoadingSample: Boolean,
    onCreate: () -> Unit,
    onLoadSample: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize().padding(ClaySpacing.Xxl), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
        ) {
            Text(
                text = "Bagan Organisasi Masih Kosong",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = WeMadeColors.OnSurface
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = WeMadeColors.OnSurfaceMuted,
                textAlign = TextAlign.Center
            )
            if (canCreate || canLoadSample) {
                Spacer(modifier = Modifier.height(ClaySpacing.Xs))
                Row(horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Md)) {
                    if (canLoadSample) {
                        ClayButton(
                            text = if (isLoadingSample) "Memuat contoh..." else "Muat contoh",
                            onClick = onLoadSample,
                            enabled = !isLoadingSample,
                            style = ClayButtonStyle.Secondary,
                            offset = ClayOffset.Small
                        )
                    }
                    if (canCreate) {
                        ClayButton(
                            text = createLabel,
                            onClick = onCreate,
                            style = ClayButtonStyle.Primary,
                            offset = ClayOffset.Small
                        )
                    }
                }
            }
        }
    }
}

/** Menunggu respons pertama server. Sengaja tanpa kartu dan tanpa data. */
@Composable
internal fun OrgChartLoadingView(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = "Memuat struktur organisasi...",
            style = MaterialTheme.typography.bodyMedium,
            color = WeMadeColors.OnSurfaceMuted
        )
    }
}

/** Galat pemuatan: pesan + "Coba lagi". Tidak menampilkan data basi atau contoh. */
@Composable
internal fun OrgChartFailedView(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize().padding(ClaySpacing.Xxl), contentAlignment = Alignment.Center) {
        ClayCard(
            modifier = Modifier.fillMaxWidth(),
            shape = ClayShapes.Panel,
            containerColor = WeMadeColors.SurfaceMuted,
            contentPadding = PaddingValues(ClaySpacing.Xl)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
            ) {
                Text(
                    text = "Struktur organisasi gagal dimuat",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = WeMadeColors.OnSurface
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = WeMadeColors.Error,
                    textAlign = TextAlign.Center
                )
                ClayButton(text = "Coba lagi", onClick = onRetry, style = ClayButtonStyle.Primary, offset = ClayOffset.Small)
            }
        }
    }
}

package com.eventverse.app.presentation.rbac

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
 * Tampilan keadaan pemuatan layar Hak Akses (TRD-PLAT-010 T3). Buta domain: hanya String dan lambda.
 * Teks sengaja ASCII/Latin-1 (font Nunito tidak punya glyph di luar itu).
 */

/** Menunggu respons pertama server. Sengaja tanpa kartu dan tanpa data. */
@Composable
internal fun RbacLoadingView(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = "Memuat hak akses...",
            style = MaterialTheme.typography.bodyMedium,
            color = WeMadeColors.OnSurfaceMuted
        )
    }
}

/** Server menjawab sukses tetapi belum ada jabatan. Contoh tidak pernah disuntik dari klien. */
@Composable
internal fun RbacEmptyView(
    message: String,
    createLabel: String,
    canCreate: Boolean,
    onCreate: () -> Unit,
    modifier: Modifier = Modifier
) {
    ScrollCenter(modifier) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
        ) {
            Text(
                text = "Belum Ada Jabatan",
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
            if (canCreate) {
                ClayButton(text = createLabel, onClick = onCreate, style = ClayButtonStyle.Primary, offset = ClayOffset.Small)
            }
        }
    }
}

/** Galat pemuatan: pesan + "Coba lagi". Tidak menampilkan data basi atau contoh. */
@Composable
internal fun RbacFailedView(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    ScrollCenter(modifier) {
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
                    text = "Hak akses gagal dimuat",
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

/**
 * Pusat-vertikal yang bisa di-scroll: terpusat bila muat, tergulir bila isinya lebih tinggi dari ruang
 * (layar 360dp). Urutan modifier penting: fillMaxSize dulu, baru scroll (pola Org Chart, Putaran 2).
 */
@Composable
private fun ScrollCenter(modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(ClaySpacing.Xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        content = content
    )
}

/** Sesi belum membawa tenant (mis. superadmin platform): tidak ada tenant bawaan yang boleh ditebak. */
@Composable
internal fun RbacNoTenantView(modifier: Modifier = Modifier) {
    ScrollCenter(modifier) {
        Text(
            text = "Pilih tenant terlebih dahulu untuk mengatur hak akses.",
            style = MaterialTheme.typography.bodyMedium,
            color = WeMadeColors.OnSurfaceMuted,
            textAlign = TextAlign.Center
        )
    }
}

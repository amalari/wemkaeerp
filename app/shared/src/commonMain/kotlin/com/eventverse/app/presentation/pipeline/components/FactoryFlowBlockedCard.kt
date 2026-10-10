package com.eventverse.app.presentation.pipeline.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.presentation.designsystem.ClayBadge
import com.eventverse.app.presentation.designsystem.ClayButton
import com.eventverse.app.presentation.designsystem.ClayButtonStyle
import com.eventverse.app.presentation.designsystem.ClayCard
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.pipeline.FactoryFlowLoadState
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Pengganti kanvas Factory Flow saat alur tenant tidak dapat dimuat (TRD-PLAT-012 Q2): 403 tampil sebagai
 * penolakan akses (tanpa tombol coba lagi - mencoba ulang tak mengubah jawaban), kegagalan jaringan/server
 * sebagai galat yang bisa dicoba lagi. Tidak pernah menampilkan data preset.
 */
@Composable
fun FactoryFlowBlockedCard(
    state: FactoryFlowLoadState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val denied = state as? FactoryFlowLoadState.AccessDenied
    val failed = state as? FactoryFlowLoadState.Failed
    if (denied == null && failed == null) return

    ClayCard(
        modifier = modifier.fillMaxWidth().widthIn(max = 640.dp),
        containerColor = WeMadeColors.ErrorBg,
        outlineColor = WeMadeColors.Outline
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (denied != null) "Anda tidak punya akses ke Factory Flow" else "Alur pabrik gagal dimuat",
                modifier = Modifier.weight(1f, fill = false),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = WeMadeColors.Error
            )
            ClayBadge(text = if (denied != null) "403" else "GALAT", tint = WeMadeColors.Error)
        }
        Column(
            modifier = Modifier.padding(top = ClaySpacing.Lg),
            verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
        ) {
            Text(
                text = denied?.message ?: failed?.message.orEmpty(),
                fontSize = 13.sp,
                color = WeMadeColors.OnSurface
            )
            Text(
                text = if (denied != null) {
                    "Hubungi pemilik pabrik untuk membuka wewenang lewat jabatan di layar Hak Akses (RBAC)."
                } else {
                    "Data tenant tidak ditampilkan sampai server menjawab, agar template contoh tidak " +
                        "disangka konfigurasi pabrik Anda."
                },
                fontSize = 12.sp,
                color = WeMadeColors.OnSurfaceMuted
            )
            if (failed != null) {
                ClayButton(
                    text = "Coba Lagi",
                    onClick = onRetry,
                    style = ClayButtonStyle.Secondary,
                    fontSize = 12.sp
                )
            }
        }
    }
}

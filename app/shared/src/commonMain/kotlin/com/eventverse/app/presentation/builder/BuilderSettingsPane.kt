package com.eventverse.app.presentation.builder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.eventverse.app.presentation.designsystem.ClayCard
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.rememberClayTypography
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Pengaturan Builder (M0): identitas bisnis & kolaborator. M0 hanya **menampilkan** konfigurasi
 * yang sudah ada di tenant; penyuntingan menyusul begitu endpoint PATCH (prefiks /api/builder) tersedia.
 */
@Composable
fun BuilderSettingsPane(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ClaySpacing.Lg)) {
        Text(
            text = "Pengaturan",
            style = rememberClayTypography().titleLarge,
            fontWeight = FontWeight.Bold,
            color = WeMadeColors.OnSurface
        )
        ClayCard {
            Text("Identitas Bisnis", fontWeight = FontWeight.Bold, color = WeMadeColors.OnSurface)
            Text(
                "Logo, NPWP, dan prefiks nomor dokumen dikelola di Pengaturan tenant hari ini; " +
                        "pemindahannya ke Builder menyusul di fase berikutnya.",
                color = WeMadeColors.OnSurfaceMuted
            )
        }
        ClayCard {
            Text("Kolaborator", fontWeight = FontWeight.Bold, color = WeMadeColors.OnSurface)
            Text(
                "Undang anggota tim dengan izin Builder (MANAGE_BUILDER) setelah mesin keputusan " +
                        "non-modul tersedia. Saat ini pemilik project dan platform superadmin punya akses.",
                color = WeMadeColors.OnSurfaceMuted
            )
        }
    }
}

package com.eventverse.app.presentation.builder.setting

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import com.eventverse.app.presentation.designsystem.ClayCard
import com.eventverse.app.presentation.designsystem.rememberClayTypography
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Tab Identitas Bisnis (FR-6-1): isi Pengaturan Builder lama, di-host ulang tanpa menulis ulang
 * janjinya — logo, NPWP, dan prefiks nomor dokumen masih dikelola di Pengaturan tenant; panel
 * penyuntingan menyusul begitu endpoint PATCH (prefiks /api/builder) tersedia.
 */
@Composable
internal fun BuilderIdentityTab() {
    val typography = rememberClayTypography()
    ClayCard {
        Text("Identitas Bisnis", fontWeight = FontWeight.Bold, color = WeMadeColors.OnSurface)
        Text(
            "Logo, NPWP, dan prefiks nomor dokumen dikelola di Pengaturan tenant hari ini; " +
                    "pemindahannya ke Builder menyusul di fase berikutnya.",
            style = typography.bodySmall,
            color = WeMadeColors.OnSurfaceMuted
        )
    }
}

/**
 * Tab Kolaborator (FR-6-4): undangan/daftar user dengan izin `MANAGE_BUILDER`. Mesin keputusan
 * non-modul belum menyediakan endpoint daftar/undang — statusnya ditampilkan jujur, bukan
 * tombol yang akan ditolak server.
 */
@Composable
internal fun BuilderCollaboratorsTab() {
    val typography = rememberClayTypography()
    ClayCard {
        Text("Kolaborator", fontWeight = FontWeight.Bold, color = WeMadeColors.OnSurface)
        Text(
            "Undang anggota tim dengan izin Builder (MANAGE_BUILDER) setelah mesin keputusan " +
                    "non-modul tersedia. Saat ini pemilik project dan platform superadmin punya akses.",
            style = typography.bodySmall,
            color = WeMadeColors.OnSurfaceMuted
        )
    }
}

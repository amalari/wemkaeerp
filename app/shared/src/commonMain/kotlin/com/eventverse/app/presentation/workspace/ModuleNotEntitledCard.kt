package com.eventverse.app.presentation.workspace

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
import com.eventverse.app.domain.pack.VocabularyKey
import com.eventverse.app.presentation.designsystem.ClayBadge
import com.eventverse.app.presentation.designsystem.ClayCard
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Ditampilkan ketika modulnya memang belum disambungkan ke tenant ini.
 *
 * Sengaja **bukan** [AccessDeniedCard]. Keduanya sama-sama berarti "layar ini tidak terbuka", tetapi
 * memperbaikinya dilakukan oleh dua orang yang berbeda di dua tempat yang berbeda: wewenang diatur
 * admin pabrik lewat matriks RBAC, entitlement diatur superadmin platform lewat pengaturan tenant.
 * Menyamakan pesannya akan mengirim admin menyisir layar RBAC untuk masalah yang tidak akan pernah
 * bisa diselesaikan di sana.
 *
 * [workplace] = istilah pack untuk tempat kerja pemakainya (A4): `"pabrik"` di konveksi, `"klinik"`
 * di klinik. Bawaannya kata netral [`VocabularyKey.WORKPLACE`], **bukan** kata konveksi.
 */
@Composable
fun ModuleNotEntitledCard(
    moduleName: String,
    tenantName: String,
    modifier: Modifier = Modifier,
    workplace: String = VocabularyKey.WORKPLACE.neutral
) {
    ClayCard(
        modifier = modifier.fillMaxWidth().widthIn(max = 640.dp),
        containerColor = WeMadeColors.WarningBg,
        outlineColor = WeMadeColors.Outline
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Modul Belum Aktif",
                modifier = Modifier.weight(1f, fill = false),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = WeMadeColors.Warning
            )
            ClayBadge(text = "Tidak Berlangganan", tint = WeMadeColors.Warning)
        }

        Column(
            modifier = Modifier.padding(top = ClaySpacing.Lg),
            verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
        ) {
            Text(
                text = "Modul $moduleName belum disambungkan ke $tenantName.",
                fontSize = 13.sp,
                color = WeMadeColors.OnSurface
            )
            Text(
                text = "Ini bukan soal wewenang jabatan Anda — mengatur ulang matriks Hak Akses " +
                    "tidak akan membukanya.",
                fontSize = 12.sp,
                color = WeMadeColors.OnSurfaceMuted
            )
            Text(
                text = "Hubungi administrator platform WeMade untuk mengaktifkan modul ini bagi " +
                    "$workplace Anda.",
                fontSize = 12.sp,
                color = WeMadeColors.OnSurfaceMuted
            )
        }
    }
}

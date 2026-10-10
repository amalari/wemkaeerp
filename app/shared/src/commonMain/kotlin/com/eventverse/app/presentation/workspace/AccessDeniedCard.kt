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
import com.eventverse.app.presentation.designsystem.ClayBadge
import com.eventverse.app.presentation.designsystem.ClayCard
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.theme.WeMadeColors

/** Label lencana kartu; kosakata pengguna, bukan nama enum `AccessLevel.NONE`. */
internal const val ACCESS_DENIED_BADGE = "Tanpa Akses"

/**
 * Ditampilkan ketika modul dibuka dengan wewenang `NONE`.
 *
 * Menyebut divisi dan jabatan persona secara eksplisit. Pesan "akses ditolak" tanpa konteks
 * memaksa penguji menebak apakah yang salah adalah konfigurasi, persona, atau sistemnya.
 */
@Composable
fun AccessDeniedCard(
    moduleName: String,
    personaName: String,
    roleTitle: String,
    departmentName: String,
    modifier: Modifier = Modifier
) {
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
                text = "Akses Ditutup",
                modifier = Modifier.weight(1f, fill = false),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = WeMadeColors.Error
            )
            ClayBadge(text = ACCESS_DENIED_BADGE, tint = WeMadeColors.Error)
        }

        Column(
            modifier = Modifier.padding(top = ClaySpacing.Lg),
            verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
        ) {
            Text(
                text = "Modul $moduleName tidak dibuka untuk persona ini.",
                fontSize = 13.sp,
                color = WeMadeColors.OnSurface
            )
            Text(
                text = "$personaName - $roleTitle, divisi $departmentName.",
                fontSize = 12.sp,
                color = WeMadeColors.OnSurfaceMuted
            )
            Text(
                text = "Wewenang dapat dibuka lewat jabatan di layar Hak Akses (RBAC), " +
                    "atau lewat penugasan modul ke divisinya.",
                fontSize = 12.sp,
                color = WeMadeColors.OnSurfaceMuted
            )
        }
    }
}

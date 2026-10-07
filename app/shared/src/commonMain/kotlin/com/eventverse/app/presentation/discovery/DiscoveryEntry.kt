package com.eventverse.app.presentation.discovery

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Pintu masuk Studio Discovery. Pengguna yang **punya tenant dan boleh membuka Builder** langsung dialihkan ke Builder
 * chat (PLAN-builder-interview-chat K7) — di sana wawancara dan penyusunan alur berlangsung sebagai chat. Prospek
 * tanpa tenant (belum punya project) tetap memakai wizard, karena Builder butuh konteks tenant dan izin
 * `MANAGE_BUILDER` (server menolak selain itu). Pengalihan hanya rute; kode wizard tidak dihapus sehingga mudah dibatalkan.
 */
@Composable
fun DiscoveryEntry(redirectToBuilder: Boolean, onOpenBuilder: () -> Unit) {
    if (redirectToBuilder) {
        LaunchedEffect(Unit) { onOpenBuilder() }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Membuka Builder...", color = WeMadeColors.OnSurfaceMuted)
        }
    } else {
        DiscoveryWizardScreen()
    }
}

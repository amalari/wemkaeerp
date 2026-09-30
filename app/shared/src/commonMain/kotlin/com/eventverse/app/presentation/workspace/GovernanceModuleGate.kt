package com.eventverse.app.presentation.workspace

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.eventverse.app.domain.pack.VocabularyKey
import com.eventverse.app.domain.rbac.AccessDecision
import com.eventverse.app.domain.rbac.ModuleAccessConfig
import com.eventverse.app.domain.rbac.TestingPersona
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.navigation.AppNavScreen
import com.eventverse.app.presentation.pack.ActiveTenantPack

/**
 * Gerbang bertingkat untuk ketiga layar tata kelola (Bagan Organisasi, Hak Akses, Alur Pabrik).
 *
 * Tiga keadaan "layar ini tidak terbuka" yang tampak mirip tetapi diperbaiki di tiga tempat berbeda,
 * dan karena itu pantas diberi tiga pesan berbeda:
 *
 * | Keadaan | Diperbaiki oleh | Di mana |
 * |---|---|---|
 * | belum login | pengguna sendiri | layar login |
 * | modul tidak disambungkan ke tenant | superadmin platform | pengaturan tenant |
 * | wewenang jabatan `NONE` | admin pabrik | matriks RBAC |
 *
 * Menyatukan ketiganya menjadi satu pesan "akses ditolak" membuat dua dari tiga orang itu mencari
 * di layar yang tidak akan pernah menyelesaikan masalahnya.
 *
 * Ini pembungkus berbasis domain, jadi ia tinggal di lapisan fitur — bukan di `designsystem/`, yang
 * tidak boleh mengenal [AccessDecision] (Kontrak 6).
 */
@Composable
fun GovernanceModuleGate(
    screen: AppNavScreen,
    isAuthenticated: Boolean,
    decision: AccessDecision?,
    persona: TestingPersona?,
    tenantName: String,
    /** Kartu "silakan login" milik shell aplikasi, diberikan sebagai lambda agar gerbang ini tidak
     *  perlu mengenal komponen privat `App.kt`. */
    authGuard: @Composable () -> Unit,
    content: @Composable (ModuleAccessConfig) -> Unit
) {
    if (!isAuthenticated) {
        authGuard()
        return
    }

    // Keputusan yang belum tiba diperlakukan sebagai terbuka penuh, bukan tertutup.
    //
    // `accessDecisions` kosong sampai persona aktif terpasang, dan itu terjadi beberapa saat setelah
    // sesi ada. Menutup layar selama jeda itu akan menampilkan "akses ditolak" berkedip pada setiap
    // muat ulang halaman — perilaku yang akan dilaporkan sebagai kerusakan, bukan sebagai keamanan.
    // Penjagaan yang sebenarnya tetap ada di server; ini hanya lapisan tampilan.
    if (decision == null) {
        content(ModuleAccessConfig())
        return
    }

    when {
        decision.blockedByEntitlement -> GateMessage {
            ModuleNotEntitledCard(
                moduleName = screen.title,
                tenantName = tenantName,
                workplace = ActiveTenantPack.current.term(VocabularyKey.WORKPLACE)
            )
        }

        !decision.config.isAccessible -> GateMessage {
            AccessDeniedCard(
                moduleName = screen.title,
                personaName = persona?.name ?: "Tanpa persona",
                roleTitle = persona?.roleTitle ?: "Tanpa jabatan",
                departmentName = persona?.departmentName ?: "Tanpa divisi"
            )
        }

        else -> content(decision.config)
    }
}

@Composable
private fun GateMessage(card: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(ClaySpacing.Xxl),
        verticalArrangement = Arrangement.spacedBy(ClaySpacing.Xl)
    ) {
        card()
    }
}

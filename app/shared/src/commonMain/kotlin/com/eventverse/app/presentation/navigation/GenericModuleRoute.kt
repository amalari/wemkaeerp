package com.eventverse.app.presentation.navigation

import com.eventverse.app.domain.pack.DomainPack
import com.eventverse.app.domain.pack.VocabularyKey
import com.eventverse.app.presentation.pack.ActiveTenantPack
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.eventverse.app.presentation.designsystem.ClayCard
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.theme.WeMadeColors
import com.eventverse.app.domain.rbac.AccessDecision
import com.eventverse.app.domain.rbac.AccessSource
import com.eventverse.app.domain.rbac.BusinessModule
import com.eventverse.app.domain.rbac.BusinessModules
import com.eventverse.app.domain.rbac.ModuleAccessConfig
import com.eventverse.app.domain.rbac.TestingPersona
import com.eventverse.app.presentation.workspace.ModuleWorkspaceScreen

/** Modul yang dituju path `/m/{code}`; null bila code bukan modul pack aktif. */
internal fun moduleFromGenericPath(path: String, pack: DomainPack = ActiveTenantPack.current): BusinessModule? {
    val code = path.substringAfter("${AppNavScreen.MODULE.route}/", "").substringBefore('/').substringBefore('?')
    // Hanya modul pack tenant (B7): modul vertikal lain yang kebetulan dikenal registry tidak boleh terbuka di sini.
    return pack.modules.firstOrNull { it.id.value.equals(code, ignoreCase = true) }?.id
}

/**
 * Layar `/m/{code}` (B6f): modul pack yang belum punya layar khusus dibuka di layar kerja generik, tergerbang
 * keputusan wewenangnya sendiri. Modul yang **punya** layar khusus tetap memakai layar itu lewat `ModuleScreenRegistry`.
 *
 * Kosakata chrome (A4) datang dari pack tenant yang sedang aktif, jadi tenant non-konveksi tidak melihat
 * kata "pabrik" di layarnya sendiri.
 */
@Composable
internal fun GenericModuleRoute(
    path: String,
    accessDecisions: Map<BusinessModule, AccessDecision>,
    persona: TestingPersona?
) {
    val pack = ActiveTenantPack.current
    val module = moduleFromGenericPath(path, pack) ?: return UnknownModuleCard(path, pack)
    val none = ModuleAccessConfig()
    ModuleWorkspaceScreen(
        module = module,
        decision = accessDecisions[module] ?: AccessDecision(none, AccessSource.NONE, none, none),
        persona = persona,
        pack = pack
    )
}

@Composable
private fun UnknownModuleCard(path: String, pack: DomainPack) {
    androidx.compose.foundation.layout.Box(
        modifier = androidx.compose.ui.Modifier.fillMaxSize().padding(ClaySpacing.Xxl)
    ) {
        ClayCard {
            androidx.compose.foundation.layout.Column(
                modifier = androidx.compose.ui.Modifier.padding(ClaySpacing.Xl),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(ClaySpacing.Sm)
            ) {
                Text("Modul tidak ditemukan", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Alamat $path tidak menunjuk modul yang tersedia di " +
                        "${pack.term(VocabularyKey.WORKPLACE)} ini.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = WeMadeColors.OnSurfaceMuted
                )
            }
        }
    }
}

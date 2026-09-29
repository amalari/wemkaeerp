package com.eventverse.app.presentation.navigation

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
internal fun moduleFromGenericPath(path: String): BusinessModule? =
    BusinessModules.fromCode(path.substringAfter("${AppNavScreen.MODULE.route}/", "").substringBefore('/').substringBefore('?'))

/**
 * Layar `/m/{code}` (B6f): modul pack yang belum punya layar khusus dibuka di layar kerja generik, tergerbang
 * keputusan wewenangnya sendiri. Modul yang **punya** layar khusus tetap memakai layar itu lewat `ModuleScreenRegistry`.
 */
@Composable
internal fun GenericModuleRoute(
    path: String,
    accessDecisions: Map<BusinessModule, AccessDecision>,
    persona: TestingPersona?
) {
    val module = moduleFromGenericPath(path) ?: return UnknownModuleCard(path)
    val none = ModuleAccessConfig()
    ModuleWorkspaceScreen(
        module = module,
        decision = accessDecisions[module] ?: AccessDecision(none, AccessSource.NONE, none, none),
        persona = persona
    )
}

@Composable
private fun UnknownModuleCard(path: String) {
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
                    "Alamat $path tidak menunjuk modul yang tersedia di pabrik ini.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = WeMadeColors.OnSurfaceMuted
                )
            }
        }
    }
}

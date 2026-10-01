package com.eventverse.app.presentation.platform

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.eventverse.app.presentation.builder.BuilderBuildQueuePane
import com.eventverse.app.presentation.builder.BuilderMenuItem
import com.eventverse.app.presentation.builder.BuilderSidebar
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.discovery.studio.DemandLedgerScreen
import com.eventverse.app.presentation.theme.WeMadeColors

private val consoleMenu = listOf(
    BuilderMenuItem("tenants", "Tenants"),
    BuilderMenuItem("buildqueue", "Antrian Pembuatan"),
    BuilderMenuItem("demand", "Buku Demand")
)

/**
 * Konsol platform superadmin di `app.<base>/admin` (discovery-M3b, PLAN-builder-console §3).
 *
 * Superadmin adalah **operator platform**, bukan pengguna aplikasi hasil: rumahnya di sini, dan ia masuk
 * ke tenant (Builder maupun aplikasi) lewat act-as yang selalu tercatat di audit log tenant tujuan.
 * Billing lintas-tenant dan entitlement lanjutan menyusul; yang ada sekarang memakai ulang pane yang
 * sudah ada.
 */
@Composable
fun PlatformAdminConsole(
    onActAs: suspend (slug: String, landingPath: String) -> Result<Unit>,
    modifier: Modifier = Modifier
) {
    var selected by remember { mutableStateOf("tenants") }

    Row(modifier = modifier.fillMaxSize().background(WeMadeColors.Background)) {
        BuilderSidebar(
            title = "WeMake ERP · Platform",
            menu = consoleMenu,
            selected = selected,
            onSelect = { selected = it.key },
            modifier = Modifier.width(248.dp).fillMaxHeight()
        )
        Column(
            modifier = Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(ClaySpacing.Xl),
            verticalArrangement = Arrangement.spacedBy(ClaySpacing.Lg)
        ) {
            when (selected) {
                "tenants" -> PlatformTenantsPane(onActAs)
                "buildqueue" -> BuilderBuildQueuePane()
                "demand" -> DemandLedgerScreen(isSuperadmin = true)
            }
        }
    }
}

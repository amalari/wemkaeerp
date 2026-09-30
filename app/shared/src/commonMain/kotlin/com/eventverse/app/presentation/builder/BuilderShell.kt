package com.eventverse.app.presentation.builder

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.presentation.designsystem.ClayBadge
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.theme.WeMadeColors
import com.eventverse.app.presentation.designsystem.rememberClayTypography

/** Satu entri menu sidebar Builder. Data polos — komponen shell buta domain (Kontrak 6). */
internal data class BuilderMenuItem(
    val key: String,
    val label: String,
    val enabled: Boolean = true,
    val badge: String? = null
)

private fun builderMenu() = listOf(
    BuilderMenuItem("overview", "Overview"),
    BuilderMenuItem("chat", "Chat"),
    BuilderMenuItem("modules", "Modules"),
    BuilderMenuItem("dataflow", "Data Flow"),
    BuilderMenuItem("prototype", "Prototype"),
    BuilderMenuItem("buildqueue", "Antrian Pembuatan", enabled = false, badge = "M2"),
    BuilderMenuItem("deployments", "Deployments", enabled = false, badge = "M2"),
    BuilderMenuItem("billing", "Billing", enabled = false, badge = "M2"),
    BuilderMenuItem("settings", "Pengaturan")
)

/**
 * Shell WeMake Builder (PLAN-builder-console §6): sidebar + konten, URL `<slug>/builder`.
 * M0 membuka **Overview** dan **Pengaturan**; M1 membuka **Chat, Modules, Data Flow, Prototype**;
 * menu M2 tampil tapi terkunci — bukan disembunyikan, supaya pemilik project melihat arah produknya.
 *
 * Data diambil dari endpoint `GET /api/builder/...` oleh pane; gerbang `MANAGE_BUILDER` ada di server,
 * layar ini hanya menampilkan pesannya (fail-closed tetap di API).
 */
@Composable
fun BuilderShell(modifier: Modifier = Modifier) {
    var selected by remember { mutableStateOf("overview") }
    val menu = remember { builderMenu() }

    Row(modifier = modifier.fillMaxSize().background(WeMadeColors.Background)) {
        BuilderSidebar(
            menu = menu,
            selected = selected,
            onSelect = { if (it.enabled) selected = it.key },
            modifier = Modifier.width(248.dp).fillMaxHeight()
        )
        Column(
            modifier = Modifier.weight(1f).fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(ClaySpacing.Xl),
            verticalArrangement = Arrangement.spacedBy(ClaySpacing.Lg)
        ) {
            when (selected) {
                "overview" -> BuilderOverviewPane()
                "chat" -> BuilderChatPane()
                "modules" -> BuilderModulesPane()
                "dataflow" -> BuilderDataFlowPane()
                "prototype" -> BuilderPrototypePane()
                "settings" -> BuilderSettingsPane()
            }
        }
    }
}

@Composable
private fun BuilderSidebar(
    menu: List<BuilderMenuItem>,
    selected: String,
    onSelect: (BuilderMenuItem) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.background(WeMadeColors.Surface).padding(ClaySpacing.Lg),
        verticalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)
    ) {
        Text(
            text = "WeMake Builder",
            style = rememberClayTypography().titleMedium,
            fontWeight = FontWeight.Bold,
            color = WeMadeColors.OnSurface,
            modifier = Modifier.padding(bottom = ClaySpacing.Md)
        )
        menu.forEach { item ->
            val tint = when {
                !item.enabled -> WeMadeColors.OnSurfaceDisabled
                item.key == selected -> WeMadeColors.Primary
                else -> WeMadeColors.OnSurface
            }
            Row(
                modifier = Modifier.fillMaxWidth()
                        .background(
                            if (item.key == selected) WeMadeColors.Primary.copy(alpha = 0.10f) else Color.Transparent
                        )
                        .clickable(enabled = item.enabled) { onSelect(item) }
                        .padding(ClaySpacing.Sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.label,
                    color = tint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                item.badge?.let { phase ->
                    Spacer(Modifier.width(ClaySpacing.Sm))
                    ClayBadge(text = phase, tint = WeMadeColors.OnSurfaceMuted, fontSize = 9.sp)
                }
            }
        }
    }
}

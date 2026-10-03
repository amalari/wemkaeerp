package com.eventverse.app.presentation.builder

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.presentation.builder.chat.BuilderChatPane
import com.eventverse.app.presentation.designsystem.ClayBadge
import com.eventverse.app.presentation.designsystem.ClayBorder
import com.eventverse.app.presentation.designsystem.ClayButton
import com.eventverse.app.presentation.designsystem.ClayButtonStyle
import com.eventverse.app.presentation.designsystem.ClayShapes
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.IconActivity
import com.eventverse.app.presentation.designsystem.IconArrowBack
import com.eventverse.app.presentation.designsystem.IconChat
import com.eventverse.app.presentation.designsystem.IconDatabase
import com.eventverse.app.presentation.designsystem.IconGlobe
import com.eventverse.app.presentation.designsystem.IconLayers
import com.eventverse.app.presentation.designsystem.IconPackage
import com.eventverse.app.presentation.designsystem.IconReceipt
import com.eventverse.app.presentation.designsystem.IconShield
import com.eventverse.app.presentation.designsystem.IconZap
import com.eventverse.app.presentation.designsystem.rememberClayTypography
import com.eventverse.app.presentation.theme.WeMadeColors

/** Satu entri menu sidebar Builder dengan ikon kanvas native. */
internal data class BuilderMenuItem(
    val key: String,
    val label: String,
    val enabled: Boolean = true,
    val badge: String? = null,
    val icon: (@Composable (Modifier, Color) -> Unit)? = null
)

private fun builderMenu() = listOf(
    BuilderMenuItem("overview", "Overview", icon = { m, c -> IconGlobe(m, c) }),
    BuilderMenuItem("chat", "Chat AI", icon = { m, c -> IconChat(m, c) }),
    BuilderMenuItem("modules", "Modules", icon = { m, c -> IconLayers(m, c) }),
    BuilderMenuItem("dataflow", "Data Flow", icon = { m, c -> IconDatabase(m, c) }),
    BuilderMenuItem("prototype", "Prototype", icon = { m, c -> IconActivity(m, c) }),
    BuilderMenuItem("buildqueue", "Antrian Pembuatan", icon = { m, c -> IconPackage(m, c) }),
    BuilderMenuItem("deployments", "Deployments", icon = { m, c -> IconZap(m, c) }),
    BuilderMenuItem("billing", "Billing", icon = { m, c -> IconReceipt(m, c) }),
    BuilderMenuItem("settings", "Pengaturan", icon = { m, c -> IconShield(m, c) })
)

/**
 * Shell WeMake Builder (PLAN-builder-console §6): sidebar Vercel-style + konten, URL `<slug>/builder`.
 * Mengintegrasikan navigasi tab interaktif, ikon modern, dan styling claymorphic.
 *
 * Setiap menu punya **route sendiri** (`/builder/<key>`) supaya reload & Back/Forward browser tetap
 * membuka pane yang sama: [section] diturunkan App dari `shellPath`, dan [onSectionChange] mendorong
 * URL baru (App memanggil `goShell`). Sumber kebenaran tunggal = URL; state lokal hanya cermin.
 */
@Composable
fun BuilderShell(
    modifier: Modifier = Modifier,
    section: String = "overview",
    onSectionChange: (String) -> Unit = {},
    onOpenApp: (suspend () -> Any?)? = null,
    onBackToConsole: (() -> Unit)? = null
) {
    val menu = remember { builderMenu() }
    var selected by remember {
        mutableStateOf(menu.firstOrNull { it.key == section }?.key ?: "overview")
    }
    // Back/Forward (popstate): URL berubah di luar → pane mengikuti; key tak dikenal diabaikan.
    LaunchedEffect(section) {
        if (menu.any { it.key == section } && section != selected) selected = section
    }
    fun select(key: String) {
        if (selected == key) return
        selected = key
        onSectionChange(key)
    }

    Row(modifier = modifier.fillMaxSize().background(WeMadeColors.Background)) {
        BuilderSidebar(
            title = "WeMake Builder",
            menu = menu,
            selected = selected,
            onSelect = { if (it.enabled) select(it.key) },
            modifier = Modifier.width(260.dp).fillMaxHeight(),
            onOpenApp = onOpenApp,
            onBackToConsole = onBackToConsole
        )

        // Area Konten Utama
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                // Chat butuh tinggi terbatas (komposer menempel di bawah); pane lain menggulir sendiri.
                .then(if (selected == "chat") Modifier else Modifier.verticalScroll(rememberScrollState()))
                .padding(horizontal = ClaySpacing.Xxl, vertical = ClaySpacing.Xl)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (selected == "chat") Modifier.fillMaxHeight() else Modifier.padding(bottom = ClaySpacing.Xxl)),
                verticalArrangement = Arrangement.spacedBy(ClaySpacing.Lg)
            ) {
                when (selected) {
                    "overview" -> BuilderOverviewPane(onNavigate = { select(it) })
                    "chat" -> BuilderChatPane()
                    "modules" -> BuilderModulesPane()
                    "dataflow" -> BuilderDataFlowPane()
                    "prototype" -> BuilderPrototypePane()
                    "buildqueue" -> BuilderBuildQueuePane()
                    "deployments" -> BuilderDeploymentsPane()
                    "billing" -> BuilderBillingPane()
                    "settings" -> BuilderSettingsPane()
                }
            }
        }
    }
}

/** Sidebar shell konsol ala Vercel: Brand Header, Pill Navigation, dan Platform Info Footer. */
@Composable
internal fun BuilderSidebar(
    title: String = "WeMake Builder",
    menu: List<BuilderMenuItem>,
    selected: String,
    onSelect: (BuilderMenuItem) -> Unit,
    modifier: Modifier = Modifier,
    onOpenApp: (suspend () -> Any?)? = null,
    onBackToConsole: (() -> Unit)? = null
) {
    val typography = rememberClayTypography()
    val scope = rememberCoroutineScope()

    Column(
        modifier = modifier
            .background(WeMadeColors.Surface)
            .border(ClayBorder.Hairline, WeMadeColors.Border)
            .padding(ClaySpacing.Lg),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)) {
            // Header Brand & Project Selector ala Vercel
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm),
                modifier = Modifier.padding(bottom = ClaySpacing.Xs)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(WeMadeColors.Primary, ClayShapes.Tile),
                    contentAlignment = Alignment.Center
                ) {
                    IconLayers(Modifier.size(18.dp), color = Color.White)
                }
                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(
                        text = title,
                        style = typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = WeMadeColors.OnSurface
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(Modifier.size(6.dp).background(WeMadeColors.Success, CircleShape))
                        Text(
                            text = "Garment Platform",
                            style = typography.bodySmall,
                            fontSize = 10.sp,
                            color = WeMadeColors.OnSurfaceMuted
                        )
                    }
                }
            }

            // Garis pembatas halus
            Box(Modifier.fillMaxWidth().height(1.dp).background(WeMadeColors.Border))

            Text(
                text = "NAVIGASI UTAMA",
                style = typography.bodySmall,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = WeMadeColors.OnSurfaceDisabled,
                modifier = Modifier.padding(horizontal = ClaySpacing.Sm, vertical = 2.dp)
            )

            // Daftar Menu Sidebar
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                menu.forEach { item ->
                    val isSelected = item.key == selected
                    val tint = when {
                        !item.enabled -> WeMadeColors.OnSurfaceDisabled
                        isSelected -> WeMadeColors.Primary
                        else -> WeMadeColors.OnSurface
                    }
                    val iconTint = when {
                        !item.enabled -> WeMadeColors.OnSurfaceDisabled
                        isSelected -> WeMadeColors.Primary
                        else -> WeMadeColors.OnSurfaceMuted
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = if (isSelected) WeMadeColors.Primary.copy(alpha = 0.10f) else Color.Transparent,
                                shape = ClayShapes.Chip
                            )
                            .clickable(enabled = item.enabled) { onSelect(item) }
                            .padding(horizontal = ClaySpacing.Md, vertical = ClaySpacing.Sm),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
                    ) {
                        item.icon?.invoke(Modifier.size(16.dp), iconTint)
                        Text(
                            text = item.label,
                            style = typography.bodyMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = tint,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        item.badge?.let { phase ->
                            Spacer(Modifier.width(ClaySpacing.Xs))
                            ClayBadge(text = phase, tint = WeMadeColors.OnSurfaceMuted, fontSize = 9.sp)
                        }
                    }
                }
            }
        }

        // Footer Sidebar
        Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
            onOpenApp?.let { open ->
                ClayButton(
                    text = "Buka Aplikasi ERP",
                    onClick = { scope.launch { open() } },
                    style = ClayButtonStyle.Secondary,
                    fontSize = 11.sp,
                    leading = { IconGlobe(Modifier.size(12.dp), color = WeMadeColors.OnSurface) }
                )
            }
            onBackToConsole?.let { back ->
                ClayButton(
                    text = "Kembali ke Admin",
                    onClick = back,
                    style = ClayButtonStyle.Secondary,
                    fontSize = 11.sp,
                    leading = { IconArrowBack(Modifier.size(12.dp), color = WeMadeColors.OnSurface) }
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(WeMadeColors.SurfaceMuted, ClayShapes.Tile)
                    .border(ClayBorder.Hairline, WeMadeColors.Border, ClayShapes.Tile)
                    .padding(ClaySpacing.Sm)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Platform Flow (Jalur B)",
                        style = typography.bodySmall,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = WeMadeColors.OnSurface
                    )
                    Text(
                        text = "v2026.09 · Multi-tenant Mode",
                        style = typography.bodySmall,
                        fontSize = 9.sp,
                        color = WeMadeColors.OnSurfaceMuted
                    )
                }
            }
        }
    }
}

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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.domain.builder.BuilderConsoleMenu
import com.eventverse.app.domain.builder.BuilderLiveSignal
import com.eventverse.app.domain.builder.BuilderMenuKey
import com.eventverse.app.domain.builder.BuilderRouteMap
import com.eventverse.app.domain.i18n.LanguageCode
import com.eventverse.app.presentation.builder.chat.BuilderChatEmptyState
import com.eventverse.app.presentation.builder.chat.BuilderChatPane
import com.eventverse.app.presentation.builder.audit.BuilderAuditPane
import com.eventverse.app.presentation.builder.handoff.BuilderHandoffPane
import com.eventverse.app.presentation.builder.live.BuilderLivePane
import com.eventverse.app.presentation.builder.setting.BuilderSettingPane
import com.eventverse.app.presentation.designsystem.ClayBadge
import com.eventverse.app.presentation.designsystem.ClayBorder
import com.eventverse.app.presentation.designsystem.ClayButton
import com.eventverse.app.presentation.designsystem.ClayButtonStyle
import com.eventverse.app.presentation.designsystem.ClayShapes
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.IconArrowBack
import com.eventverse.app.presentation.designsystem.IconChat
import com.eventverse.app.presentation.designsystem.IconEdit
import com.eventverse.app.presentation.designsystem.IconGlobe
import com.eventverse.app.presentation.designsystem.IconHandshake
import com.eventverse.app.presentation.designsystem.IconLayers
import com.eventverse.app.presentation.designsystem.IconShield
import com.eventverse.app.presentation.designsystem.rememberClayTypography
import com.eventverse.app.presentation.theme.WeMadeColors
import kotlinx.coroutines.launch

/** Satu entri menu sidebar/konsol dengan ikon kanvas native; label sudah teks jadi dari pemanggil. */
internal data class BuilderMenuItem(
    val key: String,
    val label: String,
    val enabled: Boolean = true,
    val badge: String? = null,
    val badgeTint: Color? = null,
    val icon: (@Composable (Modifier, Color) -> Unit)? = null
)

/** Ikon menu v2 per kontrak `BuilderConsoleMenu` — data kontrak yang memilih ikon, bukan urutan list. */
private val builderMenuIcons: Map<BuilderMenuKey, @Composable (Modifier, Color) -> Unit> = mapOf(
    BuilderConsoleMenu.CHAT to { m, c -> IconChat(m, c) },
    BuilderConsoleMenu.LIVE to { m, c -> IconGlobe(m, c) },
    BuilderConsoleMenu.HANDOFF to { m, c -> IconHandshake(m, c) },
    BuilderConsoleMenu.AUDIT to { m, c -> IconShield(m, c) },
    BuilderConsoleMenu.SETTING to { m, c -> IconEdit(m, c) },
)

/**
 * Menu Builder v2 (TRD-PLAT-014 FR-1-2/3): **lima entri per momen pelanggan** dari kontrak Track 0
 * `BuilderConsoleMenu` — label id+en sebagai data, diresolve ke [language] bahasa aktif (mekanisme
 * bahasa yang sama dengan menu modul). Badge (FR-1-4) opsional dari [BuilderMenuBadges]:
 * Handoff = angka antrean aktif, Live = titik sinyal deployment.
 */
internal fun builderMenu(
    language: LanguageCode,
    badges: BuilderMenuBadges? = null,
): List<BuilderMenuItem> = BuilderConsoleMenu.entries.map { entry ->
    BuilderMenuItem(
        key = entry.key.value,
        label = entry.labelText.resolve(language).text,
        badge = when (entry.key) {
            BuilderConsoleMenu.HANDOFF -> badges?.handoffActiveCount?.takeIf { it > 0 }?.toString()
            BuilderConsoleMenu.LIVE -> badges
                // Titik sinyal Live: U+00B7 satu-satunya glyph titik yang aman di Nunito (Latin-1).
                ?.liveSignal?.takeIf { it != BuilderLiveSignal.NOT_DEPLOYED }?.let { "·" }
            else -> null
        },
        badgeTint = if (entry.key == BuilderConsoleMenu.LIVE) badges?.liveSignal?.badgeTint() else null,
        icon = builderMenuIcons[entry.key],
    )
}

/**
 * Shell WeMake Builder v2 (TRD-PLAT-014 FR-1): hanya area konten — menu Builder hidup di drawer
 * header (`builderDrawerSections`, sama seperti navigasi platform), jadi seluruh lebar layar milik pane.
 *
 * Setiap menu punya **route sendiri** (`/builder/<key>`; Live dan Setting punya sub-segmen
 * `live/preview`, `live/production`, `setting/billing`) supaya reload & Back/Forward browser tetap
 * membuka pane yang sama: [section] diturunkan App dari `shellPath`. Sumber kebenaran tunggal = URL.
 * Default kini **chat** (FR-1-1); key menu lama dialihkan lewat [BuilderRouteMap] — redirect klien,
 * deep-link lama tidak pernah 404. Pane [fullHeight] (chat) mengelola scroll-nya sendiri; pane lain
 * digulir shell.
 */
@Composable
fun BuilderShell(
    modifier: Modifier = Modifier,
    section: String = BuilderConsoleMenu.CHAT.value,
    /** Nilai `?template=` pada route internal (`/builder/chat?template=bordir`), untuk alur wizard. */
    templateCode: String? = null,
    onSectionChange: (String) -> Unit = {},
    /** Membuka modul live aplikasi tenant (`/m/{code}`) dari tab Production. */
    onOpenModule: (String) -> Unit = {}
) {
    // Redirect deep-link lama (FR-1-2): resolve dulu ke tujuan baru; pemetaan identitas ("chat" →
    // "chat") tidak di-push ulang ke riwayat, hanya pemetaan lintas lokasi yang memicu perubahan URL.
    val resolved = BuilderRouteMap.resolve(section)
    val selected = (resolved ?: section).ifBlank { BuilderConsoleMenu.CHAT.value }
    if (resolved != null && resolved != section) {
        LaunchedEffect(section) { onSectionChange(resolved) }
    }
    val menuKey = selected.substringBefore('/')
    // Key asing (bukan menu v2, bukan route lama) jatuh ke default chat — perilaku fallback lama,
    // bukan layar 404.
    val route = if (menuKey in builderRouteKeys) selected else BuilderConsoleMenu.CHAT.value
    val fullHeight = route == BuilderConsoleMenu.CHAT.value

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(WeMadeColors.Background)
            .then(if (fullHeight) Modifier else Modifier.verticalScroll(rememberScrollState()))
            .padding(
                horizontal = if (fullHeight) ClaySpacing.Lg else ClaySpacing.Xxl,
                vertical = if (fullHeight) ClaySpacing.Md else ClaySpacing.Xl
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (fullHeight) Modifier.fillMaxHeight() else Modifier.padding(bottom = ClaySpacing.Xxl)),
            verticalArrangement = Arrangement.spacedBy(ClaySpacing.Lg)
        ) {
            when (route.substringBefore('/')) {
                BuilderConsoleMenu.CHAT.value -> BuilderChatPane(
                    templateCode = templateCode,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    emptyState = { BuilderChatEmptyState(onNavigate = onSectionChange) }
                )
                BuilderConsoleMenu.LIVE.value -> BuilderLivePane(
                    tab = route.substringAfter('/', missingDelimiterValue = "preview"),
                    onNavigate = onSectionChange,
                    onOpenModule = onOpenModule
                )
                BuilderConsoleMenu.HANDOFF.value -> BuilderHandoffPane()
                BuilderConsoleMenu.AUDIT.value -> BuilderAuditPane()
                BuilderConsoleMenu.SETTING.value -> BuilderSettingPane(
                    initialTab = route.substringAfter('/', missingDelimiterValue = "").ifBlank { null }
                )
                "newpack" -> BuilderNewPackScreen(onNavigate = onSectionChange)
            }
        }
    }
}

/** Segmen pertama route v2 yang sah — kontrak `BuilderConsoleMenu` + layar aksi `newpack`. */
private val builderRouteKeys: Set<String> = BuilderConsoleMenu.entries.map { it.key.value }.toSet() + "newpack"

/**
 * Sidebar konsol platform superadmin (`PlatformAdminConsole`) ala Vercel: Brand Header, Pill Navigation, dan Platform Info Footer.
 */
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
                        item.badge?.let { badge ->
                            Spacer(Modifier.width(ClaySpacing.Xs))
                            ClayBadge(
                                text = badge,
                                tint = item.badgeTint ?: WeMadeColors.OnSurfaceMuted,
                                fontSize = 9.sp
                            )
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

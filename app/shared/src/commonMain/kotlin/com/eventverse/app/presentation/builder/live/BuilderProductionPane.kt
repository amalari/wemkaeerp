package com.eventverse.app.presentation.builder.live

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.eventverse.app.domain.builder.BuildRequestStatus
import com.eventverse.app.infrastructure.api.BuilderApiClient
import com.eventverse.app.presentation.builder.BuilderDeploymentItemUi
import com.eventverse.app.presentation.builder.BuilderDeploymentsPane
import com.eventverse.app.presentation.builder.BuilderOverviewUi
import com.eventverse.app.presentation.builder.parseOverview
import com.eventverse.app.presentation.builder.statusTint
import com.eventverse.app.presentation.designsystem.ClayBadge
import com.eventverse.app.presentation.designsystem.ClayCard
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.IconArrowForward
import com.eventverse.app.presentation.designsystem.rememberClayTypography
import com.eventverse.app.presentation.i18n.rememberActiveLanguage
import com.eventverse.app.presentation.pack.ActiveTenantPack
import com.eventverse.app.presentation.theme.WeMadeColors
import com.eventverse.app.domain.rbac.code
import com.eventverse.app.domain.rbac.displayNameText
import com.eventverse.app.domain.rbac.moduleIds
import com.eventverse.app.shared.json.JsonValue

/** Satu permintaan pembuatan yang masih menunggu dikerjakan (badge = "perlu dibangun"). */
internal data class PendingBuildUi(val moduleId: String, val status: BuildRequestStatus)

/**
 * Parse murni `buildRequests` dari `GET /api/builder/deployments`; hanya yang **pending** (kontrak
 * `BuildRequestStatus.isPending`) — SHIPPED/REJECTED/SUPERSEDED bukan "perlu dibangun". Status tak
 * dikenal dilewati: responsnya data tampilan, bukan kontrak domain (lihat KDoc [BuilderApiClient]).
 */
internal fun parsePendingBuilds(raw: JsonValue?): List<PendingBuildUi> {
    val obj = raw as? JsonValue.Obj ?: return emptyList()
    return obj.objectArray("buildRequests")
        .mapNotNull { r ->
            val id = r.string("moduleId")?.takeIf(String::isNotBlank) ?: return@mapNotNull null
            val status = BuildRequestStatus.entries.firstOrNull { it.name == r.string("status") }
            status?.takeIf { it.isPending }?.let { PendingBuildUi(id, it) }
        }
}

/**
 * Tab Production (TRD-PLAT-014 FR-3-2): dunia runtime tenant — status TRIAL/LIVE, deployment aktif,
 * Deploy/Rollback (re-host pane Deployments lama), daftar modul live dengan tautan `/m/{code}`, dan
 * modul "perlu dibangun" yang menaut ke Handoff.
 */
@Composable
internal fun BuilderProductionPane(
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
    onOpenModule: (String) -> Unit = {}
) {
    val client = remember { BuilderApiClient() }
    var overview by remember { mutableStateOf<BuilderOverviewUi?>(null) }
    var pendingBuilds by remember { mutableStateOf<List<PendingBuildUi>>(emptyList()) }

    LaunchedEffect(Unit) {
        client.overview().onSuccess { overview = parseOverview(it) }
        client.deployments().onSuccess { pendingBuilds = parsePendingBuilds(it) }
    }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ClaySpacing.Lg)) {
        overview?.let { ProductionHeader(ui = it) }
        // Re-host pane Deployments lama (Deploy/Rollback + riwayat append-only) — komposisi, bukan salin.
        BuilderDeploymentsPane()
        LiveModulesCard(onOpenModule = onOpenModule)
        PendingBuildsCard(pendingBuilds = pendingBuilds, onOpenHandoff = { onNavigate("handoff") })
    }
}

/** Header status production (materi Overview lama yang menempel di tab ini): identitas + status + deployment aktif. */
@Composable
private fun ProductionHeader(ui: BuilderOverviewUi) {
    val typography = rememberClayTypography()
    val active = ui.deployments.firstOrNull { it.number == ui.activeNumber }

    ClayCard {
        Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = ui.name,
                    style = typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = WeMadeColors.OnSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                ClayBadge(text = ui.status, tint = statusTint(ui.status), dot = true)
            }
            Text(
                text = "${ui.slug}.wemakeerp.com",
                style = typography.bodySmall,
                color = WeMadeColors.OnSurfaceMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = activeDeploymentSummary(ui, active),
                style = typography.bodySmall,
                color = WeMadeColors.OnSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Satu baris deployment aktif dari data overview lama; belum deploy diucapkan apa adanya. */
private fun activeDeploymentSummary(ui: BuilderOverviewUi, active: BuilderDeploymentItemUi?): String {
    if (active == null) return "Belum ada deployment aktif · tenant berjalan tanpa pack terkunci."
    val version = active.packVersion?.let { "v$it" }
        ?: active.appBuild?.let { "build $it" }
        ?: "revisi blueprint ${active.blueprintRevision}"
    return "Deployment aktif #${active.number} · pack ${active.packCode} $version · status ${active.status}"
}

/** Daftar modul live aplikasi tenant — pack aktif tenant — dengan tautan `/m/{code}` (FR-3-2). */
@Composable
private fun LiveModulesCard(onOpenModule: (String) -> Unit) {
    val typography = rememberClayTypography()
    val language = rememberActiveLanguage()
    val modules = ActiveTenantPack.current.moduleIds

    ClayCard {
        Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
            Text(
                text = "Modul live (${modules.size})",
                style = typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = WeMadeColors.OnSurface
            )
            if (modules.isEmpty()) {
                Text(
                    text = "Tenant ini belum punya modul di pack aktifnya.",
                    style = typography.bodySmall,
                    color = WeMadeColors.OnSurfaceMuted
                )
            }
            modules.forEach { module ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenModule(module.code) },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
                ) {
                    Text(
                        text = module.displayNameText.resolve(language).text,
                        style = typography.bodyMedium,
                        color = WeMadeColors.OnSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(Modifier.width(ClaySpacing.Xs))
                    Text(
                        text = "/m/${module.code}",
                        style = typography.bodySmall,
                        fontSize = 11.sp,
                        color = WeMadeColors.Primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/** Modul "perlu dibangun" (permintaan pending) — tiap baris menaut ke Handoff (FR-3-2). */
@Composable
private fun PendingBuildsCard(pendingBuilds: List<PendingBuildUi>, onOpenHandoff: () -> Unit) {
    val typography = rememberClayTypography()

    ClayCard {
        Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
            Text(
                text = "Perlu dibangun (${pendingBuilds.size})",
                style = typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = WeMadeColors.OnSurface
            )
            if (pendingBuilds.isEmpty()) {
                Text(
                    text = "Tidak ada permintaan pembuatan modul yang menunggu.",
                    style = typography.bodySmall,
                    color = WeMadeColors.OnSurfaceMuted
                )
            }
            pendingBuilds.forEach { build ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onOpenHandoff),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
                ) {
                    Text(
                        text = build.moduleId,
                        style = typography.bodyMedium,
                        color = WeMadeColors.OnSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    ClayBadge(text = build.status.name, tint = WeMadeColors.Warning, fontSize = 9.sp)
                    Text(
                        text = "Buka Handoff",
                        style = typography.bodySmall,
                        fontSize = 11.sp,
                        color = WeMadeColors.Primary,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

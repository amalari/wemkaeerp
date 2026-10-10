package com.eventverse.app.presentation.builder

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.infrastructure.api.BuilderApiClient
import com.eventverse.app.presentation.designsystem.ClayBadge
import com.eventverse.app.presentation.designsystem.ClayBorder
import com.eventverse.app.presentation.designsystem.ClayButton
import com.eventverse.app.presentation.designsystem.ClayButtonStyle
import com.eventverse.app.presentation.designsystem.ClayCard
import com.eventverse.app.presentation.designsystem.ClayShapes
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.IconActivity
import com.eventverse.app.presentation.designsystem.IconGlobe
import com.eventverse.app.presentation.designsystem.IconZap
import com.eventverse.app.presentation.designsystem.rememberClayTypography
import com.eventverse.app.presentation.theme.WeMadeColors
import com.eventverse.app.shared.json.JsonValue

/** Satu entri riwayat deployment untuk tabel Overview. */
internal data class BuilderDeploymentItemUi(
    val number: Int,
    val status: String,
    val packCode: String,
    val packVersion: Int?,
    val appBuild: String?,
    val blueprintRevision: Int,
    val createdAt: String?
)

/** Model tampilan Overview; diparse dari JSON route server, tanpa kontrak domain. */
internal data class BuilderOverviewUi(
    val slug: String,
    val name: String,
    val status: String,
    val tier: String,
    val domainPack: String,
    val domainPackVersion: Int?,
    val activeStatus: String?,
    val activeNumber: Int?,
    val activeAppBuild: String?,
    val activeRevision: Int?,
    val activeCreatedAt: String?,
    val deployments: List<BuilderDeploymentItemUi>
)

internal fun parseOverview(root: JsonValue?): BuilderOverviewUi? {
    val obj = root as? JsonValue.Obj ?: return null
    val tenant = obj.obj("tenant") ?: return null
    val active = obj.obj("activeDeployment")
    val rawDeployments = (obj.get("deployments") as? JsonValue.Arr)?.items
        ?.filterIsInstance<JsonValue.Obj>() ?: emptyList()

    val deployments = rawDeployments.map { d ->
        BuilderDeploymentItemUi(
            number = (d.get("number") as? JsonValue.Num)?.asInt ?: 1,
            status = d.string("status") ?: "LIVE",
            packCode = d.string("packCode") ?: tenant.string("domainPack") ?: "garment",
            packVersion = (d.get("packVersion") as? JsonValue.Num)?.asInt,
            appBuild = d.string("appBuild"),
            blueprintRevision = (d.get("blueprintRevision") as? JsonValue.Num)?.asInt ?: 1,
            createdAt = d.string("createdAt")
        )
    }

    return BuilderOverviewUi(
        slug = tenant.string("slug") ?: "-",
        name = tenant.string("name") ?: "-",
        status = tenant.string("status") ?: "ACTIVE",
        tier = tenant.string("tier") ?: "ENTERPRISE",
        domainPack = tenant.string("domainPack") ?: "garment",
        domainPackVersion = (tenant.entries["domainPackVersion"] as? JsonValue.Num)?.asInt,
        activeStatus = active?.string("status"),
        activeNumber = (active?.get("number") as? JsonValue.Num)?.asInt,
        activeAppBuild = active?.string("appBuild"),
        activeRevision = (active?.get("blueprintRevision") as? JsonValue.Num)?.asInt,
        activeCreatedAt = active?.string("createdAt"),
        deployments = deployments
    )
}

/** Warna sinyal status — token theme, bukan literal (Kontrak 1 design-system-rules). */
internal fun statusTint(status: String): Color = when (status.uppercase()) {
    "ACTIVE", "IMPORTED", "LIVE", "READY" -> WeMadeColors.Success
    "TRIAL", "PENDING" -> WeMadeColors.Warning
    else -> WeMadeColors.OnSurfaceMuted
}

/**
 * Overview Builder ala Vercel Dashboard:
 * Menampilkan identitas project, status production live, grid metrik telemetri,
 * hub eksplorasi alur instan, dan log deployment terkini.
 */
@Composable
fun BuilderOverviewPane(
    modifier: Modifier = Modifier,
    onNavigate: (String) -> Unit = {}
) {
    val client = remember { BuilderApiClient() }
    var overview by remember { mutableStateOf<BuilderOverviewUi?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        client.overview()
            .onSuccess { overview = parseOverview(it); error = null }
            .onFailure { error = it.message }
        loaded = true
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ClaySpacing.Xl)
    ) {
        when {
            !loaded -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(ClaySpacing.Xxl),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Memuat data project...", color = WeMadeColors.OnSurfaceMuted)
                }
            }
            error != null -> {
                ClayCard {
                    Text("Gagal memuat overview: $error", color = WeMadeColors.Warning)
                    Spacer(Modifier.height(ClaySpacing.Sm))
                    Text(
                        "Pastikan sesi login masih aktif dan membawa izin MANAGE_BUILDER.",
                        color = WeMadeColors.OnSurfaceMuted
                    )
                }
            }
            overview == null -> {
                Text("Tidak ada data project.", color = WeMadeColors.OnSurfaceMuted)
            }
            else -> {
                val ui = overview ?: return
                // 1. Header Banner Identitas Project ala Vercel
                ProjectHeroHeader(ui = ui, onNavigate = onNavigate)

                // 2. Production Deployment Hero Showcase Card
                BuilderProductionDeploymentHero(ui = ui, onNavigate = onNavigate)

                // 3. Grid 4 Metrik & Telemetry Alur
                BuilderMetricsGrid(ui = ui)

                // 4. Hub Eksplorasi & Modifikasi Alur Kerja
                BuilderQuickActionHub(onNavigate = onNavigate)

                // 5. Riwayat Aktivitas Deployment
                BuilderRecentDeploymentsCard(
                    deployments = ui.deployments,
                    onViewAll = { onNavigate("deployments") }
                )
            }
        }
    }
}

/** Header Project Banner Vercel-style: Title, Breadcrumb, URL, dan Quick Action buttons. */
@Composable
private fun ProjectHeroHeader(
    ui: BuilderOverviewUi,
    onNavigate: (String) -> Unit
) {
    val typography = rememberClayTypography()

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
            ) {
                Text(
                    text = ui.slug,
                    style = typography.bodySmall,
                    fontSize = 11.sp,
                    color = WeMadeColors.OnSurfaceMuted
                )
                Text(
                    text = "/",
                    style = typography.bodySmall,
                    fontSize = 11.sp,
                    color = WeMadeColors.OnSurfaceDisabled
                )
                Text(
                    text = "Overview",
                    style = typography.bodySmall,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = WeMadeColors.Primary
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
            ) {
                Text(
                    text = ui.name,
                    style = typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = WeMadeColors.OnSurface
                )
                ClayBadge(
                    text = ui.status,
                    tint = statusTint(ui.status),
                    dot = true
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
            ) {
                IconGlobe(Modifier.size(12.dp), color = WeMadeColors.OnSurfaceMuted)
                Text(
                    text = "${ui.slug}.wemakeerp.com",
                    style = typography.bodySmall,
                    color = WeMadeColors.OnSurfaceMuted
                )
                Box(
                    modifier = Modifier
                        .background(WeMadeColors.SurfaceMuted, ClayShapes.Pill)
                        .border(ClayBorder.Hairline, WeMadeColors.Border, ClayShapes.Pill)
                        .padding(horizontal = 6.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = "Production",
                        style = typography.bodySmall,
                        fontSize = 10.sp,
                        color = WeMadeColors.OnSurfaceMuted
                    )
                }
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ClayButton(
                text = "Buka Prototype",
                onClick = { onNavigate("prototype") },
                style = ClayButtonStyle.Secondary,
                leading = { IconActivity(Modifier.size(14.dp), color = WeMadeColors.OnSurface) }
            )
            ClayButton(
                text = "Rancang Alur (AI)",
                onClick = { onNavigate("chat") },
                style = ClayButtonStyle.Primary,
                leading = { IconZap(Modifier.size(14.dp), color = Color.White) }
            )
        }
    }
}

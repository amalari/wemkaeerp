package com.eventverse.app.presentation.builder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.eventverse.app.infrastructure.api.BuilderApiClient
import com.eventverse.app.presentation.designsystem.ClayBadge
import com.eventverse.app.presentation.designsystem.ClayCard
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.rememberClayTypography
import com.eventverse.app.presentation.theme.WeMadeColors
import com.eventverse.app.shared.json.JsonValue

/** Model tampilan Overview; diparse dari JSON route server, tanpa kontrak domain. */
internal data class BuilderOverviewUi(
    val slug: String,
    val name: String,
    val status: String,
    val tier: String,
    val domainPack: String,
    val domainPackVersion: Int?,
    val activeStatus: String?
)

internal fun parseOverview(root: JsonValue?): BuilderOverviewUi? {
    val obj = root as? JsonValue.Obj ?: return null
    val tenant = obj.obj("tenant") ?: return null
    val active = obj.obj("activeDeployment")
    return BuilderOverviewUi(
        slug = tenant.string("slug") ?: "-",
        name = tenant.string("name") ?: "-",
        status = tenant.string("status") ?: "UNKNOWN",
        tier = tenant.string("tier") ?: "-",
        domainPack = tenant.string("domainPack") ?: "-",
        domainPackVersion = (tenant.entries["domainPackVersion"] as? JsonValue.Num)?.raw?.toIntOrNull(),
        activeStatus = active?.string("status")
    )
}

/** Warna sinyal status — token theme, bukan literal (Kontrak 1 design-system-rules). */
internal fun statusTint(status: String): androidx.compose.ui.graphics.Color = when (status.uppercase()) {
    "ACTIVE", "IMPORTED", "LIVE" -> WeMadeColors.Success
    "TRIAL" -> WeMadeColors.Warning
    else -> WeMadeColors.OnSurfaceMuted
}

/**
 * Overview Builder (M0): identitas project, status tenant, deployment aktif (`Deployment #1 IMPORTED`
 * dari migrasi V81), dan tombol Deploy yang sengaja dinonaktifkan sampai M2.
 */
@Composable
fun BuilderOverviewPane(modifier: Modifier = Modifier) {
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

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ClaySpacing.Lg)) {
        Text(
            text = "Overview",
            style = rememberClayTypography().titleLarge,
            fontWeight = FontWeight.Bold,
            color = WeMadeColors.OnSurface
        )
        when {
            !loaded -> Text("Memuat…", color = WeMadeColors.OnSurfaceMuted)
            error != null -> ClayCard {
                Text("Gagal memuat: $error", color = WeMadeColors.Warning)
                Text(
                    "Pastikan Anda login sebagai pemilik project (MANAGE_BUILDER).",
                    color = WeMadeColors.OnSurfaceMuted
                )
            }
            overview == null -> Text("Tidak ada data.", color = WeMadeColors.OnSurfaceMuted)
            else -> {
                val ui = overview!!
                ClayCard {
                    Row(horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Md)) {
                        Column(Modifier.weight(1f, fill = false)) {
                            Text(ui.name, fontWeight = FontWeight.Bold, color = WeMadeColors.OnSurface)
                            Text(
                                "${ui.slug}.wemakeerp.com",
                                color = WeMadeColors.OnSurfaceMuted,
                                maxLines = 1
                            )
                        }
                        ClayBadge(
                            text = ui.status,
                            tint = statusTint(ui.status),
                            dot = true
                        )
                    }
                }
                ClayCard {
                    Text("Deployment", fontWeight = FontWeight.Bold, color = WeMadeColors.OnSurface)
                    val active = ui.activeStatus
                    if (active == null) {
                        Text("Belum di-deploy.", color = WeMadeColors.OnSurfaceMuted)
                    } else {
                        Text(
                            "Deployment aktif: $active",
                            color = WeMadeColors.OnSurface
                        )
                        Text(
                            "Pack: ${ui.domainPack}" + (ui.domainPackVersion?.let { " · versi $it" } ?: " · versi bawaan platform"),
                            color = WeMadeColors.OnSurfaceMuted
                        )
                    }
                }
            }
        }
    }
}

package com.eventverse.app.presentation.builder.setting

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.eventverse.app.infrastructure.api.BuilderApiClient
import com.eventverse.app.presentation.designsystem.ClayBadge
import com.eventverse.app.presentation.designsystem.ClayCard
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.rememberClayTypography
import com.eventverse.app.presentation.theme.WeMadeColors
import com.eventverse.app.shared.json.JsonValue

/**
 * Satu baris bacaan Pack & Tema: pack aktif + versinya + versi ter-pin deployment aktif —
 * semuanya **read-only** (FR-6-2): mengganti pack tetap lewat chat/deploy, bukan dari sini.
 */
internal data class BuilderPackTemaUi(
    val packCode: String,
    val packVersion: Int?,
    val pinnedPackVersion: Int?,
)

/**
 * Parse dari payload `GET /api/builder/overview` yang sudah ada — tanpa endpoint baru.
 * Field hilang → `null` → tampil "—", tidak dipalsukan jadi angka.
 */
internal fun parsePackTema(raw: JsonValue?): BuilderPackTemaUi? {
    val obj = raw as? JsonValue.Obj ?: return null
    val tenant = obj.obj("tenant") ?: return null
    val active = obj.obj("activeDeployment")
    return BuilderPackTemaUi(
        packCode = tenant.string("domainPack") ?: "-",
        packVersion = tenant.int("domainPackVersion"),
        pinnedPackVersion = active?.int("packVersion"),
    )
}

/**
 * Tab Pack & Tema (FR-6-2): pack aktif + versi ter-pin (read-only), dan status jembatan ke
 * kustomisasi tema (TRD-PLAT-013) — yang **belum terpasang** dinyatakan jujur "belum tersedia",
 * bukan disembunyikan atau direkayasa tampilannya.
 */
@Composable
internal fun BuilderPackThemeTab(modifier: Modifier = Modifier) {
    val client = remember { BuilderApiClient() }
    val typography = rememberClayTypography()
    var pack by remember { mutableStateOf<BuilderPackTemaUi?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        client.overview()
            .onSuccess { pack = parsePackTema(it); error = null }
            .onFailure { e -> error = e.message }
    }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)) {
        ClayCard {
            Text("Pack Aktif", fontWeight = FontWeight.Bold, color = WeMadeColors.OnSurface)
            if (error != null) {
                Text(
                    text = "Gagal memuat pack aktif: $error",
                    style = typography.bodySmall,
                    color = WeMadeColors.Error,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            } else if (pack == null) {
                Text("Memuat data pack...", style = typography.bodySmall, color = WeMadeColors.OnSurfaceMuted)
            } else {
                val p = pack ?: return@ClayCard
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = p.packCode,
                        style = typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = WeMadeColors.OnSurface,
                        modifier = Modifier.weight(1f, fill = false),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    p.packVersion?.let { ClayBadge(text = "versi $it", tint = WeMadeColors.Info) }
                    ClayBadge(
                        text = p.pinnedPackVersion?.let { "ter-pin v$it" } ?: "belum ada deployment",
                        tint = if (p.pinnedPackVersion != null) WeMadeColors.Success else WeMadeColors.OnSurfaceMuted
                    )
                }
                Text(
                    text = "Mengganti pack tetap lewat Chat atau Deploy; tab ini hanya bacaan.",
                    style = typography.bodySmall,
                    color = WeMadeColors.OnSurfaceMuted
                )
            }
        }

        // Jembatan tema (TRD-PLAT-013) — fitur belum terpasang: status jujur, tanpa kontrol mati.
        ClayCard {
            Text("Tema", fontWeight = FontWeight.Bold, color = WeMadeColors.OnSurface)
            Text(
                "Kustomisasi tema visual per tenant (TRD-PLAT-013) belum tersedia di platform. " +
                        "Bila sudah terpasang, pengaturannya muncul di tab ini.",
                style = typography.bodySmall,
                color = WeMadeColors.OnSurfaceMuted
            )
            ClayBadge(text = "Belum tersedia", tint = WeMadeColors.OnSurfaceMuted)
        }
    }
}

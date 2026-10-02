package com.eventverse.app.presentation.platform

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.eventverse.app.infrastructure.api.AdminApiClient
import com.eventverse.app.infrastructure.api.PlatformTenantRow
import com.eventverse.app.presentation.builder.statusTint
import com.eventverse.app.presentation.designsystem.ClayBadge
import com.eventverse.app.presentation.designsystem.ClayButton
import com.eventverse.app.presentation.designsystem.ClayButtonStyle
import com.eventverse.app.presentation.designsystem.ClayCard
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.ClayTextField
import com.eventverse.app.presentation.designsystem.rememberClayTypography
import com.eventverse.app.presentation.tenant.TenantModuleEntitlementDialog
import com.eventverse.app.presentation.theme.WeMadeColors
import kotlinx.coroutines.launch

/**
 * Daftar seluruh tenant (discovery-M3b). Per baris: masuk ke **Builder** atau **aplikasi hasil**
 * tenant itu (act-as, ber-audit di server), dan atur modul yang disambungkan.
 *
 * Tombol hanya kenyamanan; penjagaannya di server (`/api/admin` + `/handoff/issue?actAs`).
 */
@Composable
internal fun PlatformTenantsPane(
    onActAs: suspend (slug: String, landingPath: String) -> Result<Unit>,
    modifier: Modifier = Modifier
) {
    val client = remember { AdminApiClient() }
    val scope = rememberCoroutineScope()
    var tenants by remember { mutableStateOf<List<PlatformTenantRow>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var busySlug by remember { mutableStateOf<String?>(null) }
    var entitlementSlug by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var visibleCount by remember(query) { mutableStateOf(PAGE_SIZE) }

    LaunchedEffect(Unit) {
        client.listTenants().onSuccess { tenants = it; error = null }.onFailure { error = it.message }
    }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ClaySpacing.Lg)) {
        Text("Tenants", style = rememberClayTypography().titleLarge, fontWeight = FontWeight.Bold, color = WeMadeColors.OnSurface)
        Text(
            "Setiap kali Anda masuk ke workspace tenant, aksinya tercatat di audit log tenant tersebut.",
            style = rememberClayTypography().bodySmall,
            color = WeMadeColors.OnSurfaceMuted
        )
        error?.let { Text(it, color = WeMadeColors.Error) }
        ClayTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            label = "Cari tenant",
            placeholder = "slug atau nama, mis. bordir",
            focusColor = WeMadeColors.Primary
        )

        // Konsol bisa memuat ratusan tenant; menyusun semua kartu clay (3 tombol + bayangan) sekaligus membekukan
        // thread UI di Wasm. Tampilkan per halaman — pencarian tetap menyaring seluruh daftar.
        val matches = tenants.filter { query.isBlank() || it.slug.contains(query.trim(), true) || it.name.contains(query.trim(), true) }
        matches.take(visibleCount).forEach { tenant ->
            ClayCard(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f, fill = false)) {
                        Text(tenant.name, fontWeight = FontWeight.Bold, color = WeMadeColors.OnSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${tenant.slug} · ${tenant.domainPack} · ${tenant.tier}",
                            style = rememberClayTypography().bodySmall,
                            color = WeMadeColors.OnSurfaceMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.width(ClaySpacing.Md))
                    ClayBadge(text = tenant.status, tint = statusTint(tenant.status), dot = true, fontSize = 10.sp)
                }
                Spacer(Modifier.height(ClaySpacing.Md))
                Row(horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
                    val busy = busySlug != null
                    fun actAs(path: String) = scope.launch {
                        busySlug = tenant.slug
                        onActAs(tenant.slug, path).onFailure { error = it.message }
                        busySlug = null
                    }
                    ClayButton(text = "Masuk Builder", onClick = { actAs("/builder") }, enabled = !busy, fontSize = 12.sp)
                    ClayButton(text = "Masuk Aplikasi", onClick = { actAs("/login") }, enabled = !busy, style = ClayButtonStyle.Secondary, fontSize = 12.sp)
                    ClayButton(text = "Modul", onClick = { entitlementSlug = tenant.slug }, enabled = !busy, style = ClayButtonStyle.Ghost, fontSize = 12.sp)
                }
            }
        }
        if (matches.size > visibleCount) {
            ClayButton(
                text = "Tampilkan lebih banyak (${visibleCount} dari ${matches.size})",
                onClick = { visibleCount += PAGE_SIZE },
                style = ClayButtonStyle.Ghost,
                fontSize = 12.sp
            )
        }
    }

    entitlementSlug?.let { slug ->
        TenantModuleEntitlementDialog(tenantSlug = slug, onDismiss = { entitlementSlug = null }, onSaved = { entitlementSlug = null })
    }
}

private const val PAGE_SIZE = 20

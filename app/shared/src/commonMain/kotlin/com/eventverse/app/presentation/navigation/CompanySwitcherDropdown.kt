package com.eventverse.app.presentation.navigation

import com.eventverse.app.domain.blueprint.Blueprint

import com.eventverse.app.domain.pack.GarmentBlueprints

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.eventverse.app.infrastructure.api.AdminApiClient
import com.eventverse.app.infrastructure.api.PlatformTenantRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import com.eventverse.app.presentation.designsystem.ClayBorder
import com.eventverse.app.presentation.designsystem.IconCheck
import com.eventverse.app.presentation.designsystem.ClayShapes
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.ClayTextField
import com.eventverse.app.presentation.designsystem.IconChevronDown
import com.eventverse.app.presentation.pipeline.components.IconBuilding
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Metadata for standard multi-tenant companies available to Superadmin.
 */
data class CompanyTenantProfile(
    val id: String,
    val slug: String,
    val name: String,
    val preset: Blueprint,
    val iconColor: Color,
    val subtitle: String
) {
    companion object {
        val ALL = listOf(
            CompanyTenantProfile(
                id = "ten-demo-001",
                slug = "wemade-demo",
                name = "PT WeMade Garmen Ekspor",
                preset = GarmentBlueprints.FOB_FULL_PACKAGE,
                iconColor = Color(0xFF2563EB),
                subtitle = "Pabrik Utama · 12 Line Produksi"
            ),
            CompanyTenantProfile(
                id = "ten-demo-cmt",
                slug = "cv-berkah-makloon",
                name = "CV Berkah Makloon Jahit",
                preset = GarmentBlueprints.CMT_MAKLOON,
                iconColor = Color(0xFFD97706),
                subtitle = "Unit Makloon Jahit · 8 Line Produksi"
            ),
            CompanyTenantProfile(
                id = "ten-demo-d2c",
                slug = "urbanwear-d2c",
                name = "UrbanWear Studio Apparel",
                preset = GarmentBlueprints.BRAND_D2C,
                iconColor = Color(0xFF059669),
                subtitle = "Workshop Studio · 4 Line Produksi"
            )
        )

        /** Null bila slug bukan profil demo; tidak jatuh ke profil pertama. */
        fun findBySlug(slug: String?): CompanyTenantProfile? =
            ALL.firstOrNull { it.slug.equals(slug, ignoreCase = true) }

        fun findByPreset(preset: Blueprint): CompanyTenantProfile? = ALL.firstOrNull { it.preset == preset }
    }
}

/**
 * Pemilih tenant aktif untuk superadmin (gaya GCP project switcher).
 *
 * Daftarnya **tenant sungguhan** dari `GET /api/admin/tenants` (bukan tiga perusahaan demo), dan yang tampil
 * di tombol adalah tenant pada sesi saat ini ([currentSlug]). Daftar bisa ratusan baris, jadi ia dicari lewat
 * kolom pencarian dan dirender **lazy** (`LazyColumn`): tak ada batas jumlah, tapi hanya baris yang terlihat yang
 * disusun — menyusun ratusan baris sekaligus membekukan UI.
 */
@Composable
fun CompanySwitcherDropdown(
    currentSlug: String,
    onSelectTenant: (slug: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val client = remember { AdminApiClient() }
    var expanded by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var tenants by remember { mutableStateOf<List<PlatformTenantRow>>(emptyList()) }
    var loadError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(currentSlug) {
        client.listTenants().onSuccess { tenants = it; loadError = null }.onFailure { loadError = it.message }
    }
    val active = tenants.firstOrNull { it.slug.equals(currentSlug, ignoreCase = true) }
    val matches = remember(tenants, query) {
        val q = query.trim()
        tenants.filter { q.isBlank() || it.slug.contains(q, true) || it.name.contains(q, true) }
    }

    Box(modifier = modifier) {
        Surface(
            modifier = Modifier.clip(ClayShapes.Chip).clickable { query = ""; expanded = true },
            shape = ClayShapes.Chip,
            color = WeMadeColors.Surface,
            border = BorderStroke(ClayBorder.Medium, WeMadeColors.Outline)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = ClaySpacing.Md, vertical = ClaySpacing.Sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
            ) {
                IconBuilding(modifier = Modifier.size(16.dp), color = WeMadeColors.Primary)
                Column(modifier = Modifier.widthIn(max = 220.dp)) {
                    Text(
                        text = active?.name ?: currentSlug,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = WeMadeColors.OnSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(text = "Tenant: $currentSlug", fontSize = 10.sp, color = WeMadeColors.OnSurfaceMuted, maxLines = 1)
                }
                IconChevronDown(modifier = Modifier.size(10.dp), color = WeMadeColors.OnSurfaceMuted)
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.width(MENU_WIDTH).background(WeMadeColors.Surface)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = ClaySpacing.Md, vertical = ClaySpacing.Sm)) {
                Text("GANTI TENANT (SUPERADMIN)", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = WeMadeColors.OnSurfaceMuted)
                Spacer(Modifier.height(ClaySpacing.Sm))
                ClayTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = "Cari slug atau nama tenant",
                    focusColor = WeMadeColors.Primary
                )
                loadError?.let { Text(it, fontSize = 11.sp, color = WeMadeColors.Error) }
            }
            HorizontalDivider(color = WeMadeColors.Border)

            if (matches.isEmpty() && loadError == null) {
                Text("Tidak ada tenant yang cocok.", fontSize = 11.sp, color = WeMadeColors.OnSurfaceMuted, modifier = Modifier.padding(ClaySpacing.Md))
            }
            // Ukuran TETAP (bukan fillMaxWidth): DropdownMenu mengukur isinya dengan IntrinsicSize.Max dan LazyColumn
            // melempar IllegalStateException bila ditanya intrinsik — modifier ukuran menjawabnya lebih dulu.
            LazyColumn(modifier = Modifier.size(MENU_WIDTH, LIST_HEIGHT)) {
                items(matches, key = { it.slug }) { tenant ->
                    val isSelected = tenant.slug.equals(currentSlug, ignoreCase = true)
                    DropdownMenuItem(
                        text = {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f, fill = false)) {
                                    Text(
                                        tenant.name, fontSize = 12.sp, color = WeMadeColors.OnSurface, maxLines = 1,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        "${tenant.slug} · ${tenant.domainPack} · ${tenant.status}",
                                        fontSize = 10.sp, color = WeMadeColors.OnSurfaceMuted, maxLines = 1, overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (isSelected) {
                                    Spacer(Modifier.width(ClaySpacing.Sm))
                                    // Glyph ✓ tidak ada di Nunito (tampil tofu di web) — pakai ikon vektor.
                                    IconCheck(modifier = Modifier.size(14.dp), color = WeMadeColors.Primary)
                                }
                            }
                        },
                        onClick = {
                            expanded = false
                            if (!isSelected) onSelectTenant(tenant.slug)
                        },
                        modifier = Modifier.background(if (isSelected) WeMadeColors.PrimaryContainer.copy(alpha = 0.4f) else Color.Transparent)
                    )
                }
            }
        }
    }
}

private val MENU_WIDTH = 340.dp
private val LIST_HEIGHT = 320.dp

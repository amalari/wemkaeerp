package com.eventverse.app.presentation.navigation

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
import androidx.compose.ui.unit.sp
import com.eventverse.app.domain.pipeline.GarmentBusinessPreset
import com.eventverse.app.presentation.pipeline.components.IconBuilding
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Metadata for standard multi-tenant companies available to Superadmin.
 */
data class CompanyTenantProfile(
    val id: String,
    val slug: String,
    val name: String,
    val preset: GarmentBusinessPreset,
    val iconColor: Color,
    val subtitle: String
) {
    companion object {
        val ALL = listOf(
            CompanyTenantProfile(
                id = "ten-demo-001",
                slug = "wemade-demo",
                name = "PT WeMade Garmen Ekspor",
                preset = GarmentBusinessPreset.FOB_FULL_PACKAGE,
                iconColor = Color(0xFF2563EB),
                subtitle = "Pabrik Utama • 12 Line Produksi"
            ),
            CompanyTenantProfile(
                id = "ten-demo-cmt",
                slug = "cv-berkah-makloon",
                name = "CV Berkah Makloon Jahit",
                preset = GarmentBusinessPreset.CMT_MAKLOON,
                iconColor = Color(0xFFD97706),
                subtitle = "Unit Makloon Jahit • 8 Line Produksi"
            ),
            CompanyTenantProfile(
                id = "ten-demo-d2c",
                slug = "urbanwear-d2c",
                name = "UrbanWear Studio Apparel",
                preset = GarmentBusinessPreset.BRAND_D2C,
                iconColor = Color(0xFF059669),
                subtitle = "Workshop Studio • 4 Line Produksi"
            )
        )

        fun findBySlug(slug: String?): CompanyTenantProfile {
            return ALL.firstOrNull { it.slug.equals(slug, ignoreCase = true) } ?: ALL.first()
        }

        fun findByPreset(preset: GarmentBusinessPreset): CompanyTenantProfile {
            return ALL.firstOrNull { it.preset == preset } ?: ALL.first()
        }
    }
}

/**
 * GCP-Style Project / Company Switcher Dropdown for Superadmin.
 * Allows instant switching of active enterprise tenant context across the entire ERP platform.
 */
@Composable
fun CompanySwitcherDropdown(
    currentSlug: String,
    onSelectCompany: (CompanyTenantProfile) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val activeCompany = remember(currentSlug) { CompanyTenantProfile.findBySlug(currentSlug) }

    Box(modifier = modifier) {
        // GCP-Style Pill Button
        Surface(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { expanded = true },
            shape = RoundedCornerShape(8.dp),
            color = WeMadeColors.Surface,
            border = BorderStroke(1.dp, WeMadeColors.Border),
            shadowElevation = 0.5.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Factory Icon
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(activeCompany.iconColor.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    IconBuilding(
                        modifier = Modifier.size(13.dp),
                        color = activeCompany.iconColor
                    )
                }

                // Company Name + Tenant Slug
                Column {
                    Text(
                        text = activeCompany.name,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = WeMadeColors.OnSurface
                    )
                    Text(
                        text = "Tenant: ${activeCompany.slug}",
                        fontSize = 10.sp,
                        color = WeMadeColors.OnSurfaceMuted
                    )
                }

                Spacer(modifier = Modifier.width(2.dp))

                // Small Chevron Down
                Text(
                    text = "▼",
                    fontSize = 8.sp,
                    color = WeMadeColors.OnSurfaceMuted
                )
            }
        }

        // Dropdown Menu Listing Companies
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .width(320.dp)
                .background(WeMadeColors.Surface)
        ) {
            // Dropdown Header
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "GANTI PERUSAHAAN (SUPERADMIN)",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = WeMadeColors.OnSurfaceMuted,
                    letterSpacing = 0.5.sp
                )
                Text(
                    text = "Pilih perusahaan untuk memuat alur kerja operasionalnya:",
                    fontSize = 11.sp,
                    color = WeMadeColors.OnSurfaceMuted
                )
            }

            HorizontalDivider(color = WeMadeColors.Border)

            // Items for Each Company
            CompanyTenantProfile.ALL.forEach { company ->
                val isSelected = company.slug == activeCompany.slug

                DropdownMenuItem(
                    text = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(company.iconColor.copy(alpha = 0.12f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    IconBuilding(
                                        modifier = Modifier.size(15.dp),
                                        color = company.iconColor
                                    )
                                }

                                Column {
                                    Text(
                                        text = company.name,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                        color = WeMadeColors.OnSurface
                                    )
                                    Text(
                                        text = company.subtitle,
                                        fontSize = 10.sp,
                                        color = WeMadeColors.OnSurfaceMuted
                                    )
                                }
                            }

                            if (isSelected) {
                                Text(
                                    text = "✓",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = WeMadeColors.Primary
                                )
                            }
                        }
                    },
                    onClick = {
                        expanded = false
                        onSelectCompany(company)
                    },
                    modifier = Modifier.background(
                        if (isSelected) WeMadeColors.PrimaryContainer.copy(alpha = 0.4f) else Color.Transparent
                    )
                )
            }
        }
    }
}

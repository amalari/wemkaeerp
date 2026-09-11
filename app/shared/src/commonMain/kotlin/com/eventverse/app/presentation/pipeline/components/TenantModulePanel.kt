package com.eventverse.app.presentation.pipeline.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.domain.pipeline.GarmentBusinessPreset
import com.eventverse.app.presentation.designsystem.ClayBorder
import com.eventverse.app.presentation.designsystem.ClayButton
import com.eventverse.app.presentation.designsystem.ClayButtonStyle
import com.eventverse.app.presentation.designsystem.ClayCard
import com.eventverse.app.presentation.designsystem.ClayOffset
import com.eventverse.app.presentation.designsystem.ClayShapes
import com.eventverse.app.presentation.designsystem.ClayTag
import com.eventverse.app.presentation.designsystem.clayFlat
import com.eventverse.app.domain.pipeline.TenantModuleAvailability
import com.eventverse.app.domain.pipeline.TenantModuleCatalogSnapshot
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Per-tenant module provisioning panel.
 *
 * Every switch here writes to `tenant_pipelines.graph_data` for this tenant alone, so two
 * factories on the same deployment can run entirely different module sets. Modules the
 * subscription plan does not grant are shown but disabled, rather than hidden, so it is
 * clear what an upgrade would unlock.
 */
@Composable
fun TenantModulePanel(
    catalog: TenantModuleCatalogSnapshot,
    isSaving: Boolean,
    activePreset: GarmentBusinessPreset,
    onSetModuleActive: (moduleId: String, isActive: Boolean) -> Unit,
    onResetToPreset: (GarmentBusinessPreset) -> Unit,
    modifier: Modifier = Modifier
) {
    ClayCard(
        modifier = modifier.fillMaxWidth(),
        shape = ClayShapes.Panel,
        contentPadding = PaddingValues(16.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PanelHeader(catalog = catalog)

            if (catalog.modules.isEmpty()) {
                Text(
                    text = "Katalog modul belum tersedia. Panel ini aktif setelah server " +
                        "mengirim daftar modul untuk tenant ini.",
                    fontSize = 12.sp,
                    color = WeMadeColors.OnSurfaceMuted
                )
            } else {
                Column(
                    modifier = Modifier
                        .heightIn(max = 280.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    catalog.modules.forEach { module ->
                        ModuleRow(
                            module = module,
                            isSaving = isSaving,
                            // A module can always be switched off; switching one on needs a
                            // plan grant and a free slot within the plan limit.
                            canActivate = module.isGrantedByPlan &&
                                (module.isActive || !catalog.hasReachedPlanLimit),
                            onSetActive = { isActive -> onSetModuleActive(module.moduleId, isActive) }
                        )
                    }
                }
            }

            HorizontalDivider(color = WeMadeColors.Border)

            PresetResetRow(
                activePreset = activePreset,
                isSaving = isSaving,
                onResetToPreset = onResetToPreset
            )
        }
    }
}

@Composable
private fun PanelHeader(catalog: TenantModuleCatalogSnapshot) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "Modul Operasional Tenant Ini",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = WeMadeColors.OnSurface
            )
            Text(
                text = "Nonaktifkan modul yang tidak dipakai pabrik ini tanpa memutus alur berikutnya.",
                fontSize = 11.sp,
                color = WeMadeColors.OnSurfaceMuted
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Badge(
                text = "Paket ${catalog.tier.name}",
                color = WeMadeColors.Primary
            )
            Badge(
                text = if (catalog.maxActiveModules == Int.MAX_VALUE) {
                    "${catalog.activeModuleCount} modul aktif · tanpa batas"
                } else {
                    "${catalog.activeModuleCount}/${catalog.maxActiveModules} modul aktif"
                },
                color = if (catalog.hasReachedPlanLimit) WeMadeColors.Error else WeMadeColors.Success
            )
        }
    }
}

@Composable
private fun ModuleRow(
    module: TenantModuleAvailability,
    isSaving: Boolean,
    canActivate: Boolean,
    onSetActive: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clayFlat(
                shape = ClayShapes.Chip,
                background = if (module.isActive) WeMadeColors.Success.copy(alpha = 0.07f)
                else WeMadeColors.Background,
                // Baris aktif diberi outline hijau, yang nonaktif outline netral pucat — status
                // terbaca saat memindai kolom dari atas ke bawah, bukan cuma dari posisi switch.
                outline = if (module.isActive) WeMadeColors.Success.copy(alpha = 0.45f)
                else WeMadeColors.Border,
                borderWidth = ClayBorder.Medium
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = module.tenantDisplayName ?: module.displayName,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = WeMadeColors.OnSurface
                )
                if (module.isCustomPlugin) {
                    Badge(text = "Plugin Kustom", color = WeMadeColors.Primary)
                }
                if (module.requiresPlanUpgrade) {
                    Badge(text = "Perlu Upgrade", color = WeMadeColors.Error)
                }
            }

            // Show the catalogue name too when the tenant renamed the module, so an
            // operator can still tell which built-in module a custom label refers to.
            val subtitle = buildString {
                append(module.archetype.displayName)
                if (module.tenantDisplayName != null && !module.isCustomPlugin) {
                    append(" · modul bawaan: ${module.displayName}")
                }
            }
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = WeMadeColors.OnSurfaceMuted
            )
        }

        Switch(
            checked = module.isActive,
            enabled = !isSaving && (module.isActive || canActivate),
            onCheckedChange = onSetActive
        )
    }
}

@Composable
private fun PresetResetRow(
    activePreset: GarmentBusinessPreset,
    isSaving: Boolean,
    onResetToPreset: (GarmentBusinessPreset) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "Pulihkan ke Preset Model Bisnis",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = WeMadeColors.OnSurface
        )
        Text(
            text = "Menimpa seluruh kustomisasi modul tenant ini dengan susunan standar preset terpilih.",
            fontSize = 11.sp,
            color = WeMadeColors.OnSurfaceMuted
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GarmentBusinessPreset.entries.forEach { preset ->
                ClayButton(
                    text = preset.shortBadge,
                    onClick = { onResetToPreset(preset) },
                    enabled = !isSaving,
                    style = if (preset == activePreset) ClayButtonStyle.Primary else ClayButtonStyle.Secondary,
                    fontSize = 11.sp,
                    offset = ClayOffset.Pressed,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}

/** Kini tinggal membungkus [ClayTag] — salah satu dari tiga badge duplikat yang disatukan. */
@Composable
private fun Badge(
    text: String,
    color: androidx.compose.ui.graphics.Color
) {
    ClayTag(text = text, tint = color, fontSize = 11.sp)
}

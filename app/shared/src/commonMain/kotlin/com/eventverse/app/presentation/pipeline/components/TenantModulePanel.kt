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
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = WeMadeColors.Surface),
        border = BorderStroke(1.dp, WeMadeColors.Border)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
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
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (module.isActive) WeMadeColors.Success.copy(alpha = 0.06f)
                else WeMadeColors.Background
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
                fontSize = 10.sp,
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
            fontSize = 10.sp,
            color = WeMadeColors.OnSurfaceMuted
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GarmentBusinessPreset.entries.forEach { preset ->
                OutlinedButton(
                    onClick = { onResetToPreset(preset) },
                    enabled = !isSaving,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = if (preset == activePreset) {
                            WeMadeColors.Primary
                        } else {
                            WeMadeColors.OnSurfaceMuted
                        }
                    ),
                    border = BorderStroke(
                        1.dp,
                        if (preset == activePreset) WeMadeColors.Primary else WeMadeColors.Border
                    )
                ) {
                    Text(
                        text = preset.shortBadge,
                        fontSize = 11.sp,
                        fontWeight = if (preset == activePreset) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun Badge(
    text: String,
    color: androidx.compose.ui.graphics.Color
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = color
        )
    }
}

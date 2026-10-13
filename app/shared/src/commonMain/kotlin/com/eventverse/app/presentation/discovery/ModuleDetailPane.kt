package com.eventverse.app.presentation.discovery

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.presentation.designsystem.ClayBadge
import com.eventverse.app.presentation.designsystem.ClayBorder
import com.eventverse.app.presentation.designsystem.ClayCard
import com.eventverse.app.presentation.designsystem.ClayOffset
import com.eventverse.app.presentation.designsystem.ClayShapes
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.claySurface
import com.eventverse.app.presentation.designsystem.rememberClayTypography
import com.eventverse.app.presentation.theme.WeMadeColors
import org.jetbrains.compose.resources.painterResource

/**
 * Tampilan Spesifikasi & Fitur Modul (Tab 'Fitur & Spek'):
 * Menyajikan rincian lengkap kapabilitas, kontrak port data, dan aturan alur modul
 * tanpa memakan ruang tab Prototype yang kini berdiri selayar penuh.
 */
@Composable
internal fun ModuleDetailPane(
    draft: DiscoveryDraftUi,
    focusModuleId: String?,
    onSelectModule: ((String?) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val typography = rememberClayTypography()
    val focusedModule = focusModuleId?.let { id -> draft.modules.firstOrNull { it.id == id } }

    if (focusedModule != null) {
        // Tampilan Spesifikasi Modul Terpilih
        val sectionStyle = resolveSectionStyle(focusedModule.section, draft)
        val clayRes = resolveClayAsset(focusedModule)
        val iconRenderer = resolveModuleIcon(focusedModule)
        val moduleScreens = draft.screens.filter { it.moduleId == focusedModule.id }

        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
        ) {
            // Header Kartu Modul
            ClayCard(
                modifier = Modifier.fillMaxWidth(),
                shape = ClayShapes.Card,
                containerColor = if (focusedModule.active) WeMadeColors.Surface else WeMadeColors.SurfaceMuted,
                outlineColor = if (focusedModule.active) sectionStyle.color else WeMadeColors.Outline,
                offset = ClayOffset.Small
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(ClaySpacing.Md),
                    verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
                        ) {
                            ClayBadge(
                                text = sectionStyle.title.uppercase(),
                                tint = sectionStyle.color
                            )
                            ClayBadge(
                                text = if (focusedModule.active) "AKTIF" else "Non-aktif",
                                tint = if (focusedModule.active) WeMadeColors.Success else WeMadeColors.OnSurfaceMuted,
                                dot = focusedModule.active
                            )
                            focusedModule.slot?.let {
                                ClayBadge(text = "Slot: $it", tint = WeMadeColors.Primary)
                            }
                        }
                        Text(
                            text = "ID: ${focusedModule.id}",
                            style = typography.bodySmall,
                            color = WeMadeColors.OnSurfaceMuted
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Md),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .claySurface(
                                    shape = ClayShapes.Button,
                                    background = sectionStyle.background,
                                    outline = sectionStyle.color,
                                    offset = ClayOffset.Small,
                                    borderWidth = ClayBorder.Medium
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (clayRes != null) {
                                Image(
                                    painter = painterResource(clayRes),
                                    contentDescription = focusedModule.displayName,
                                    modifier = Modifier.size(44.dp)
                                )
                            } else {
                                iconRenderer(Modifier.size(28.dp), sectionStyle.color)
                            }
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Xxs)) {
                            Text(
                                text = focusedModule.displayName,
                                style = typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = WeMadeColors.OnSurface
                            )
                            Text(
                                text = "Divisi: ${sectionStyle.title} · Kind: ${focusedModule.kind}",
                                style = typography.bodySmall,
                                color = WeMadeColors.OnSurfaceMuted
                            )
                        }
                    }

                    // Port Alur Data Masuk dan Keluar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .claySurface(
                                shape = ClayShapes.Pill,
                                background = WeMadeColors.SurfaceMuted,
                                outline = WeMadeColors.OutlineSoft,
                                offset = ClayOffset.Flat,
                                borderWidth = ClayBorder.Hairline
                            )
                            .padding(horizontal = ClaySpacing.Md, vertical = ClaySpacing.Xs),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "In: ${focusedModule.slotInput ?: "Tanpa input eksternal"}",
                            fontSize = 11.sp,
                            color = WeMadeColors.OnSurfaceMuted
                        )
                        Text(text = "→", fontSize = 11.sp, color = WeMadeColors.OnSurfaceMuted)
                        Text(
                            text = "Out: ${focusedModule.slotOutput ?: "Tanpa output eksternal"}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = WeMadeColors.OnSurface
                        )
                    }
                }
            }

            // Seksi Fitur & Kemampuan Operasional
            ClayCard(
                modifier = Modifier.fillMaxWidth(),
                shape = ClayShapes.Card,
                offset = ClayOffset.Small
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(ClaySpacing.Md),
                    verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
                ) {
                    Text(
                        text = "Fitur & Kemampuan Operasional",
                        style = typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = WeMadeColors.OnSurface
                    )

                    val features = buildList {
                        focusedModule.slotInput?.let { add("Menerima dokumen alur: $it") }
                        focusedModule.slotOutput?.let { add("Menghasilkan dokumen output: $it") }
                        if (moduleScreens.isNotEmpty()) {
                            moduleScreens.forEach { add("Layar kerja aktif: ${it.title} (Widget: ${it.widget})") }
                        } else {
                            add("Dukungan proses operasional alur kerja pabrik")
                            add("Integrasi state & pencatatan aktivitas terstandarisasi")
                        }
                    }

                    features.forEach { item ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(sectionStyle.color, CircleShape)
                            )
                            Text(
                                text = item,
                                style = typography.bodySmall,
                                color = WeMadeColors.OnSurface
                            )
                        }
                    }
                }
            }

            // Panduan Prompt AI
            ClayCard(
                modifier = Modifier.fillMaxWidth(),
                containerColor = WeMadeColors.SurfaceMuted,
                shape = ClayShapes.Card
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(ClaySpacing.Md),
                    verticalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)
                ) {
                    Text(
                        text = "Penyelarasan Fitur via Chat AI",
                        style = typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = WeMadeColors.OnSurface
                    )
                    Text(
                        text = "Untuk menambah atau mengubah aturan alur modul ${focusedModule.displayName}, Anda dapat langsung mengetikkan instruksi di panel chat sebelah kiri.",
                        style = typography.bodySmall,
                        color = WeMadeColors.OnSurfaceMuted
                    )
                }
            }
        }
    } else {
        // Tampilan Semua Modul (Ketika thread == null / Semua)
        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
        ) {
            Text(
                text = "Ringkasan Seluruh Modul Tenant (${draft.modules.size})",
                style = typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = WeMadeColors.OnSurface
            )

            draft.modules.forEach { mod ->
                val sectionStyle = resolveSectionStyle(mod.section, draft)
                val iconRenderer = resolveModuleIcon(mod)

                ClayCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectModule?.invoke(mod.id) },
                    shape = ClayShapes.Card,
                    offset = ClayOffset.Small
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(ClaySpacing.Md),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .claySurface(
                                        shape = ClayShapes.Button,
                                        background = sectionStyle.background,
                                        outline = sectionStyle.color,
                                        offset = ClayOffset.Flat,
                                        borderWidth = ClayBorder.Hairline
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                iconRenderer(Modifier.size(20.dp), sectionStyle.color)
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = mod.displayName,
                                    style = typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = WeMadeColors.OnSurface
                                )
                                Text(
                                    text = "Divisi: ${sectionStyle.title} · In: ${mod.slotInput ?: "–"} → Out: ${mod.slotOutput ?: "–"}",
                                    fontSize = 11.sp,
                                    color = WeMadeColors.OnSurfaceMuted
                                )
                            }
                        }

                        ClayBadge(
                            text = if (mod.active) "AKTIF" else "Non-aktif",
                            tint = if (mod.active) WeMadeColors.Success else WeMadeColors.OnSurfaceMuted,
                            dot = mod.active
                        )
                    }
                }
            }
        }
    }
}

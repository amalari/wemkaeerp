package com.eventverse.app.presentation.discovery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.eventverse.app.presentation.designsystem.ClayBadge
import com.eventverse.app.presentation.designsystem.ClayCard
import com.eventverse.app.presentation.designsystem.ClayPaneWidth
import com.eventverse.app.presentation.designsystem.ClayShapes
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.ClayTag
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * `ModuleMapPane` (plan §4, Fase C/D): kanvas **read-only** dari blueprint — kolom per seksi pack,
 * kartu modul aktif menonjol, modul non-aktif diredupkan. Tidak ada interaksi ubah: menyusun alur
 * tetap pekerjaan Factory Flow, di sini prospek hanya *melihat* sistemnya.
 */
@Composable
fun ModuleMapPane(
    draft: DiscoveryDraftUi,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Lg)
    ) {
        draft.sections.forEach { section ->
            val inSection = draft.modules.filter { it.section == section }
            Column(
                modifier = Modifier
                    .width(ClayPaneWidth.Board)
                    .background(WeMadeColors.SurfaceMuted.copy(alpha = 0.14f), ClayShapes.Panel)
                    .padding(ClaySpacing.Md),
                verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
            ) {
                Text(
                    text = section.replace('_', ' ').uppercase(),
                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                    color = WeMadeColors.OnSurfaceMuted,
                    fontWeight = FontWeight.Bold
                )
                inSection.forEach { module ->
                    ClayCard(
                        modifier = Modifier.fillMaxWidth(),
                        // Peredupan modul non-aktif memakai token **opaque**, bukan `.copy(alpha = …)`:
                        // bayangan hard digambar tepat di belakang kartu, jadi warna transparan akan
                        // menampakkan bayangan menembus kartu (kartu tampak navy gelap, bukan redup).
                        containerColor = if (module.active) WeMadeColors.Surface else WeMadeColors.SurfaceMuted,
                        outlineColor = if (module.active) WeMadeColors.Primary else WeMadeColors.OutlineSoft
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(modifier = Modifier.weight(1f, fill = false)) {
                                Text(
                                    text = module.displayName,
                                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                    fontWeight = if (module.active) FontWeight.Bold else FontWeight.Normal,
                                    color = if (module.active) WeMadeColors.OnSurface else WeMadeColors.OnSurfaceMuted,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (module.active) {
                                ClayTag(text = "AKTIF", tint = WeMadeColors.Success)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * `DataFlowPane` (plan §4): hint port informasional — data apa yang masuk dan keluar tiap modul
 * aktif, sesuai definisi slot pack. Read-only; menyambung node tetap pekerjaan kanvas Factory Flow.
 */
@Composable
fun DataFlowPane(
    draft: DiscoveryDraftUi,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
        draft.activeModules.forEach { module ->
            ClayCard(modifier = Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(ClaySpacing.Lg)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(modifier = Modifier.weight(1f, fill = false)) {
                        Text(
                            text = module.displayName,
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Masuk: ${module.slotInput ?: "—"}  •  Keluar: ${module.slotOutput ?: "—"}",
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                            color = WeMadeColors.OnSurfaceMuted,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    ClayBadge(
                        text = module.slot ?: "kustom",
                        tint = WeMadeColors.Primary,
                        modifier = Modifier.padding(start = ClaySpacing.Sm)
                    )
                }
            }
        }
    }
}

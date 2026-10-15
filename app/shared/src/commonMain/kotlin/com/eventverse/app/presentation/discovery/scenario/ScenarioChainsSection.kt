package com.eventverse.app.presentation.discovery.scenario

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.presentation.designsystem.ClayBadge
import com.eventverse.app.presentation.designsystem.ClayBorder
import com.eventverse.app.presentation.designsystem.ClayCard
import com.eventverse.app.presentation.designsystem.ClayShapes
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.ClayTag
import com.eventverse.app.presentation.designsystem.IconArrowForward
import com.eventverse.app.presentation.designsystem.clayFlat
import com.eventverse.app.presentation.designsystem.rememberClayTypography
import com.eventverse.app.presentation.discovery.DiscoveryDraftUi
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Tingkat kedua tab Alur Data (TRD-BLDR-002 FR-7): satu rantai horizontal per skenario **terkonfirmasi** (diusulkan tidak
 * muncul: tebakan tidak boleh tampil sebagai spesifikasi). Data dari [scenarioChains]; file ini hanya menggambar.
 */
@Composable
fun ScenarioChainsSection(draft: DiscoveryDraftUi, modifier: Modifier = Modifier) {
    val typography = rememberClayTypography()
    val chains = remember(draft) { scenarioChains(draft) }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)) {
        if (chains.isEmpty()) {
            ClayCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(ClaySpacing.Md)) {
                Text(
                    text = if (draft.scenarios.isEmpty()) "Belum ada skenario. Usulkan di tab Prototype."
                    else "Belum ada skenario terkonfirmasi. Konfirmasi dulu di tab Prototype.",
                    style = typography.bodyMedium,
                    color = WeMadeColors.OnSurfaceMuted
                )
            }
        }
        chains.forEach { chain ->
            ClayCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(ClaySpacing.Md)) {
                Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = chain.title,
                            style = typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = WeMadeColors.OnSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        ClayBadge(text = scenarioSourceLabel(chain.source), tint = WeMadeColors.Info, fontSize = 10.sp)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        chain.steps.forEachIndexed { i, step ->
                            if (i > 0) IconArrowForward(Modifier.size(14.dp), WeMadeColors.OnSurfaceMuted)
                            Column(
                                modifier = Modifier
                                    .clayFlat(
                                        shape = ClayShapes.Chip,
                                        background = WeMadeColors.Surface,
                                        outline = if (step.skeleton) WeMadeColors.Purple else WeMadeColors.Outline,
                                        borderWidth = ClayBorder.Medium
                                    )
                                    .padding(horizontal = ClaySpacing.Md, vertical = ClaySpacing.Sm),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(step.moduleName, style = typography.bodySmall, fontSize = 10.sp, color = WeMadeColors.OnSurfaceMuted, maxLines = 1)
                                Text(step.label, style = typography.bodySmall, fontWeight = FontWeight.Bold, color = WeMadeColors.OnSurface, maxLines = 1)
                                if (step.skeleton) ClayTag(text = "Kerangka", tint = WeMadeColors.Purple)
                            }
                        }
                    }
                }
            }
        }
    }
}

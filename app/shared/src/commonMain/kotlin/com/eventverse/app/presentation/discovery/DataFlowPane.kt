package com.eventverse.app.presentation.discovery

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.presentation.designsystem.ClayBadge
import com.eventverse.app.presentation.designsystem.ClayCard
import com.eventverse.app.presentation.designsystem.ClayChoiceChip
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.rememberClayTypography
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * `DataFlowPane` (plan §4): peta aliran data antar modul aktif — input tiap modul **dari modul
 * mana**, outputnya **apa** dan **masuk ke modul mana**. Sambungan dihitung dari kontrak port
 * domain lewat [buildDataFlowMap]; pane ini hanya menggambar.
 */
@Composable
fun DataFlowPane(
    draft: DiscoveryDraftUi,
    modifier: Modifier = Modifier
) {
    val typography = rememberClayTypography()
    val map = remember(draft) { buildDataFlowMap(draft) }
    var selectedSection by remember { mutableStateOf<String?>(null) }

    val displayedFlows = remember(map, selectedSection) {
        if (selectedSection == null) map.flows
        else map.flows.filter { it.module.section == selectedSection }
    }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ClaySpacing.Lg)) {
        // 4 KPI Summary Cards
        DataFlowSummaryCards(map = map)

        // Peta Rantai Alur Nilai (Pipeline Flow Chain - Opsi 1)
        DataFlowPipelineChain(map = map, draft = draft)

        // Header Rincian per Modul + Filter Bar Departemen
        Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Rincian Alur per Modul",
                    style = typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = WeMadeColors.OnSurface
                )
                ClayBadge(
                    text = "${displayedFlows.size} dari ${map.flows.size} Modul",
                    tint = WeMadeColors.Primary,
                    fontSize = 10.sp
                )
            }

            // Filter Chips per Departemen
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ClayChoiceChip(
                    text = "Semua (${map.flows.size})",
                    selected = selectedSection == null,
                    onClick = { selectedSection = null }
                )
                draft.sections.forEach { section ->
                    val count = map.flows.count { it.module.section == section }
                    if (count > 0) {
                        val style = resolveSectionStyle(section, draft)
                        ClayChoiceChip(
                            text = "${style.title} ($count)",
                            selected = selectedSection == section,
                            tint = style.color,
                            onClick = { selectedSection = section }
                        )
                    }
                }
            }
        }

        // Modul Alur: Grid 2 Kolom Responsif di Desktop
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val isWide = maxWidth >= 860.dp
            if (isWide) {
                Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)) {
                    displayedFlows.chunked(2).forEach { rowFlows ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
                        ) {
                            rowFlows.forEach { flow ->
                                ModuleFlowCard(flow = flow, draft = draft, modifier = Modifier.weight(1f))
                            }
                            if (rowFlows.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)) {
                    displayedFlows.forEach { flow ->
                        ModuleFlowCard(flow = flow, draft = draft, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

/** 4 Kartu Ringkasan Metrik Alur Data. */
@Composable
private fun DataFlowSummaryCards(map: DataFlowMap) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
    ) {
        StatTile(
            title = "Modul Aktif",
            value = "${map.flows.size}",
            subtitle = "stasiun kerja alur",
            tint = WeMadeColors.Success,
            modifier = Modifier.weight(1f)
        )
        StatTile(
            title = "Sambungan Port",
            value = "${map.connectionCount}",
            subtitle = "serah-terima data",
            tint = WeMadeColors.Primary,
            modifier = Modifier.weight(1f)
        )
        StatTile(
            title = "Input Eksternal",
            value = "${map.externalInputCount}",
            subtitle = "dari luar sistem",
            tint = WeMadeColors.Info,
            modifier = Modifier.weight(1f)
        )
        StatTile(
            title = "Keluaran Akhir",
            value = "${map.endOutputCount}",
            subtitle = "ujung rantai alur",
            tint = WeMadeColors.Accent,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun StatTile(
    title: String,
    value: String,
    subtitle: String,
    tint: Color,
    modifier: Modifier = Modifier
) {
    val typography = rememberClayTypography()
    ClayCard(modifier = modifier, contentPadding = PaddingValues(ClaySpacing.Md)) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(Modifier.size(8.dp).background(tint, CircleShape))
                Text(
                    text = title.uppercase(),
                    style = typography.bodySmall,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = WeMadeColors.OnSurfaceMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = value,
                style = typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = WeMadeColors.OnSurface
            )
            Text(
                text = subtitle,
                style = typography.bodySmall,
                fontSize = 10.sp,
                color = WeMadeColors.OnSurfaceMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

internal fun sectionTint(module: DiscoveryModuleUi, draft: DiscoveryDraftUi) =
    resolveSectionStyle(module.section, draft).color

package com.eventverse.app.presentation.discovery.scenario

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.eventverse.app.domain.discovery.scenario.DefaultScenarioCarryRule
import com.eventverse.app.domain.discovery.scenario.ScenarioCarryRule
import com.eventverse.app.domain.discovery.scenario.ScenarioSketch
import com.eventverse.app.presentation.designsystem.ClayBadge
import com.eventverse.app.presentation.designsystem.ClayButton
import com.eventverse.app.presentation.designsystem.ClayButtonStyle
import com.eventverse.app.presentation.designsystem.ClayCard
import com.eventverse.app.presentation.designsystem.ClayChoiceChip
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.ClayTag
import com.eventverse.app.presentation.designsystem.rememberClayTypography
import com.eventverse.app.presentation.theme.WeMadeColors

/** Aksi server + aturan pembawa untuk panel skenario; dibangun pemanggil (chat Builder), dibaca [ScenarioPicker]. */
data class ScenarioActions(
    val carryRule: ScenarioCarryRule = DefaultScenarioCarryRule,
    val busy: Boolean = false,
    val notice: String? = null,
    val onPropose: () -> Unit = {},
    val onConfirm: (String) -> Unit = {}
)

/**
 * Pemilih skenario di atas navigator prototype (TRD-BLDR-002 FR-7). Tanpa pilihan = mode lama (urutan daftar layar).
 * Menampilkan sumber (AI/DETERMINISTIC/USER), status, tanda langkah berkerangka, dan tombol Konfirmasi/Usulkan.
 * Hanya menggambar; keputusan ada di [ScenarioActions] dan pemutar.
 */
@Composable
fun ScenarioPicker(
    scenarios: List<ScenarioSketch>,
    selectedId: String?,
    onSelect: (String?) -> Unit,
    actions: ScenarioActions,
    modifier: Modifier = Modifier
) {
    val typography = rememberClayTypography()
    val selected = scenarios.firstOrNull { it.id.value == selectedId }
    ClayCard(modifier = modifier.fillMaxWidth(), contentPadding = PaddingValues(ClaySpacing.Sm)) {
        Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f, fill = false).horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Skenario", style = typography.bodySmall, fontWeight = FontWeight.Bold, color = WeMadeColors.OnSurface, maxLines = 1)
                    ClayChoiceChip(text = "Per modul", selected = selected == null, onClick = { onSelect(null) })
                    scenarios.forEach { sc ->
                        ClayChoiceChip(text = sc.title, selected = sc.id.value == selectedId, onClick = { onSelect(sc.id.value) })
                    }
                }
                ClayButton(
                    text = if (actions.busy) "Menyusun..." else "Usulkan skenario",
                    onClick = actions.onPropose,
                    enabled = !actions.busy,
                    style = ClayButtonStyle.Secondary,
                    fontSize = 11.sp,
                    maxLines = 1
                )
            }
            if (scenarios.isEmpty()) {
                Text("Belum ada skenario.", style = typography.bodySmall, color = WeMadeColors.OnSurfaceMuted)
            }
            selected?.let { sc ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ClayBadge(text = scenarioSourceLabel(sc.source), tint = WeMadeColors.Info, fontSize = 10.sp)
                    ClayBadge(
                        text = scenarioStatusLabel(sc),
                        tint = if (sc.confirmed) WeMadeColors.Success else WeMadeColors.Warning,
                        dot = true,
                        fontSize = 10.sp
                    )
                    val skeletons = sc.steps.count { it.screenId == null }
                    if (skeletons > 0) ClayTag(text = "$skeletons langkah kerangka", tint = WeMadeColors.Purple)
                    Text(
                        text = "${sc.steps.size} langkah",
                        style = typography.bodySmall,
                        color = WeMadeColors.OnSurfaceMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (!sc.confirmed) {
                        ClayButton(
                            text = "Konfirmasi",
                            onClick = { actions.onConfirm(sc.id.value) },
                            enabled = !actions.busy,
                            style = ClayButtonStyle.Success,
                            fontSize = 11.sp,
                            maxLines = 1
                        )
                    }
                }
            }
            actions.notice?.let {
                Text(it, style = typography.bodySmall, color = WeMadeColors.OnSurfaceMuted)
            }
        }
    }
}

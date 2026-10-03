package com.eventverse.app.presentation.builder.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eventverse.app.presentation.designsystem.ClayCard
import com.eventverse.app.presentation.designsystem.ClayIconButton
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.ClayTabBar
import com.eventverse.app.presentation.designsystem.IconClose
import com.eventverse.app.presentation.designsystem.rememberClayTypography
import com.eventverse.app.presentation.discovery.DataFlowPane
import com.eventverse.app.presentation.discovery.DiscoveryDraftUi
import com.eventverse.app.presentation.discovery.ModuleMapPane
import com.eventverse.app.presentation.discovery.PrototypeRenderer
import com.eventverse.app.presentation.theme.WeMadeColors

private val TABS = listOf("Modul", "Fitur", "Alur Data", "Prototype")

/**
 * Panel "Hasil request": empat tab di atas draf kerja tenant yang sama dengan pane Modules/Data Flow/
 * Prototype — tidak ada logika tampilan baru, hanya komposisi ulang. [patchPreview] (bila ada) adalah
 * ringkasan usulan agent yang belum diterapkan; draf yang ditampilkan tetap draf aktif sampai Terapkan.
 */
@Composable
internal fun ChatResultPanel(
    draft: DiscoveryDraftUi?,
    patchPreview: List<String>,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val typography = rememberClayTypography()
    var tab by remember { mutableStateOf(0) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Hasil request",
                style = typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = WeMadeColors.OnSurface
            )
            ClayIconButton(onClick = onClose) { IconClose(Modifier.size(14.dp)) }
        }
        ClayTabBar(
            tabs = TABS,
            selectedIndex = tab,
            onSelect = { tab = it },
            badges = listOf(draft?.activeModules?.size?.toString(), draft?.let { featuresOf(it).size.toString() }, null, draft?.screens?.size?.toString())
        )
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
        ) {
            if (patchPreview.isNotEmpty()) {
                ClayCard(containerColor = WeMadeColors.SurfaceMuted, outlineColor = WeMadeColors.Warning) {
                    Text("Usulan belum diterapkan", style = typography.bodySmall, fontWeight = FontWeight.Bold, color = WeMadeColors.OnSurface)
                    patchPreview.forEach { Text("• $it", style = typography.bodySmall, color = WeMadeColors.OnSurfaceMuted) }
                }
            }
            if (draft == null) {
                Text("Memuat draf…", style = typography.bodyMedium, color = WeMadeColors.OnSurfaceMuted)
            } else when (tab) {
                0 -> ModuleMapPane(draft = draft)
                1 -> FeaturesTab(featuresOf(draft))
                2 -> DataFlowPane(draft = draft)
                else -> if (draft.screens.isEmpty()) {
                    Text(
                        "Belum ada prototype — minta agent membuatnya, mis. \"buat layar untuk modul QC\".",
                        style = typography.bodyMedium,
                        color = WeMadeColors.OnSurfaceMuted
                    )
                } else PrototypeRenderer(draft = draft)
            }
        }
    }
}

@Composable
private fun FeaturesTab(groups: List<ModuleFeatures>) {
    val typography = rememberClayTypography()
    groups.forEach { g ->
        ClayCard {
            Text(g.moduleName, style = typography.titleSmall, fontWeight = FontWeight.Bold, color = WeMadeColors.OnSurface)
            g.items.forEach { Text("• $it", style = typography.bodySmall, color = WeMadeColors.OnSurfaceMuted) }
        }
    }
}

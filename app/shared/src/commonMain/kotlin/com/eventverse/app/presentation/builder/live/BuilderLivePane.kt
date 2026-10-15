package com.eventverse.app.presentation.builder.live

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.eventverse.app.presentation.builder.BuilderDataFlowPane
import com.eventverse.app.presentation.builder.BuilderModulesPane
import com.eventverse.app.presentation.builder.BuilderPrototypePane
import com.eventverse.app.presentation.designsystem.ClayButton
import com.eventverse.app.presentation.designsystem.ClayButtonStyle
import com.eventverse.app.presentation.designsystem.ClaySegmentedBar
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.rememberClayTypography
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Tab pane Live (TRD-PLAT-014 FR-3); `key`-nya segmen route `/builder/live/<key>` sehingga
 * deep-link `live/preview` & `live/production` (termasuk redirect dari `modules`/`deployments`)
 * membuka tab yang tepat. Key tak dikenal → Preview (default, bukan 404).
 */
internal enum class BuilderLiveTab(val key: String, val label: String) {
    PREVIEW("preview", "Preview"),
    PRODUCTION("production", "Production");

    companion object {
        fun fromKey(key: String?): BuilderLiveTab =
            entries.firstOrNull { it.key == key } ?: PREVIEW
    }
}

/** Sub-tab Preview (PLAN §7.3): tiap pane lama punya interaksinya sendiri — tidak digabung satu gulir. */
internal enum class BuilderPreviewSection(val label: String) {
    MODULES("Modules"), DATA_FLOW("Data Flow"), PROTOTYPE("Prototype");
}

/**
 * Pane Live (FR-3): satu menu, dua dunia berbeda — **Preview** (draf kerja: Modules | Data Flow |
 * Prototype, re-host pane lama apa adanya) dan **Production** (runtime: status, deployment aktif,
 * modul live). Transisi antar-tab eksplisit lewat tombol (FR-3-3) karena keduanya membaca dua
 * dunia berbeda dan tidak boleh tertukar diam-diam.
 */
@Composable
fun BuilderLivePane(
    tab: String?,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
    onOpenModule: (String) -> Unit = {}
) {
    val typography = rememberClayTypography()
    val current = BuilderLiveTab.fromKey(tab)
    var previewSection by remember { mutableStateOf(BuilderPreviewSection.MODULES) }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Live",
                style = typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = WeMadeColors.OnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            ClaySegmentedBar(
                tabs = BuilderLiveTab.entries.map { it.label },
                selectedIndex = BuilderLiveTab.entries.indexOf(current),
                onSelect = { index -> onNavigate("live/${BuilderLiveTab.entries[index].key}") },
                modifier = Modifier.weight(1f, fill = false)
            )
            // FR-3-3: pergantian dunia dinyatakan eksplisit, bukan sekadar geser tab.
            ClayButton(
                text = if (current == BuilderLiveTab.PREVIEW) "Lihat di Production" else "Lihat di Preview",
                onClick = {
                    onNavigate(
                        if (current == BuilderLiveTab.PREVIEW) "live/production" else "live/preview"
                    )
                },
                style = ClayButtonStyle.Secondary
            )
        }

        when (current) {
            BuilderLiveTab.PREVIEW -> {
                ClaySegmentedBar(
                    tabs = BuilderPreviewSection.entries.map { it.label },
                    selectedIndex = BuilderPreviewSection.entries.indexOf(previewSection),
                    onSelect = { index -> previewSection = BuilderPreviewSection.entries[index] }
                )
                // Re-host pane lama apa adanya (komposisi, bukan salin — NFR Maintainability):
                // masing-masing sudah memuat draf kerja tenant sendiri.
                when (previewSection) {
                    BuilderPreviewSection.MODULES -> BuilderModulesPane()
                    BuilderPreviewSection.DATA_FLOW -> BuilderDataFlowPane()
                    BuilderPreviewSection.PROTOTYPE -> BuilderPrototypePane()
                }
            }
            BuilderLiveTab.PRODUCTION -> BuilderProductionPane(
                onNavigate = onNavigate,
                onOpenModule = onOpenModule
            )
        }
    }
}

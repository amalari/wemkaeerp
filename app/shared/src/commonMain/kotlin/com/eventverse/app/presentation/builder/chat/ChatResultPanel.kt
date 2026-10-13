package com.eventverse.app.presentation.builder.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import com.eventverse.app.presentation.builder.BuilderDraftState
import com.eventverse.app.presentation.builder.BuilderDraftStatusView
import com.eventverse.app.presentation.designsystem.ClayCard
import com.eventverse.app.presentation.designsystem.ClayIconButton
import com.eventverse.app.presentation.designsystem.ClaySegmentedBar
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.IconClose
import com.eventverse.app.presentation.designsystem.rememberClayTypography
import com.eventverse.app.presentation.discovery.DataFlowPane
import com.eventverse.app.presentation.discovery.DiscoveryDraftUi
import com.eventverse.app.presentation.discovery.ModuleDetailPane
import com.eventverse.app.presentation.discovery.PrototypeRenderer
import com.eventverse.app.presentation.theme.WeMadeColors

private val TABS = listOf("Prototype", "Fitur & Spek", "Alur Data")

/**
 * Panel "Hasil request":
 * Tab 0: Prototype — tampil SELAYAR PENUH (full height) tanpa terpotong header/fitur.
 * Tab 1: Fitur & Spek — spesifikasi detail modul & port data.
 * Tab 2: Alur Data — diagram koneksi pipeline modul.
 */
@Composable
internal fun ChatResultPanel(
    draft: DiscoveryDraftUi?,
    draftState: BuilderDraftState,
    patchPreview: List<String>,
    onClose: (() -> Unit)?,
    modifier: Modifier = Modifier,
    /** Modul yang sedang dibahas di chat (null = Semua). */
    focusModuleId: String? = null
) {
    val typography = rememberClayTypography()
    var tab by remember { mutableStateOf(0) }

    val contextScreens = remember(draft, focusModuleId) {
        if (draft == null) emptyList()
        else if (focusModuleId != null) draft.screens.filter { it.moduleId == focusModuleId }
        else draft.screens
    }

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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
            ) {
                ClaySegmentedBar(
                    tabs = TABS,
                    selectedIndex = tab,
                    onSelect = { tab = it },
                    badges = listOf(
                        contextScreens.size.toString(),
                        if (focusModuleId != null) "Spek" else draft?.modules?.size?.toString(),
                        null
                    )
                )
                onClose?.let { close -> ClayIconButton(onClick = close) { IconClose(Modifier.size(14.dp)) } }
            }
        }

        if (patchPreview.isNotEmpty()) {
            ClayCard(containerColor = WeMadeColors.SurfaceMuted, outlineColor = WeMadeColors.Warning) {
                Text("Usulan belum diterapkan", style = typography.bodySmall, fontWeight = FontWeight.Bold, color = WeMadeColors.OnSurface)
                patchPreview.forEach { Text("· $it", style = typography.bodySmall, color = WeMadeColors.OnSurfaceMuted) }
            }
        }

        if (draft == null) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f).fillMaxHeight()) {
                BuilderDraftStatusView(draftState)
            }
        } else when (tab) {
            0 -> {
                // TAB 0: PROTOTYPE SELAYAR & FULL HEIGHT
                if (contextScreens.isEmpty()) {
                    val focusedName = draft.modules.firstOrNull { it.id == focusModuleId }?.displayName ?: focusModuleId
                    ClayCard(modifier = Modifier.fillMaxWidth().weight(1f).fillMaxHeight()) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(ClaySpacing.Md),
                            verticalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)
                        ) {
                            Text(
                                text = "Belum Ada Layar Prototype Khusus",
                                style = typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = WeMadeColors.OnSurface
                            )
                            Text(
                                text = if (focusModuleId != null) {
                                    "Modul '$focusedName' belum memiliki form atau tabel prototype. Anda dapat mengetik di chat: \"Buatkan layar untuk modul $focusedName\" agar asisten AI menyusun rancangannya."
                                } else {
                                    "Belum ada prototype - minta agent membuatnya, mis. \"buat layar untuk modul QC\"."
                                },
                                style = typography.bodyMedium,
                                color = WeMadeColors.OnSurfaceMuted
                            )
                        }
                    }
                } else {
                    PrototypeRenderer(
                        draft = draft,
                        screens = contextScreens,
                        carousel = true,
                        fullWidth = true,
                        modifier = Modifier.fillMaxWidth().weight(1f).fillMaxHeight()
                    )
                }
            }
            1 -> {
                Box(modifier = Modifier.fillMaxWidth().weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) {
                    ModuleDetailPane(draft = draft, focusModuleId = focusModuleId)
                }
            }
            else -> {
                Box(modifier = Modifier.fillMaxWidth().weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) {
                    DataFlowPane(draft = draft)
                }
            }
        }
    }
}

package com.eventverse.app.presentation.builder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.infrastructure.api.BuilderApiClient
import com.eventverse.app.presentation.designsystem.ClayBadge
import com.eventverse.app.presentation.designsystem.ClayButton
import com.eventverse.app.presentation.designsystem.ClayButtonStyle
import com.eventverse.app.presentation.designsystem.ClayFlowRow
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.rememberClayTypography
import com.eventverse.app.presentation.discovery.DataFlowPane
import com.eventverse.app.presentation.discovery.DiscoveryDraftUi
import com.eventverse.app.presentation.discovery.ModuleMapPane
import com.eventverse.app.presentation.discovery.PrototypePricePanel
import com.eventverse.app.presentation.discovery.PrototypeRenderer
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Pane Modules / Data Flow / Prototype (FR-M1-4/5): ketiganya membaca **draf kerja tenant yang
 * sama** (`GET /api/builder/draft`) dan men-render-nya dengan komponen Fase D yang sudah teruji
 * ([ModuleMapPane]/[DataFlowPane]/[PrototypeRenderer]) — nol logika tampilan baru.
 */
@Composable
private fun BuilderTenantDraft(content: @Composable (DiscoveryDraftUi) -> Unit) {
    val client = remember { BuilderApiClient() }
    var state by remember { mutableStateOf<BuilderDraftState>(BuilderDraftState.Loading) }

    LaunchedEffect(Unit) { state = BuilderDraftState.load(client) }

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)) {
        val current = state.draftOrNull
        if (current == null) {
            BuilderDraftStatusView(state)
        } else {
            content(current)
        }
    }
}

@Composable
fun BuilderModulesPane(modifier: Modifier = Modifier) {
    BuilderTenantDraft { draft ->
        val typography = rememberClayTypography()
        Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = draft.packDisplayName,
                        style = typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = WeMadeColors.OnSurface
                    )
                    Text(
                        text = "Peta stasiun kerja operasional dan modul alur produksi. Dikonfigurasi dari blueprint pabrik.",
                        style = typography.bodySmall,
                        fontSize = 11.sp,
                        color = WeMadeColors.OnSurfaceMuted
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm), verticalAlignment = Alignment.CenterVertically) {
                    ClayBadge(text = "${draft.activeModules.size} Modul Aktif", tint = WeMadeColors.Success, dot = true)
                    ClayBadge(text = "${draft.sections.size} Departemen", tint = WeMadeColors.Primary)
                    ClayBadge(text = draft.blueprintCode.uppercase(), tint = WeMadeColors.Info)
                }
            }

            ModuleMapPane(draft = draft)
        }
    }
}

@Composable
fun BuilderDataFlowPane(modifier: Modifier = Modifier) {
    BuilderTenantDraft { draft ->
        val typography = rememberClayTypography()
        Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "${draft.packDisplayName} · Aliran Data",
                        style = typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = WeMadeColors.OnSurface
                    )
                    Text(
                        text = "Peta serah-terima data antar modul aktif. Sambungan dihitung otomatis dari kontrak port domain.",
                        style = typography.bodySmall,
                        fontSize = 11.sp,
                        color = WeMadeColors.OnSurfaceMuted
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm), verticalAlignment = Alignment.CenterVertically) {
                    val opsCount = draft.activeModules.count { it.kind == "OPERATIONAL" }
                    ClayBadge(text = "$opsCount Modul Alur", tint = WeMadeColors.Success, dot = true)
                    ClayBadge(text = "${draft.sections.size} Departemen", tint = WeMadeColors.Primary)
                    ClayBadge(text = draft.blueprintCode.uppercase(), tint = WeMadeColors.Info)
                }
            }

            DataFlowPane(draft = draft)
        }
    }
}

@Composable
fun BuilderPrototypePane(modifier: Modifier = Modifier) {
    BuilderTenantDraft { draft ->
        val typography = rememberClayTypography()
        var currentScreens by remember(draft.id) { mutableStateOf(draft.screens) }
        // Sesi hidup selama drafnya sama: perubahan spec lewat chat memperbarui blok DI DALAM sesi
        // (`updateScreenSpec`). Jika sesi dikunci pada `currentScreens`, ia dibuat ulang tiap perubahan dan
        // log tangkapan, riwayat undo, dan posisi kartu ikut hilang.
        val session = remember(draft.id) { com.eventverse.app.presentation.discovery.PrototypeSession(draft.screens) }
        var showExportDialog by remember { mutableStateOf(false) }

        // "Per modul ada apa aja": filter modul — pilih satu modul, pratinjau hanya menampilkan
        // layar miliknya. null = Semua Modul. Urutan chip mengikuti urutan modul di pack.
        var selectedModule by remember(draft.id) { mutableStateOf<String?>(null) }
        val modulesWithScreens = draft.modules.filter { m -> currentScreens.any { it.moduleId == m.id } }
        // Modul yang dipakai (panel harga): layar dan harga sama-sama mengikutinya. Awalnya semua.
        var included by remember(draft.id) { mutableStateOf(modulesWithScreens.map { it.id }.toSet()) }
        val includedScreens = currentScreens.filter { it.moduleId in included }
        // Dasbor menghitung dari modul yang dipakai saja (what-if panel harga); sesi tidak lagi dibuat ulang per pilihan.
        androidx.compose.runtime.SideEffect { session.includedModuleIds = included }
        val visibleScreens = selectedModule?.takeIf { it in included }
            ?.let { id -> includedScreens.filter { it.moduleId == id } }
            ?: includedScreens

        Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Pratinjau layar",
                        style = typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = WeMadeColors.OnSurface
                    )
                    ClayBadge(
                        text = "${visibleScreens.size} dari ${currentScreens.size} layar",
                        tint = WeMadeColors.Primary,
                        dot = true
                    )
                }

                // A5: Tombol Ekspor Brief Kebutuhan
                com.eventverse.app.presentation.designsystem.ClayButton(
                    text = "Ekspor Brief",
                    style = com.eventverse.app.presentation.designsystem.ClayButtonStyle.Primary,
                    onClick = { showExportDialog = true }
                )
            }

            // A4: Panel Chat Edit Prototype
            com.eventverse.app.presentation.discovery.PrototypeChatEditPanel(
                screens = currentScreens,
                session = session,
                onScreensUpdated = { currentScreens = it }
            )

            PrototypePricePanel(
                draft = draft.copy(screens = currentScreens),
                included = included,
                onToggle = { id -> included = if (id in included) included - id else included + id }
            )

            if (modulesWithScreens.size > 1) {
                ClayFlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    spacing = ClaySpacing.Sm
                ) {
                    ClayButton(
                        text = "Semua Modul",
                        onClick = { selectedModule = null },
                        style = if (selectedModule == null) ClayButtonStyle.Primary else ClayButtonStyle.Secondary
                    )
                    modulesWithScreens.forEach { m ->
                        ClayButton(
                            text = m.displayName,
                            onClick = { selectedModule = m.id },
                            style = if (selectedModule == m.id) ClayButtonStyle.Primary else ClayButtonStyle.Secondary
                        )
                    }
                }
            }

            PrototypeRenderer(
                draft = draft.copy(screens = includedScreens),
                screens = visibleScreens,
                session = session
            )
        }

        if (showExportDialog) {
            com.eventverse.app.presentation.discovery.PrototypeExportBriefDialog(
                draft = draft.copy(screens = currentScreens),
                includedModuleIds = included,
                session = session,
                onDismissRequest = { showExportDialog = false }
            )
        }
    }
}

package com.eventverse.app.presentation.tutorial

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import com.eventverse.app.domain.pack.ModuleId
import com.eventverse.app.domain.pack.ShippedTutorialSource
import com.eventverse.app.domain.rbac.AccessDecision
import com.eventverse.app.domain.tutorial.TutorialAccess
import com.eventverse.app.domain.tutorial.TutorialCatalog
import com.eventverse.app.presentation.help.HelpActionRequests
import com.eventverse.app.presentation.help.HelpChatPanel
import com.eventverse.app.presentation.help.HelpChatUiEffect
import com.eventverse.app.presentation.help.HelpChatViewModel
import com.eventverse.app.presentation.navigation.AppNavScreen
import com.eventverse.app.presentation.pack.ActiveTenantPack
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/** Batas tunggu anchor muncul setelah pindah layar; lewat dari ini callout tampil di tengah (FR-3). */
private const val ANCHOR_WAIT_MS = 1_500L

private val catalog = TutorialCatalog(ShippedTutorialSource)

/** State tutorial satu aplikasi: controller, registry anchor, dan apakah daftar tutorial sedang terbuka. */
@Stable
class TutorialUiState internal constructor(
    val controller: TutorialController,
    val anchors: TutorialAnchorRegistry,
    val chat: HelpChatViewModel,
) {
    var isListOpen by mutableStateOf(false)
    var tab by mutableStateOf(HelpTab.TUTORIALS)
}

/** Satu per aplikasi: riwayat chat bertahan selama sesi walau jendela bantuan ditutup-buka. */
@Composable
fun rememberTutorialUiState(): TutorialUiState =
    remember { TutorialUiState(TutorialController(), TutorialAnchorRegistry(), HelpChatViewModel()) }

/** Layar tujuan langkah untuk modul [id]: layar khusus bila ada, selain itu `null` (pemanggil memakai `/m/{code}`). */
fun tutorialScreenFor(id: ModuleId): AppNavScreen? =
    AppNavScreen.entries.firstOrNull { it.businessModule == id && it.isNavMenuItem }

/**
 * Daftar tutorial + overlay coach mark, dipasang sekali di akar `App`. Katalog disaring [decisions] di sini
 * (fail-closed), jadi baik daftar maupun tutorial yang sedang berjalan tidak pernah melewati wewenang.
 */
@Composable
fun TutorialLayer(
    state: TutorialUiState,
    currentModule: ModuleId?,
    decisions: Map<ModuleId, AccessDecision>,
    onNavigateToModule: (ModuleId) -> Unit,
) {
    val pack by ActiveTenantPack.flow.collectAsState()
    val accessible = remember(pack, decisions) { TutorialAccess.accessible(catalog.forPack(pack), decisions) }
    val run by state.controller.run.collectAsState()
    val latestModule by rememberUpdatedState(currentModule)

    LaunchedEffect(accessible, run) {
        // Wewenang bisa berubah saat tutorial berjalan (ganti persona) — tutorial yang tak lagi boleh dibaca berhenti.
        if (run != null && accessible.none { it.id == run?.tutorial?.id }) state.controller.dismiss()
    }

    val latestAccessible by rememberUpdatedState(accessible)
    val latestNavigate by rememberUpdatedState(onNavigateToModule)
    LaunchedEffect(state) {
        // Saran AI → tutorial lokal. Katalog klien sudah disaring wewenang yang sama; id yang tidak dikenal
        // (klien lebih lama dari server) diabaikan, bukan ditebak.
        state.chat.effects.collect { effect ->
            when (effect) {
                is HelpChatUiEffect.StartTutorial -> latestAccessible.firstOrNull { it.id == effect.suggestion.tutorialId }?.let {
                    state.isListOpen = false
                    state.controller.start(it, effect.suggestion.stepIndex)
                }
                // Aksi (Fase 5b): taruh di kotak surat, lalu buka layar modulnya — layar itu yang mengambil & menjalankan.
                is HelpChatUiEffect.RunAction -> {
                    HelpActionRequests.post(effect.action)
                    state.isListOpen = false
                    latestNavigate(effect.action.module)
                }
            }
        }
    }

    if (state.isListOpen) {
        val (forScreen, others) = accessible.partition { it.moduleId != null && it.moduleId == currentModule }
        HelpSheet(tab = state.tab, onTabChange = { state.tab = it }, onDismiss = { state.isListOpen = false }) { tab ->
            when (tab) {
                HelpTab.TUTORIALS -> TutorialListContent(forScreen, others, onStart = { state.isListOpen = false; state.controller.start(it) })
                HelpTab.ASK_AI -> HelpChatPanel(state.chat, currentModule)
            }
        }
    }

    val active = run ?: return
    var ready by remember(active) { mutableStateOf(false) }
    LaunchedEffect(active) {
        active.step.screen?.let { if (it != latestModule) onNavigateToModule(it) }
        active.step.anchor?.let { anchor ->
            withTimeoutOrNull(ANCHOR_WAIT_MS) { snapshotFlow { state.anchors.boundsOf(anchor) }.first { it != null } }
            state.anchors.bringIntoView(anchor)
        }
        ready = true
    }
    if (ready) {
        CoachMarkOverlay(
            run = active,
            targetInWindow = active.step.anchor?.let { state.anchors.boundsOf(it) },
            onBack = state.controller::back,
            onNext = state.controller::next,
            onClose = state.controller::dismiss
        )
    }
}

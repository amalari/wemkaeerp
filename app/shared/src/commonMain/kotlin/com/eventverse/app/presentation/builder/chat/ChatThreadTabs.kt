package com.eventverse.app.presentation.builder.chat

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.eventverse.app.presentation.designsystem.ClayTabBar

/** Satu utas chat: kode modul (null = Semua) dan label tampil. */
internal data class ChatThread(val moduleId: String?, val label: String)

/**
 * Tab utas chat (PLAN-builder-interview-chat K4): "Semua" (tanpa filter) lalu satu tab per modul. [pending] = jumlah
 * follow-up menunggu per utas (dari server) ditempel sebagai lencana. Buta domain: hanya label dan lambda.
 */
@Composable
internal fun ChatThreadTabs(
    threads: List<ChatThread>,
    selected: String?,
    pending: Map<String?, Int>,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    ClayTabBar(
        tabs = threads.map { it.label },
        selectedIndex = threads.indexOfFirst { it.moduleId == selected }.coerceAtLeast(0),
        onSelect = { onSelect(threads[it].moduleId) },
        modifier = modifier,
        badges = threads.map { t -> pending[t.moduleId]?.takeIf { it > 0 }?.toString() }
    )
}

/** Utas tersedia dari draf: "Semua" + modul aktif (urutan draf). */
internal fun threadsOf(modules: List<Pair<String, String>>): List<ChatThread> =
    listOf(ChatThread(null, "Semua")) + modules.map { (id, name) -> ChatThread(id, name) }

/** Label status run (SSE `status.phase`) untuk indikator memuat; fase tak dikenal ditampilkan apa adanya. */
internal fun runPhaseLabel(phase: String?): String = when (phase) {
    "planning" -> "Menyusun alur..."
    "drafting" -> "Menyusun draf..."
    "validating" -> "Memeriksa hasil..."
    "editing" -> "Mengubah modul..."
    null, "" -> "Memproses..."
    else -> "$phase..."
}

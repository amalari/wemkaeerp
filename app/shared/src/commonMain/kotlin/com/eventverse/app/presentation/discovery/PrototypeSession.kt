package com.eventverse.app.presentation.discovery

import com.eventverse.app.domain.discovery.WidgetKind
import com.eventverse.app.domain.prototype.InteractiveScreen
import com.eventverse.app.domain.prototype.PrototypeRow

/** Blok yang bisa dimainkan; [rows] = baris datanya, null untuk blok tanpa data (dasbor). */
sealed interface PlayableState {
    val rows: List<PrototypeRow>?
}

/**
 * Satu sesi prototype untuk seluruh layar draf: memegang state tiap blok dan menjadi sumber data
 * bersama, sehingga dasbor menghitung angkanya dari papan/tabel layar lain (memindah kartu mengubah
 * angka dasbor). Hidup di memori; menutup layar membuangnya, seed membuatnya ulang.
 */
class PrototypeSession(screens: List<DiscoveryScreenUi>) {
    private val blocks: Map<String, PlayableState> = screens.mapNotNull { s ->
        s.interactive?.let { screen -> build(screen)?.let { s.screenId to it } }
    }.toMap()

    private val sourceScreenByModule: Map<String, String> = screens
        .filter { blocks[it.screenId]?.rows != null }
        .groupBy { it.moduleId }
        .mapValues { (_, list) -> list.first().screenId }

    fun block(screenId: String): PlayableState? = blocks[screenId]

    /** Baris layar data pertama milik [moduleId]; null bila modul itu tak punya layar yang bisa dimainkan. */
    fun rowsOf(moduleId: String): List<PrototypeRow>? = sourceScreenByModule[moduleId]?.let { blocks[it]?.rows }

    private fun build(screen: InteractiveScreen): PlayableState? = when (screen.spec.screens.firstOrNull()?.widget) {
        WidgetKind.KANBAN -> InteractiveKanbanState(screen)
        WidgetKind.TABLE -> InteractiveTableState(screen)
        WidgetKind.CHECKLIST -> InteractiveChecklistState(screen)
        WidgetKind.DASHBOARD -> InteractiveDashboardState(screen) { moduleId -> rowsOf(moduleId) }
        else -> null
    }
}

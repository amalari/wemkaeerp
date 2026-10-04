package com.eventverse.app.presentation.discovery

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import com.eventverse.app.domain.discovery.WidgetKind
import com.eventverse.app.domain.discovery.brief.CaptureEntry
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
 *
 * Mendukung pembaruan spec (A4 - Chat Edit) dan log perubahan [captureLog] untuk brief (A5).
 */
class PrototypeSession(screens: List<DiscoveryScreenUi>) {
    private val blocks = mutableStateMapOf<String, PlayableState>()

    /**
     * Modul yang sedang dipakai (panel Paket & Harga). `null` = semua. Modul di luar himpunan ini tidak
     * menjadi sumber angka dasbor; nilainya state Compose, jadi dasbor menghitung ulang saat pilihan berubah.
     */
    var includedModuleIds: Set<String>? by mutableStateOf(null)

    /** Log riwayat operasi untuk ekspor brief (A5). */
    val captureLog = mutableStateListOf<CaptureEntry>()

    /** Riwayat spec per screenId untuk undo/redo (A4). */
    private val undoStackByScreen = mutableMapOf<String, MutableList<InteractiveScreen>>()

    private val sourceScreenByModule: MutableMap<String, String> = mutableMapOf()

    init {
        screens.forEach { s ->
            s.interactive?.let { screen ->
                build(screen)?.let { block ->
                    blocks[s.screenId] = block
                }
            }
        }
        recalculateSourceScreens(screens)
    }

    private fun recalculateSourceScreens(screens: List<DiscoveryScreenUi>) {
        sourceScreenByModule.clear()
        screens.filter { blocks[it.screenId]?.rows != null }
            .groupBy { it.moduleId }
            .forEach { (moduleId, list) ->
                sourceScreenByModule[moduleId] = list.first().screenId
            }
    }

    fun block(screenId: String): PlayableState? = blocks[screenId]

    /** Baris layar data pertama milik [moduleId]; null bila modul itu tak punya layar yang bisa dimainkan. */
    fun rowsOf(moduleId: String): List<PrototypeRow>? =
        if (includedModuleIds?.contains(moduleId) == false) null
        else sourceScreenByModule[moduleId]?.let { blocks[it]?.rows }

    /** Menyiarkan baris baru ke semua blok yang mengelola [entityId] yang sama. */
    fun broadcastRowCreated(entityId: String, newRow: PrototypeRow) {
        blocks.values.forEach { b ->
            when (b) {
                is InteractiveKanbanState -> if (b.entityId == entityId) b.insertRow(newRow)
                is InteractiveTableState -> if (b.entityId == entityId) b.insertRow(newRow)
                else -> {}
            }
        }
    }

    /** Menghapus baris dari semua blok yang mengelola [entityId] yang sama. */
    fun broadcastRowDeleted(entityId: String, rowId: String) {
        blocks.values.forEach { b ->
            when (b) {
                is InteractiveKanbanState -> if (b.entityId == entityId) b.delete(rowId)
                is InteractiveTableState -> if (b.entityId == entityId) b.delete(rowId)
                else -> {}
            }
        }
    }

    /**
     * Memperbarui spec layar setelah chat edit (A4). Menyimpan spec sebelumnya di stack undo.
     */
    fun updateScreenSpec(screenId: String, oldScreen: InteractiveScreen, updatedScreen: InteractiveScreen) {
        val stack = undoStackByScreen.getOrPut(screenId) { mutableListOf() }
        stack.add(oldScreen)

        build(updatedScreen)?.let { newBlock ->
            blocks[screenId] = newBlock
        }
    }

    /**
     * Undo pembaruan spec layar sebelumnya (A4).
     */
    fun undo(screenId: String): InteractiveScreen? {
        val stack = undoStackByScreen[screenId] ?: return null
        if (stack.isEmpty()) return null
        val previousScreen = stack.removeAt(stack.lastIndex)
        build(previousScreen)?.let { restoredBlock ->
            blocks[screenId] = restoredBlock
        }
        return previousScreen
    }

    fun canUndo(screenId: String): Boolean = undoStackByScreen[screenId]?.isNotEmpty() == true

    fun recordCapture(entries: List<CaptureEntry>) {
        captureLog.addAll(entries)
    }

    private fun build(screen: InteractiveScreen): PlayableState? = when (screen.spec.screens.firstOrNull()?.widget) {
        WidgetKind.KANBAN -> InteractiveKanbanState(screen)
        WidgetKind.TABLE -> InteractiveTableState(screen)
        WidgetKind.CHECKLIST -> InteractiveChecklistState(screen)
        WidgetKind.DASHBOARD -> InteractiveDashboardState(screen) { moduleId -> rowsOf(moduleId) }
        WidgetKind.FORM -> InteractiveFormState(screen) { entityId, newRow -> broadcastRowCreated(entityId, newRow) }
        else -> null
    }
}

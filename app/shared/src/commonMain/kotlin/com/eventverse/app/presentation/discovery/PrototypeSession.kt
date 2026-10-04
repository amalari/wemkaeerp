package com.eventverse.app.presentation.discovery

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.eventverse.app.domain.discovery.WidgetKind
import com.eventverse.app.domain.discovery.brief.CaptureEntry
import com.eventverse.app.domain.prototype.InteractiveScreen
import com.eventverse.app.domain.prototype.PrototypeRow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Blok yang bisa dimainkan; [rows] = baris datanya, null untuk blok tanpa data (dasbor). */
sealed interface PlayableState {
    val rows: List<PrototypeRow>?
    val phase: BlockDataPhase get() = BlockDataPhase.Idle
    val errorMessage: String? get() = null
    fun retry() {}
}

/**
 * Satu sesi prototype untuk seluruh layar draf (TRD-PLAT-003, butir A2):
 * - Memegang controller dan port data per blok sesuai [com.eventverse.app.domain.prototype.DataBinding].
 * - Menjadi sumber data bersama, sehingga dasbor menghitung angkanya dari baris layar lain.
 * - Mempertahankan port yang sama saat spec diubah lewat [updateScreenSpec] (undo tetap bekerja).
 * - Dibuat sekali per draf (ingat kunci remember berbasis draft.id).
 */
class PrototypeSession(
    screens: List<DiscoveryScreenUi>,
    val portFactory: BlockDataPortFactory = BlockDataPortFactory.defaultFactory,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main)
) {
    private val blocks = mutableStateMapOf<String, PlayableState>()
    private val controllers = mutableMapOf<String, BlockDataController>()

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
                val screenSpec = screen.spec.screens.firstOrNull()
                val entityId = screenSpec?.entityId
                val controller = if (entityId != null) {
                    val port = portFactory.createPort(screen, entityId)
                    val seedRows = screen.seed[entityId].orEmpty()
                    BlockDataController(port, screen.spec, entityId, seedRows).also { ctrl ->
                        controllers[s.screenId] = ctrl
                        // Bila seed kosong (misal API), muat di latar belakang
                        if (seedRows.isEmpty()) {
                            scope.launch { ctrl.load() }
                        }
                    }
                } else null

                build(screen, controller)?.let { block ->
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
        controllers.values.filter { it.entityId == entityId }.forEach { ctrl ->
            ctrl.insertRowLocally(newRow)
        }
    }

    /** Menghapus baris dari semua blok yang mengelola [entityId] yang sama. */
    fun broadcastRowDeleted(entityId: String, rowId: String) {
        controllers.values.filter { it.entityId == entityId }.forEach { ctrl ->
            ctrl.deleteRowLocally(rowId)
        }
    }

    /**
     * Memperbarui spec layar setelah chat edit (A4). Mempertahankan port yang sama agar baris data
     * dan koneksi tidak hilang.
     */
    fun updateScreenSpec(screenId: String, oldScreen: InteractiveScreen, updatedScreen: InteractiveScreen) {
        val stack = undoStackByScreen.getOrPut(screenId) { mutableListOf() }
        stack.add(oldScreen)

        val controller = controllers[screenId]
        if (controller != null) {
            controller.updateSpec(updatedScreen.spec)
            build(updatedScreen, controller)?.let { newBlock ->
                blocks[screenId] = newBlock
            }
        } else {
            build(updatedScreen, null)?.let { newBlock ->
                blocks[screenId] = newBlock
            }
        }
    }

    /**
     * Undo pembaruan spec layar sebelumnya (A4).
     */
    fun undo(screenId: String): InteractiveScreen? {
        val stack = undoStackByScreen[screenId] ?: return null
        if (stack.isEmpty()) return null
        val previousScreen = stack.removeAt(stack.lastIndex)
        val controller = controllers[screenId]
        if (controller != null) {
            controller.updateSpec(previousScreen.spec)
            build(previousScreen, controller)?.let { restoredBlock ->
                blocks[screenId] = restoredBlock
            }
        } else {
            build(previousScreen, null)?.let { restoredBlock ->
                blocks[screenId] = restoredBlock
            }
        }
        return previousScreen
    }

    fun canUndo(screenId: String): Boolean = undoStackByScreen[screenId]?.isNotEmpty() == true

    fun recordCapture(entries: List<CaptureEntry>) {
        captureLog.addAll(entries)
    }

    private fun build(screen: InteractiveScreen, controller: BlockDataController?): PlayableState? =
        when (screen.spec.screens.firstOrNull()?.widget) {
            WidgetKind.KANBAN -> controller?.let { InteractiveKanbanState(screen, it, scope) }
            WidgetKind.TABLE -> controller?.let { InteractiveTableState(screen, it, scope) }
            WidgetKind.CHECKLIST -> controller?.let { InteractiveChecklistState(screen, it, scope) }
            WidgetKind.DASHBOARD -> InteractiveDashboardState(screen) { moduleId -> rowsOf(moduleId) }
            WidgetKind.FORM -> controller?.let {
                InteractiveFormState(screen, it, scope) { entityId, newRow -> broadcastRowCreated(entityId, newRow) }
            }
            else -> null
        }
}

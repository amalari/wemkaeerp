package com.eventverse.app.presentation.discovery

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.eventverse.app.domain.prototype.InteractiveScreen
import com.eventverse.app.domain.prototype.KanbanConfig
import com.eventverse.app.domain.prototype.PrototypeReducer
import com.eventverse.app.domain.prototype.PrototypeRow

/**
 * State papan kanban prototype (TRD-PLAT-003). Hanya memegang state UI (seret, kolom di bawah
 * pointer, pesan) — aturan pindah kartu seluruhnya milik [PrototypeReducer] di core.
 * Store hidup di memori sesi dan hilang saat layar ditutup; seed membuatnya ulang.
 */
@Stable
class InteractiveKanbanState(screen: InteractiveScreen) {
    private val spec = screen.spec
    private val screenSpec = requireNotNull(spec.screens.firstOrNull()) { "Layar interaktif tanpa ScreenSpec" }
    val config: KanbanConfig = requireNotNull(screenSpec.kanban) { "Layar '${screenSpec.screenId}' bukan kanban" }
    private val entityId = screenSpec.entityId
    private val machine = spec.entity(entityId)?.stateMachine?.takeIf { it.field == config.groupField }

    var store by mutableStateOf(screen.newStore())
        private set
    /** Alasan penolakan terakhir (transisi terlarang), null bila aksi terakhir berhasil. */
    var message by mutableStateOf<String?>(null)
        private set
    var draggedId by mutableStateOf<String?>(null)
        private set
    var dragOffset by mutableStateOf(Offset.Zero)
        private set
    var hoverColumn by mutableStateOf<String?>(null)
        private set

    private val columnBounds = mutableMapOf<String, Rect>()
    private var pointerOrigin = Offset.Zero

    fun cards(column: String): List<PrototypeRow> =
        store.rowsOf(entityId).filter { it[config.groupField] == column }

    fun registerColumn(column: String, bounds: Rect) { columnBounds[column] = bounds }

    /** Kolom tujuan yang boleh untuk kartu [row] (dipakai menu "Pindah ke…"). */
    fun targetsFor(row: PrototypeRow): List<String> {
        val from = row[config.groupField]
        return config.columns.filter { it != from && (machine?.allows(from, it) ?: true) }
    }

    fun move(rowId: String, to: String) {
        PrototypeReducer.moveCard(spec, store, entityId, rowId, config.groupField, to)
            .onSuccess { store = it; message = null }
            .onFailure { message = it.message }
    }

    fun startDrag(rowId: String, cardOrigin: Offset, grab: Offset) {
        draggedId = rowId
        pointerOrigin = cardOrigin + grab
        dragOffset = Offset.Zero
        hoverColumn = null
    }

    fun dragBy(amount: Offset) {
        dragOffset += amount
        val pointer = pointerOrigin + dragOffset
        hoverColumn = columnBounds.entries.firstOrNull { it.value.contains(pointer) }?.key
    }

    fun endDrag() {
        val id = draggedId
        val target = hoverColumn
        if (id != null && target != null) move(id, target)
        cancelDrag()
    }

    fun cancelDrag() {
        draggedId = null
        dragOffset = Offset.Zero
        hoverColumn = null
    }
}

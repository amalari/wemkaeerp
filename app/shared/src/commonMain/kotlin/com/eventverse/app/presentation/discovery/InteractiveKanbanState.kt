package com.eventverse.app.presentation.discovery

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.eventverse.app.domain.prototype.InteractiveScreen
import com.eventverse.app.domain.prototype.KanbanConfig
import com.eventverse.app.domain.prototype.PrototypeRow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * State papan kanban prototype (TRD-PLAT-003, butir A2).
 * Berjalan di atas [BlockDataController] dan [com.eventverse.app.domain.prototype.BlockDataPort].
 * Memegang state UI interaksi papan (seret, kolom di bawah pointer); operasi data dikelola controller.
 */
@Stable
class InteractiveKanbanState(
    screen: InteractiveScreen,
    val controller: BlockDataController = createDefaultController(screen),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) : PlayableState {
    val spec get() = controller.spec
    private val screenSpec get() = requireNotNull(spec.screens.firstOrNull()) { "Layar interaktif tanpa ScreenSpec" }
    val config: KanbanConfig get() = requireNotNull(screenSpec.kanban) { "Layar '${screenSpec.screenId}' bukan kanban" }
    val entityId: String get() = controller.entityId
    private val machine get() = spec.entity(entityId)?.stateMachine?.takeIf { it.field == config.groupField }

    override val rows: List<PrototypeRow> get() = controller.rows
    override val phase: BlockDataPhase get() = controller.phase
    override val errorMessage: String? get() = controller.errorMessage

    /** Alasan penolakan terakhir (transisi terlarang), null bila aksi terakhir berhasil. */
    var message by mutableStateOf<String?>(null)
        private set
    var draggedId by mutableStateOf<String?>(null)
        private set
    var dragOffset by mutableStateOf(Offset.Zero)
        private set
    var hoverColumn by mutableStateOf<String?>(null)
        private set

    /** Kartu yang sedang dibuka di dialog detail (butir A5). */
    var selectedCardForDetail by mutableStateOf<PrototypeRow?>(null)

    private val columnBounds = mutableMapOf<String, Rect>()
    private var pointerOrigin = Offset.Zero

    init {
        if (controller.phase is BlockDataPhase.Loading && controller.rows.isEmpty()) {
            scope.launch { controller.load() }
        }
    }

    override fun retry() {
        scope.launch { controller.load() }
    }

    fun cards(column: String): List<PrototypeRow> =
        controller.rows.filter { it[config.groupField] == column }

    fun registerColumn(column: String, bounds: Rect) { columnBounds[column] = bounds }

    /** Kolom tujuan yang boleh untuk kartu [row] (dipakai menu "Pindah ke…"). */
    fun targetsFor(row: PrototypeRow): List<String> {
        val from = row[config.groupField]
        return config.columns.filter { it != from && (machine?.allows(from, it) ?: true) }
    }

    fun move(rowId: String, to: String) {
        scope.launch {
            controller.move(rowId, config.groupField, to)
                .onSuccess { message = null }
                .onFailure { message = it.message }
        }
    }

    fun delete(rowId: String) {
        controller.deleteRowLocally(rowId)
        scope.launch {
            controller.delete(rowId)
                .onSuccess { message = null }
                .onFailure { message = it.message }
        }
    }

    fun insertRow(row: PrototypeRow) {
        controller.insertRowLocally(row)
        scope.launch {
            controller.create(row.values)
                .onSuccess { message = null }
                .onFailure { message = it.message }
        }
    }

    fun updateRow(rowId: String, changes: Map<String, String>) {
        scope.launch {
            controller.update(rowId, changes)
                .onSuccess { message = null }
                .onFailure { message = it.message }
        }
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

    companion object {
        fun createDefaultController(screen: InteractiveScreen): BlockDataController {
            val screenSpec = requireNotNull(screen.spec.screens.firstOrNull()) { "Layar interaktif tanpa ScreenSpec" }
            val entityId = requireNotNull(screenSpec.entityId) { "Layar kanban tanpa entitas" }
            val port = BlockDataPortFactory.defaultFactory.createPort(screen, entityId)
            val seedRows = screen.seed[entityId].orEmpty()
            return BlockDataController(port, screen.spec, entityId, seedRows)
        }
    }
}

package com.eventverse.app.presentation.discovery

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.eventverse.app.domain.prototype.InteractiveScreen
import com.eventverse.app.domain.prototype.PrototypeRow
import com.eventverse.app.domain.prototype.TableConfig
import com.eventverse.app.domain.prototype.TableView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * State tabel prototype (TRD-PLAT-003, butir A2).
 * Berjalan di atas [BlockDataController] dan [com.eventverse.app.domain.prototype.BlockDataPort].
 * Sortir/filter dihitung [TableView]; perubahan data dikelola controller.
 */
@Stable
class InteractiveTableState(
    screen: InteractiveScreen,
    val controller: BlockDataController = createDefaultController(screen),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) : PlayableState {
    private val spec get() = controller.spec
    private val screenSpec get() = requireNotNull(spec.screens.firstOrNull()) { "Layar interaktif tanpa ScreenSpec" }
    val config: TableConfig get() = requireNotNull(screenSpec.table) { "Layar '${screenSpec.screenId}' bukan tabel" }
    val entityId: String get() = controller.entityId
    private val entity get() = requireNotNull(spec.entity(entityId)) { "Entitas '$entityId' tidak ada" }
    private val machine get() = entity.stateMachine?.takeIf { it.field == config.statusField }

    var query by mutableStateOf("")
    var sortColumn by mutableStateOf<String?>(null)
        private set
    var ascending by mutableStateOf(true)
        private set

    override val rows: List<PrototypeRow> get() = controller.rows
    override val phase: BlockDataPhase get() = controller.phase
    override val errorMessage: String? get() = controller.errorMessage
    val message: String? get() = controller.errorMessage

    val visibleRows: List<PrototypeRow>
        get() = TableView.apply(controller.rows, config.columns, query, sortColumn, ascending)

    init {
        if (controller.phase is BlockDataPhase.Loading && controller.rows.isEmpty()) {
            scope.launch { controller.load() }
        }
    }

    override fun retry() {
        scope.launch { controller.load() }
    }

    fun toggleSort(column: String) {
        if (sortColumn == column) ascending = !ascending else { sortColumn = column; ascending = true }
    }

    fun isStatus(column: String): Boolean = column == config.statusField

    /** Status tujuan yang boleh untuk [row] (menu ubah status). */
    fun statusTargets(row: PrototypeRow): List<String> {
        val field = config.statusField ?: return emptyList()
        val from = row[field]
        val options = entity.field(field)?.options.orEmpty()
        return options.filter { it != from && (machine?.allows(from, it) ?: true) }
    }

    fun setStatus(rowId: String, to: String) {
        val field = config.statusField ?: return
        scope.launch { controller.move(rowId, field, to) }
    }

    fun delete(rowId: String) {
        controller.deleteRowLocally(rowId)
        scope.launch { controller.delete(rowId) }
    }

    fun insertRow(row: PrototypeRow) {
        controller.insertRowLocally(row)
        scope.launch { controller.create(row.values) }
    }

    companion object {
        fun createDefaultController(screen: InteractiveScreen): BlockDataController {
            val screenSpec = requireNotNull(screen.spec.screens.firstOrNull()) { "Layar interaktif tanpa ScreenSpec" }
            val entityId = requireNotNull(screenSpec.entityId) { "Layar tabel tanpa entitas" }
            val port = BlockDataPortFactory.defaultFactory.createPort(screen, entityId)
            val seedRows = screen.seed[entityId].orEmpty()
            return BlockDataController(port, screen.spec, entityId, seedRows)
        }
    }
}

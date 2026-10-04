package com.eventverse.app.presentation.discovery

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.eventverse.app.domain.prototype.InteractiveScreen
import com.eventverse.app.domain.prototype.PrototypeReducer
import com.eventverse.app.domain.prototype.PrototypeRow
import com.eventverse.app.domain.prototype.TableConfig
import com.eventverse.app.domain.prototype.TableView

/**
 * State tabel prototype (TRD-PLAT-003). Sortir/filter hanya cara melihat (dihitung [TableView]);
 * perubahan status melewati [PrototypeReducer], jadi opsi dan transisi dijaga spec, bukan UI.
 */
@Stable
class InteractiveTableState(screen: InteractiveScreen) : PlayableState {
    private val spec = screen.spec
    private val screenSpec = requireNotNull(spec.screens.firstOrNull()) { "Layar interaktif tanpa ScreenSpec" }
    val config: TableConfig = requireNotNull(screenSpec.table) { "Layar '${screenSpec.screenId}' bukan tabel" }
    val entityId: String = requireNotNull(screenSpec.entityId) { "Layar tabel tanpa entitas" }
    private val entity = requireNotNull(spec.entity(entityId)) { "Entitas '$entityId' tidak ada" }
    private val machine = entity.stateMachine?.takeIf { it.field == config.statusField }

    var store by mutableStateOf(screen.newStore())
        private set
    var query by mutableStateOf("")
    var sortColumn by mutableStateOf<String?>(null)
        private set
    var ascending by mutableStateOf(true)
        private set
    var message by mutableStateOf<String?>(null)
        private set

    override val rows: List<PrototypeRow> get() = store.rowsOf(entityId)

    val visibleRows: List<PrototypeRow>
        get() = TableView.apply(store.rowsOf(entityId), config.columns, query, sortColumn, ascending)

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
        PrototypeReducer.moveCard(spec, store, entityId, rowId, field, to)
            .onSuccess { store = it; message = null }
            .onFailure { message = it.message }
    }

    fun delete(rowId: String) {
        com.eventverse.app.domain.prototype.PrototypeReducer.reduce(spec, store, com.eventverse.app.domain.prototype.PrototypeAction.Delete(entityId, rowId))
            .onSuccess { store = it; message = null }
            .onFailure { message = it.message }
    }

    fun insertRow(row: PrototypeRow) {
        com.eventverse.app.domain.prototype.PrototypeReducer.reduce(spec, store, com.eventverse.app.domain.prototype.PrototypeAction.Create(entityId, row))
            .onSuccess { store = it; message = null }
            .onFailure { message = it.message }
    }
}

package com.eventverse.app.presentation.discovery

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.eventverse.app.domain.prototype.ChecklistConfig
import com.eventverse.app.domain.prototype.InteractiveScreen
import com.eventverse.app.domain.prototype.PrototypeAction
import com.eventverse.app.domain.prototype.PrototypeReducer
import com.eventverse.app.domain.prototype.PrototypeRow

/** State checklist prototype: centang = `SetField` lewat reducer (nilai BOOL "ya"/"tidak" dijaga spec). */
@Stable
class InteractiveChecklistState(screen: InteractiveScreen) : PlayableState {
    private val spec = screen.spec
    private val screenSpec = requireNotNull(spec.screens.firstOrNull()) { "Layar interaktif tanpa ScreenSpec" }
    val config: ChecklistConfig = requireNotNull(screenSpec.checklist) { "Layar '${screenSpec.screenId}' bukan checklist" }
    private val entityId = requireNotNull(screenSpec.entityId) { "Layar checklist tanpa entitas" }

    var store by mutableStateOf(screen.newStore())
        private set
    var message by mutableStateOf<String?>(null)
        private set

    override val rows: List<PrototypeRow> get() = store.rowsOf(entityId)
    val doneCount: Int get() = rows.count { isDone(it) }

    fun isDone(row: PrototypeRow): Boolean = row[config.doneField] == "ya"

    fun toggle(row: PrototypeRow) {
        val next = if (isDone(row)) "tidak" else "ya"
        PrototypeReducer.reduce(spec, store, PrototypeAction.SetField(entityId, row.id, config.doneField, next))
            .onSuccess { store = it; message = null }
            .onFailure { message = it.message }
    }
}

package com.eventverse.app.presentation.discovery

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.eventverse.app.domain.prototype.ChecklistConfig
import com.eventverse.app.domain.prototype.InteractiveScreen
import com.eventverse.app.domain.prototype.PrototypeRow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * State checklist prototype (TRD-PLAT-003, butir A2).
 * Berjalan di atas [BlockDataController] dan [com.eventverse.app.domain.prototype.BlockDataPort].
 * Centang memicu pembaruan field BOOL ("ya"/"tidak") via controller.
 */
@Stable
class InteractiveChecklistState(
    screen: InteractiveScreen,
    val controller: BlockDataController = createDefaultController(screen),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) : PlayableState {
    val spec get() = controller.spec
    private val screenSpec get() = requireNotNull(spec.screens.firstOrNull()) { "Layar interaktif tanpa ScreenSpec" }
    val config: ChecklistConfig get() = requireNotNull(screenSpec.checklist) { "Layar '${screenSpec.screenId}' bukan checklist" }
    val entityId: String get() = controller.entityId

    override val rows: List<PrototypeRow> get() = controller.rows
    override val phase: BlockDataPhase get() = controller.phase
    override val errorMessage: String? get() = controller.errorMessage

    var message by mutableStateOf<String?>(null)
        private set

    init {
        if (controller.phase is BlockDataPhase.Loading && controller.rows.isEmpty()) {
            scope.launch { controller.load() }
        }
    }

    override fun retry() {
        scope.launch { controller.load() }
    }

    val doneCount: Int get() = rows.count { isDone(it) }

    fun isDone(row: PrototypeRow): Boolean = row[config.doneField] == "ya"

    fun toggle(row: PrototypeRow): Job = scope.launch {
        val next = if (isDone(row)) "tidak" else "ya"
        controller.move(row.id, config.doneField, next)
            .onSuccess { message = null }
            .onFailure { message = it.message }
    }

    companion object {
        fun createDefaultController(screen: InteractiveScreen): BlockDataController {
            val screenSpec = requireNotNull(screen.spec.screens.firstOrNull()) { "Layar interaktif tanpa ScreenSpec" }
            val entityId = requireNotNull(screenSpec.entityId) { "Layar checklist tanpa entitas" }
            val port = BlockDataPortFactory.defaultFactory.createPort(screen, entityId)
            val seedRows = screen.seed[entityId].orEmpty()
            return BlockDataController(port, screen.spec, entityId, seedRows)
        }
    }
}

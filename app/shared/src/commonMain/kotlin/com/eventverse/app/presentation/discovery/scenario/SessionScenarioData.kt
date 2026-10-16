package com.eventverse.app.presentation.discovery.scenario

import com.eventverse.app.domain.prototype.DataBinding
import com.eventverse.app.domain.prototype.EntitySpec
import com.eventverse.app.domain.prototype.PrototypeRow
import com.eventverse.app.presentation.discovery.DiscoveryScreenUi
import com.eventverse.app.presentation.discovery.InteractiveFormState
import com.eventverse.app.presentation.discovery.PrototypeSession

/**
 * Adaptor [ScenarioScreenData] di atas [PrototypeSession]: baris dibuat lewat pengontrol layar yang sama dengan form
 * prototype (pra-validasi reducer + siaran ke layar lain yang berbagi entitas). Layar ber-binding API **tidak** dilayani
 * (`entityOf` null): simulasi hanya menyentuh memori sesi dan tidak pernah memicu permintaan jaringan.
 */
class SessionScenarioData(
    private val session: PrototypeSession,
    screens: List<DiscoveryScreenUi>
) : ScenarioScreenData {
    private val byId = screens.associateBy { it.screenId }

    override fun entityOf(screenId: String): EntitySpec? {
        val screen = byId[screenId]?.interactive ?: return null
        if (screen.binding != DataBinding.Memory) return null
        val entityId = screen.spec.screens.firstOrNull()?.entityId ?: return null
        return screen.spec.entities.firstOrNull { it.id == entityId }
    }

    override fun rowsOf(screenId: String): List<PrototypeRow> = session.controllerOf(screenId)?.rows.orEmpty()

    override suspend fun createRow(screenId: String, values: Map<String, String>): Result<PrototypeRow> {
        val controller = session.controllerOf(screenId)
            ?: return Result.failure(IllegalStateException("Layar tidak punya data yang bisa diisi."))
        return controller.create(values).onSuccess { row -> session.broadcastRowCreated(controller.entityId, row) }
    }

    override fun isFormScreen(screenId: String): Boolean = session.block(screenId) is InteractiveFormState

    override fun prefillForm(screenId: String, values: Map<String, String>) {
        (session.block(screenId) as? InteractiveFormState)?.prefill(values)
    }
}

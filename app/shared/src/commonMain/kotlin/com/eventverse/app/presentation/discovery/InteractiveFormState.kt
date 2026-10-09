package com.eventverse.app.presentation.discovery

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.eventverse.app.domain.prototype.EntitySpec
import com.eventverse.app.domain.prototype.FieldSpec
import com.eventverse.app.domain.prototype.FieldType
import com.eventverse.app.domain.prototype.FormConfig
import com.eventverse.app.domain.prototype.InteractiveScreen
import com.eventverse.app.domain.prototype.PrototypeAction
import com.eventverse.app.domain.prototype.PrototypeReducer
import com.eventverse.app.domain.prototype.PrototypeRow
import com.eventverse.app.domain.prototype.PrototypeStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * State formulir penambahan data prototype (TRD-PLAT-003, butir A2).
 * Berjalan di atas [BlockDataController] dan [com.eventverse.app.domain.prototype.BlockDataPort].
 * Form terikat ke [entityId] yang sama dengan tabel/papan kanban sumbernya.
 */
@Stable
class InteractiveFormState(
    val screen: InteractiveScreen,
    val controller: BlockDataController = createDefaultController(screen),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    val onRowCreated: ((entityId: String, newRow: PrototypeRow) -> Unit)? = null
) : PlayableState {
    val spec get() = controller.spec
    private val screenSpec get() = requireNotNull(spec.screens.firstOrNull()) { "Layar interaktif tanpa ScreenSpec" }
    val config: FormConfig get() = requireNotNull(screenSpec.form) { "Layar '${screenSpec.screenId}' bukan form" }
    val entityId: String get() = controller.entityId
    val entity: EntitySpec get() = requireNotNull(spec.entity(entityId)) { "Entitas '$entityId' tidak ada" }

    override val rows: List<PrototypeRow> get() = controller.rows
    override val phase: BlockDataPhase get() = controller.phase
    override val errorMessage: String? get() = controller.errorMessage

    val formValues = mutableStateMapOf<String, String>()
    var message by mutableStateOf<String?>(null)
        private set
    var successMessage by mutableStateOf<String?>(null)
        private set

    init {
        resetForm()
        if (controller.phase is BlockDataPhase.Loading && controller.rows.isEmpty()) {
            scope.launch { controller.load() }
        }
    }

    override fun retry() {
        scope.launch { controller.load() }
    }

    fun fields(): List<FieldSpec> = config.fields.mapNotNull { entity.field(it) }

    fun setFieldValue(fieldKey: String, value: String) {
        formValues[fieldKey] = value
        message = null
        successMessage = null
    }

    fun submit(): Boolean {
        // Pra-validasi
        val dummyRow = PrototypeRow("temp", formValues.toMap())
        val dummyStore = PrototypeStore.seeded(spec, mapOf(entityId to controller.rows))
        val prevalResult = PrototypeReducer.reduce(spec, dummyStore, PrototypeAction.Create(entityId, dummyRow))
        if (prevalResult.isFailure) {
            message = prevalResult.exceptionOrNull()?.message
            successMessage = null
            return false
        }

        val valuesToSave = formValues.toMap()
        val optimisticRow = PrototypeRow("$entityId-${controller.rows.size + 1}", valuesToSave)

        message = null
        successMessage = "Data '${entity.label}' berhasil ditambahkan!"
        controller.insertRowLocally(optimisticRow)
        onRowCreated?.invoke(entityId, optimisticRow)
        resetForm()

        scope.launch {
            controller.create(valuesToSave).onFailure { err ->
                message = err.message
                successMessage = null
            }
        }
        return true
    }

    fun resetForm() {
        formValues.clear()
        fields().forEach { f ->
            formValues[f.key] = when (f.type) {
                FieldType.BOOL -> "tidak"
                FieldType.ENUM -> f.options.firstOrNull().orEmpty()
                FieldType.TEXT, FieldType.LONG_TEXT, FieldType.NUMBER, FieldType.DATE, FieldType.RELATION -> ""
            }
        }
    }

    companion object {
        fun createDefaultController(screen: InteractiveScreen): BlockDataController {
            val screenSpec = requireNotNull(screen.spec.screens.firstOrNull()) { "Layar interaktif tanpa ScreenSpec" }
            val entityId = requireNotNull(screenSpec.entityId) { "Layar form tanpa entitas" }
            val port = BlockDataPortFactory.defaultFactory.createPort(screen, entityId)
            val seedRows = screen.seed[entityId].orEmpty()
            return BlockDataController(port, screen.spec, entityId, seedRows)
        }
    }
}

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

/**
 * State formulir penambahan data prototype (TRD-PLAT-003, butir A2).
 * Form terikat ke [entityId] yang sama dengan tabel/papan kanban sumbernya.
 * Submit memanggil [PrototypeReducer.reduce] dengan [PrototypeAction.Create],
 * dan bila berhasil memicu [onRowCreated] sehingga baris baru langsung muncul
 * di tabel/kanban pada sesi bersama [PrototypeSession].
 */
@Stable
class InteractiveFormState(
    val screen: InteractiveScreen,
    val onRowCreated: ((entityId: String, newRow: PrototypeRow) -> Unit)? = null
) : PlayableState {
    val spec = screen.spec
    private val screenSpec = requireNotNull(spec.screens.firstOrNull()) { "Layar interaktif tanpa ScreenSpec" }
    val config: FormConfig = requireNotNull(screenSpec.form) { "Layar '${screenSpec.screenId}' bukan form" }
    val entityId: String = requireNotNull(screenSpec.entityId) { "Layar form tanpa entitas" }
    val entity: EntitySpec = requireNotNull(spec.entity(entityId)) { "Entitas '$entityId' tidak ada" }

    var store by mutableStateOf(screen.newStore())
        private set

    val formValues = mutableStateMapOf<String, String>()
    var message by mutableStateOf<String?>(null)
        private set
    var successMessage by mutableStateOf<String?>(null)
        private set

    private var autoIdCounter = 1

    init {
        resetForm()
    }

    override val rows: List<PrototypeRow> get() = store.rowsOf(entityId)

    fun fields(): List<FieldSpec> = config.fields.mapNotNull { entity.field(it) }

    fun setFieldValue(fieldKey: String, value: String) {
        formValues[fieldKey] = value
        message = null
        successMessage = null
    }

    fun submit(): Boolean {
        val existingIds = store.rowsOf(entityId).map { it.id }.toSet()
        var newId = "$entityId-${store.rowsOf(entityId).size + autoIdCounter}"
        while (newId in existingIds) {
            autoIdCounter++
            newId = "$entityId-${store.rowsOf(entityId).size + autoIdCounter}"
        }
        autoIdCounter++

        val newRow = PrototypeRow(newId, formValues.toMap())
        val result = PrototypeReducer.reduce(spec, store, PrototypeAction.Create(entityId, newRow))

        return result.fold(
            onSuccess = { updatedStore ->
                store = updatedStore
                message = null
                successMessage = "Data '${entity.label}' berhasil ditambahkan!"
                onRowCreated?.invoke(entityId, newRow)
                resetForm()
                true
            },
            onFailure = { error ->
                message = error.message
                successMessage = null
                false
            }
        )
    }

    fun resetForm() {
        formValues.clear()
        fields().forEach { f ->
            formValues[f.key] = when (f.type) {
                FieldType.BOOL -> "tidak"
                FieldType.ENUM -> f.options.firstOrNull().orEmpty()
                else -> ""
            }
        }
    }
}

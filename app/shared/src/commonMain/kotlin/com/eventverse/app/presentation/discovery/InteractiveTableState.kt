package com.eventverse.app.presentation.discovery

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.eventverse.app.domain.prototype.DataBinding
import com.eventverse.app.domain.prototype.FieldSpec
import com.eventverse.app.domain.prototype.FieldType
import com.eventverse.app.domain.prototype.InteractiveScreen
import com.eventverse.app.domain.prototype.PrototypeRow
import com.eventverse.app.domain.prototype.TableConfig
import com.eventverse.app.domain.prototype.TableView
import com.eventverse.app.presentation.discovery.fields.FileFieldOps
import com.eventverse.app.presentation.discovery.fields.fieldFileOpsOrNull
import com.eventverse.app.presentation.relation.RelationFieldUi
import com.eventverse.app.presentation.relation.relationFieldControllerOrNull
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

    /** Pesan sekali-jalan untuk aksi sel di luar controller (mis. gagal unduh berkas, C8). */
    var transientMessage by mutableStateOf<String?>(null)

    /** Binding API layar (bila ada) — sumber moduleCode untuk aksi field FILE (C8 Track C). */
    private val apiBinding: DataBinding.Api? = screen.binding as? DataBinding.Api

    /**
     * Aksi unggah/unduh berkas untuk field FILE pada satu baris (C8, TRD-FIELD-002 Track C);
     * `null` bila layar berbinding memori (demo tanpa server — unggah tidak mungkin).
     */
    fun fileFieldOps(recordId: String): FileFieldOps? = fieldFileOpsOrNull(apiBinding, recordId)

    /** Cache label rujukan (id target → label) untuk kolom RELATION, diisi pemilih saat opsi dimuat. */
    val relationLabels = mutableStateMapOf<String, String>()

    private val relationControllers = mutableMapOf<String, RelationFieldUi>()

    /**
     * Kontroler pemilih rujukan untuk kolom RELATION (C7, TRD-FIELD-001 Track C); `null` bila
     * kolom bukan RELATION atau layar berbinding memori (demo tanpa server, opsi tak bisa dimuat).
     */
    fun relationField(column: String): RelationFieldUi? {
        val field = entity.field(column) ?: return null
        if (field.type != FieldType.RELATION) return null
        relationControllers[column]?.let { return it }
        val created = relationFieldControllerOrNull(apiBinding, field.target.orEmpty(), scope) ?: return null
        relationControllers[column] = created
        return created
    }

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

    fun fieldSpec(column: String): FieldSpec? = entity.field(column)

    /** Teks header kolom: label field; fallback ke key bila field tak ada. Pengurutan tetap memakai key. */
    fun columnLabel(column: String): String = fieldSpec(column)?.label ?: column

    // --- Inline Creation (A4) ---
    var isCreatingInline by mutableStateOf(false)
        private set
    val inlineValues = mutableStateMapOf<String, String>()
    var inlineErrorMessage by mutableStateOf<String?>(null)
        private set

    fun startInlineCreate() {
        inlineValues.clear()
        config.columns.forEach { col ->
            val f = entity.field(col)
            inlineValues[col] = when (f?.type) {
                FieldType.BOOL -> "tidak"
                FieldType.ENUM -> f.options.firstOrNull().orEmpty()
                // A0 (TRD-FIELD-003): MULTI_SELECT default kosong (belum ada pilihan); chip = Track C.
                // C6: TIME default kosong (belum diisi) — pola DATE.
                // A0 (penyatuan kosakata): USER_REF default kosong — id pengguna tidak dikarang.
                FieldType.TEXT, FieldType.LONG_TEXT, FieldType.NUMBER, FieldType.DATE, FieldType.TIME, FieldType.MULTI_SELECT, FieldType.RELATION, FieldType.FILE, FieldType.USER_REF, null -> ""
            }
        }
        inlineErrorMessage = null
        isCreatingInline = true
    }

    fun cancelInlineCreate() {
        isCreatingInline = false
        inlineErrorMessage = null
        inlineValues.clear()
    }

    fun setInlineValue(column: String, value: String) {
        inlineValues[column] = value
        inlineErrorMessage = null
    }

    fun submitInlineCreate() {
        val valuesToSave = inlineValues.toMap()
        scope.launch {
            controller.create(valuesToSave).fold(
                onSuccess = {
                    cancelInlineCreate()
                },
                onFailure = { err ->
                    inlineErrorMessage = err.message ?: "Gagal menambah data"
                }
            )
        }
    }

    // --- Inline Cell Editing (A4) ---
    var editingCell by mutableStateOf<Pair<String, String>?>(null)
        private set
    var editingValue by mutableStateOf("")
    var cellErrorMessage by mutableStateOf<String?>(null)
        private set

    fun isCellEditable(column: String): Boolean =
        config.editableFields.contains(column) && !isStatus(column)

    fun startCellEdit(rowId: String, column: String, currentValue: String) {
        editingCell = rowId to column
        editingValue = currentValue
        cellErrorMessage = null
    }

    fun cancelCellEdit() {
        editingCell = null
        editingValue = ""
        cellErrorMessage = null
    }

    fun submitCellEdit(rowId: String, column: String) {
        val valueToSave = editingValue
        scope.launch {
            controller.update(rowId, mapOf(column to valueToSave)).fold(
                onSuccess = {
                    cancelCellEdit()
                },
                onFailure = { err ->
                    cellErrorMessage = err.message ?: "Gagal mengubah nilai sel"
                }
            )
        }
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

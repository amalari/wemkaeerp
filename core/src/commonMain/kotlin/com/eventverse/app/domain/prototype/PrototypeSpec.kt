package com.eventverse.app.domain.prototype

import com.eventverse.app.domain.discovery.WidgetKind

/**
 * Konfigurasi papan kanban: kartu dikelompokkan menurut field ENUM [groupField]; [columns] memuat
 * **semua** kolom termasuk yang kosong, sehingga kartu bisa dijatuhkan ke kolom yang belum berisi.
 */
data class KanbanConfig(
    val groupField: String,
    val columns: List<String>,
    val titleField: String,
    val detailFields: List<String> = emptyList()
)

/**
 * Konfigurasi tabel: [columns] urutan kolom tampil; [statusField] (opsional, ENUM) = kolom yang
 * statusnya bisa diubah langsung di baris lewat reducer.
 */
data class TableConfig(val columns: List<String>, val statusField: String? = null)

/** Daftar periksa: [labelField] teks butir, [doneField] field BOOL ("ya"/"tidak") yang dicentang. */
data class ChecklistConfig(val labelField: String, val doneField: String)

/**
 * Satu layar prototype. Layar data (kanban/tabel/checklist) **terikat** ke [entityId]; dasbor tidak
 * punya entitas sendiri — angkanya dihitung dari layar lain lewat [DashboardConfig].
 */
data class ScreenSpec(
    val screenId: String,
    val title: String,
    val widget: WidgetKind,
    val entityId: String?,
    val kanban: KanbanConfig? = null,
    val table: TableConfig? = null,
    val checklist: ChecklistConfig? = null,
    val dashboard: DashboardConfig? = null
) {
    init {
        require(screenId.isNotBlank() && title.isNotBlank()) { "ScreenSpec tanpa id/judul" }
        fun need(kind: WidgetKind, present: Boolean, label: String) =
            require((widget == kind) == present) { "Layar '$screenId': konfigurasi $label wajib ada tepat untuk widget ${kind.code}" }
        need(WidgetKind.KANBAN, kanban != null, "kanban")
        need(WidgetKind.TABLE, table != null, "tabel")
        need(WidgetKind.CHECKLIST, checklist != null, "checklist")
        need(WidgetKind.DASHBOARD, dashboard != null, "dasbor")
        require((widget == WidgetKind.DASHBOARD) == (entityId == null)) {
            "Layar '$screenId': entitas wajib untuk layar data, dan tidak boleh ada untuk dasbor"
        }
    }
}

/** Spec utuh prototype. Validasi fail-closed di konstruktor: spec tak koheren tidak pernah terbentuk. */
data class PrototypeSpec(val entities: List<EntitySpec>, val screens: List<ScreenSpec>) {
    init {
        require(entities.map { it.id }.distinct().size == entities.size) { "Entitas kembar" }
        screens.forEach { screen ->
            val id = screen.screenId
            val entity = screen.entityId?.let { eid ->
                requireNotNull(entities.firstOrNull { it.id == eid }) { "Layar '$id' menunjuk entitas '$eid' yang tidak ada" }
            }
            if (entity == null) return@forEach
            screen.table?.let { t ->
                require(t.columns.isNotEmpty() && t.columns.all { entity.field(it) != null }) { "Layar '$id': kolom tabel di luar field entitas" }
                t.statusField?.let { sf ->
                    require(entity.field(sf)?.type == FieldType.ENUM && sf in t.columns) {
                        "Layar '$id': kolom status '$sf' wajib ENUM dan tampil di tabel"
                    }
                }
            }
            screen.checklist?.let { c ->
                require(entity.field(c.labelField) != null) { "Layar '$id': field butir '${c.labelField}' tidak ada" }
                require(entity.field(c.doneField)?.type == FieldType.BOOL) { "Layar '$id': field centang '${c.doneField}' wajib BOOL" }
            }
            screen.kanban?.let { k ->
                val group = requireNotNull(entity.field(k.groupField)) { "Layar '$id': field kelompok '${k.groupField}' tidak ada" }
                require(group.type == FieldType.ENUM) { "Field kelompok '${k.groupField}' wajib ENUM" }
                require(k.columns.isNotEmpty() && k.columns.all { it in group.options }) { "Layar '$id': kolom di luar opsi '${k.groupField}'" }
                require(entity.field(k.titleField) != null && k.detailFields.all { entity.field(it) != null }) {
                    "Layar '$id': field judul/detail tidak ada di entitas"
                }
            }
        }
    }

    fun entity(id: String): EntitySpec? = entities.firstOrNull { it.id == id }
}

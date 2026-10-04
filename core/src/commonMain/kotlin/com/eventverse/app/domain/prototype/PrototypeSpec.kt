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

/** Satu layar prototype yang **terikat** ke entitas — bukan lagi baris contoh lepas. */
data class ScreenSpec(
    val screenId: String,
    val title: String,
    val widget: WidgetKind,
    val entityId: String,
    val kanban: KanbanConfig? = null,
    val table: TableConfig? = null
) {
    init {
        require(screenId.isNotBlank() && title.isNotBlank()) { "ScreenSpec tanpa id/judul" }
        require((widget == WidgetKind.KANBAN) == (kanban != null)) {
            "Layar '$screenId': konfigurasi kanban wajib ada tepat untuk widget KANBAN"
        }
        require((widget == WidgetKind.TABLE) == (table != null)) {
            "Layar '$screenId': konfigurasi tabel wajib ada tepat untuk widget TABLE"
        }
    }
}

/** Spec utuh prototype. Validasi fail-closed di konstruktor: spec tak koheren tidak pernah terbentuk. */
data class PrototypeSpec(val entities: List<EntitySpec>, val screens: List<ScreenSpec>) {
    init {
        require(entities.map { it.id }.distinct().size == entities.size) { "Entitas kembar" }
        screens.forEach { screen ->
            val entity = requireNotNull(entities.firstOrNull { it.id == screen.entityId }) {
                "Layar '${screen.screenId}' menunjuk entitas '${screen.entityId}' yang tidak ada"
            }
            screen.table?.let { t ->
                require(t.columns.isNotEmpty() && t.columns.all { entity.field(it) != null }) {
                    "Layar '${screen.screenId}': kolom tabel di luar field entitas"
                }
                t.statusField?.let { sf ->
                    require(entity.field(sf)?.type == FieldType.ENUM && sf in t.columns) {
                        "Layar '${screen.screenId}': kolom status '$sf' wajib ENUM dan tampil di tabel"
                    }
                }
            }
            screen.kanban?.let { k ->
                val group = requireNotNull(entity.field(k.groupField)) {
                    "Layar '${screen.screenId}': field kelompok '${k.groupField}' tidak ada"
                }
                require(group.type == FieldType.ENUM) { "Field kelompok '${k.groupField}' wajib ENUM" }
                require(k.columns.isNotEmpty() && k.columns.all { it in group.options }) {
                    "Layar '${screen.screenId}': kolom di luar opsi '${k.groupField}'"
                }
                require(entity.field(k.titleField) != null && k.detailFields.all { entity.field(it) != null }) {
                    "Layar '${screen.screenId}': field judul/detail tidak ada di entitas"
                }
            }
        }
    }

    fun entity(id: String): EntitySpec? = entities.firstOrNull { it.id == id }
}

package com.eventverse.app.domain.prototype

import com.eventverse.app.domain.discovery.WidgetKind

/**
 * Gaya tampil satu elemen kartu kanban — kosakata **tertutup milik sistem** (Uji Variabilitas:
 * renderer harus bisa menggambar tiap gaya di semua vertikal; menambah gaya = mengubah renderer,
 * jadi ia memang kode). *Nilai* field yang ditampilkan tetap data tenant/entitas.
 */
enum class CardStyle { TITLE, TEXT, BADGE, DATE, NUMBER, FLAG }

/** Satu elemen bertipe pada kartu kanban (kontrak v2, plan induk §3.3); [field] wajib milik entitas. */
data class CardElement(val field: String, val style: CardStyle = CardStyle.TEXT)

/**
 * Metadata satu kolom kanban (kontrak v2). [tintHex] adalah **warna data tenant** — pengecualian
 * sah design-system Kontrak 1 (seperti `colorHex` domain lain), bukan keputusan styling renderer.
 * [wipLimit] batas antrean kolom; wajib positif bila diisi.
 */
data class ColumnMeta(val tintHex: Long? = null, val wipLimit: Int? = null) {
    init {
        require(wipLimit == null || wipLimit > 0) { "wipLimit kolom wajib positif, dapat $wipLimit" }
    }
}

/**
 * Konfigurasi papan kanban: kartu dikelompokkan menurut field ENUM [groupField]; [columns] memuat
 * **semua** kolom termasuk yang kosong, sehingga kartu bisa dijatuhkan ke kolom yang belum berisi.
 * Kunci v2 semuanya opsional — kosong/null = perilaku lama.
 */
data class KanbanConfig(
    val groupField: String,
    val columns: List<String>,
    val titleField: String,
    val detailFields: List<String> = emptyList(),
    /** Elemen bertipe kartu (v2); kosong = perilaku lama (titleField + detailFields). */
    val card: List<CardElement> = emptyList(),
    /** Metadata per kolom (v2): warna data tenant & batas WIP; kunci = kolom di [columns]. */
    val columnMeta: Map<String, ColumnMeta> = emptyMap(),
    /** Form saat kartu diketuk (v2); null = perilaku lama (tanpa form detail). */
    val detailForm: FormConfig? = null
)

/**
 * Konfigurasi tabel: [columns] urutan kolom tampil; [statusField] (opsional, ENUM) = kolom yang
 * statusnya bisa diubah langsung di baris lewat reducer. Kunci v2 opsional — false/kosong =
 * perilaku lama.
 */
data class TableConfig(
    val columns: List<String>,
    val statusField: String? = null,
    /** Baris isian + tombol Tambah langsung di tabel (v2). */
    val inlineCreate: Boolean = false,
    /** Sel yang bisa diubah lewat ketuk (v2); status bermesin tidak boleh masuk (divalidasi spec). */
    val editableFields: List<String> = emptyList()
)


/** Daftar periksa: [labelField] teks butir, [doneField] field BOOL ("ya"/"tidak") yang dicentang. */
data class ChecklistConfig(val labelField: String, val doneField: String)

/**
 * Formulir tambah data (kontrak v1): [fields] = urutan field entitas yang ditampilkan; [submitLabel] teks tombol.
 * Layar form terikat ke entitas yang **sama** dengan tabel/papan sumbernya, sehingga baris baru langsung
 * tampil di sana lewat sesi prototype.
 */
data class FormConfig(val fields: List<String>, val submitLabel: String = "Simpan") {
    init {
        require(fields.isNotEmpty() && fields.distinct().size == fields.size) { "Form wajib punya field unik" }
        require(submitLabel.isNotBlank()) { "Label tombol form kosong" }
    }
}

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
    val dashboard: DashboardConfig? = null,
    val form: FormConfig? = null
) {
    init {
        require(screenId.isNotBlank() && title.isNotBlank()) { "ScreenSpec tanpa id/judul" }
        fun need(kind: WidgetKind, present: Boolean, label: String) =
            require((widget == kind) == present) { "Layar '$screenId': konfigurasi $label wajib ada tepat untuk widget ${kind.code}" }
        need(WidgetKind.KANBAN, kanban != null, "kanban")
        need(WidgetKind.TABLE, table != null, "tabel")
        need(WidgetKind.CHECKLIST, checklist != null, "checklist")
        need(WidgetKind.DASHBOARD, dashboard != null, "dasbor")
        need(WidgetKind.FORM, form != null, "form")
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
                // Kontrak v2 (plan induk §3.3): field sunting wajib milik entitas; status yang
                // diatur mesin tidak boleh disunting sebagai sel teks (status lewat pilihan/drag).
                require(t.editableFields.distinct().size == t.editableFields.size) { "Layar '$id': field sunting kembar" }
                require(t.editableFields.all { entity.field(it) != null }) { "Layar '$id': field sunting di luar field entitas" }
                entity.stateMachine?.let { sm ->
                    require(sm.field !in t.editableFields) {
                        "Layar '$id': status '${sm.field}' diubah lewat pilihan status/drag, bukan sel teks"
                    }
                }
            }
            screen.form?.let { f ->
                require(f.fields.all { entity.field(it) != null }) { "Layar '$id': field form di luar field entitas" }
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
                // Kontrak v2 (plan induk §3.3): elemen kartu unik dan wajib milik entitas; form
                // detail satu entitas yang sama.
                require(k.card.map { it.field }.distinct().size == k.card.size) { "Layar '$id': elemen kartu kembar" }
                require(k.card.all { entity.field(it.field) != null }) { "Layar '$id': elemen kartu menunjuk field yang tidak ada" }
                k.detailForm?.let { f ->
                    require(f.fields.all { entity.field(it) != null }) { "Layar '$id': field form detail di luar field entitas" }
                }
            }
        }
    }

    fun entity(id: String): EntitySpec? = entities.firstOrNull { it.id == id }
}

package com.eventverse.app.domain.prototype

import com.eventverse.app.domain.discovery.WidgetKind

/**
 * Pelaksana [SpecOp.ChangeWidget] (SP-B5): mengganti jenis tampilan satu layar data antara **tabel** dan **papan
 * kanban**, atau daftar periksa → tabel. Entitas, seed, dan layar lain **tidak berubah** — yang diganti hanya
 * konfigurasi tampilan layar itu; spec hasil tetap dibangun lewat konstruktor [PrototypeSpec] (validasi tak dilonggarkan).
 *
 * Aturan kelayakan (ditolak bermesej, tidak ada tebakan):
 *  - target hanya [WidgetKind.TABLE] / [WidgetKind.KANBAN]; layar sumber harus layar data berentitas
 *    (tabel, papan, atau daftar periksa → tabel). Dasbor, formulir, cetak ditolak;
 *  - tabel **selalu mungkin** untuk layar data;
 *  - papan butuh tepat satu field pilihan status (ENUM) yang jelas — dari mesin status, status tabel/papan
 *    sebelumnya, atau satu-satunya ENUM; nol atau ambigu ditolak; butuh juga ≥ 1 field lain sebagai judul kartu;
 *  - seed yang tak punya nilai status akan hilang dari papan, jadi ditolak.
 * Perilaku jenis lain (mis. hints pack, `inlineCreate`, `editableFields`, `detailForm`) tidak dibawa: itu watak
 * tampilan asal, bukan data entitas.
 */
internal object ChangeWidgetOp {

    fun apply(screen: InteractiveScreen, op: SpecOp.ChangeWidget): InteractiveScreen {
        val target = when (op.widget) {
            WidgetKind.TABLE, WidgetKind.KANBAN -> op.widget
            else -> throw IllegalArgumentException("Belum bisa mengubah tampilan menjadi ${op.widget.code}; yang dikenal: tabel dan papan (kanban).")
        }
        val current = requireNotNull(screen.spec.screens.firstOrNull { it.screenId == op.screenId }) { "Layar '${op.screenId}' tidak ada." }
        require(current.widget != target) { "Layar '${current.title}' sudah berupa ${name(target)}." }
        val entityId = requireNotNull(current.entityId) { "Layar '${current.title}' (${current.widget.code}) tidak punya data yang bisa ditampilkan sebagai ${name(target)}." }
        require(current.widget in SOURCES) { "Layar '${current.title}' berjenis ${current.widget.code}; hanya tabel, papan, atau daftar periksa yang bisa diubah." }
        val entity = requireNotNull(screen.spec.entity(entityId)) { "Entitas '$entityId' tidak ada." }
        val changed = when (target) {
            WidgetKind.TABLE -> toTable(current, entity)
            else -> toKanban(screen, current, entity)
        }
        return screen.copy(spec = PrototypeSpec(screen.spec.entities, screen.spec.screens.map { if (it.screenId == op.screenId) changed else it }))
    }

    private val SOURCES = setOf(WidgetKind.TABLE, WidgetKind.KANBAN, WidgetKind.CHECKLIST)

    private fun name(w: WidgetKind) = if (w == WidgetKind.KANBAN) "papan (kanban)" else "tabel"

    private fun toTable(s: ScreenSpec, entity: EntitySpec): ScreenSpec {
        val k = s.kanban
        val columns = when {
            k != null -> (k.card.map { it.field }.ifEmpty { listOf(k.titleField) + k.detailFields } + k.groupField).distinct()
            s.checklist != null -> listOf(s.checklist.labelField, s.checklist.doneField)
            else -> entity.fields.map { it.key }
        }
        return ScreenSpec(s.screenId, s.title, WidgetKind.TABLE, s.entityId, table = TableConfig(columns, statusField = k?.groupField))
    }

    private fun toKanban(screen: InteractiveScreen, s: ScreenSpec, entity: EntitySpec): ScreenSpec {
        val enums = entity.fields.filter { it.type == FieldType.ENUM }
        require(enums.isNotEmpty()) { "'${s.title}' belum punya field pilihan status (ENUM), jadi belum bisa dijadikan papan. Tambah status dulu." }
        val hinted = listOfNotNull(entity.stateMachine?.field, s.table?.statusField)
        val group = when {
            enums.size == 1 -> enums.single()
            else -> enums.firstOrNull { it.key in hinted }
                ?: throw IllegalArgumentException("'${s.title}' punya ${enums.size} pilihan status (${enums.joinToString { it.label }}); sebutkan mana yang jadi kolom papan.")
        }
        val shown = (s.table?.columns ?: entity.fields.map { it.key }).filter { it != group.key && entity.field(it) != null }
            .ifEmpty { entity.fields.map { it.key }.filter { it != group.key } }
        require(shown.isNotEmpty()) { "'${s.title}' hanya punya field status; papan butuh minimal satu field lain sebagai judul kartu." }
        val rows = screen.seed[entity.id].orEmpty()
        require(rows.all { !it.values[group.key].isNullOrEmpty() }) {
            "Ada data tanpa '${group.label}' yang akan hilang dari papan; isi dulu statusnya."
        }
        val title = shown.first()
        val card = shown.map { key ->
            CardElement(key, if (key == title) CardStyle.TITLE else when (entity.field(key)?.type) {
                FieldType.DATE -> CardStyle.DATE
                FieldType.NUMBER -> CardStyle.NUMBER
                FieldType.BOOL -> CardStyle.FLAG
                // C7: id rujukan tampil sebagai teks pola di kartu (label resolusinya menyusul).
                // C8: nama berkas serupa — kartu menampilkan teks, bukan pratinjau.
                // A0 (TRD-FIELD-003): MULTI_SELECT tampil sebagai teks; gaya daftar label ditetapkan Track C.
                // C6: TIME tampil sebagai teks `JJ:MM` — belum ada gaya kartu khusus jam (menyusul bila ada pemakaian ketiga).
                // A0 (penyatuan kosakata): USER_REF tampil sebagai teks id pengguna di kartu (pola RELATION).
                FieldType.TEXT, FieldType.LONG_TEXT, FieldType.TIME, FieldType.ENUM, FieldType.MULTI_SELECT, FieldType.RELATION, FieldType.FILE, FieldType.USER_REF, null -> CardStyle.TEXT
            })
        }
        return ScreenSpec(
            s.screenId, s.title, WidgetKind.KANBAN, s.entityId,
            kanban = KanbanConfig(group.key, group.options, title, shown.drop(1), card = card)
        )
    }
}

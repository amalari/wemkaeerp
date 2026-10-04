package com.eventverse.app.domain.prototype

import com.eventverse.app.domain.discovery.WidgetKind

/**
 * Menurunkan [InteractiveScreen] kanban dari baris contoh bergaya lama (kunci `Kolom` + kunci lain =
 * isi kartu). Adaptor ini yang menjaga draf lama tetap hidup (TRD-PLAT-003 F7): bentuk baris tidak
 * berubah, hanya kini ditafsirkan sebagai entitas. Mengembalikan null bila baris tak bisa dibentuk
 * jadi papan sah — pemanggil jatuh ke gambar statis, tidak menebak.
 */
object InteractiveScreenFactory {
    const val ENTITY_ID = "item"
    const val GROUP_FIELD = "Kolom"
    const val LABEL_FIELD = "Butir"
    const val DONE_FIELD = "Selesai"

    /**
     * Dua mode (B2.1, usulan jalur C — lihat KDoc [KanbanHints]):
     *  - **Legacy**: baris contoh wajib berkunci `"Kolom"` (atau [KanbanHints.groupField]) dan tak
     *    kosong; field entitas diturunkan dari baris, semuanya TEXT. Perilaku garment tak berubah.
     *  - **Dideklarasikan**: [KanbanHints.fields] diisi — entitas dari petunjuk (status ENUM, tanggal
     *    DATE, …), `titleField` = elemen kartu bergaya TITLE (atau deklarasi pertama), dan baris
     *    contoh **opsional** (layar berbinding Api memuat datanya dari server).
     *
     * Petunjuk tak koheren tetap **ditolak** (null → gambar statis), bukan diabaikan.
     */
    fun kanban(screenId: String, title: String, rows: List<Map<String, String>>, hints: KanbanHints? = null): InteractiveScreen? {
        val declared = hints?.fields.orEmpty()
        val declaredMode = declared.isNotEmpty()
        if (declaredMode && hints?.groupField.isNullOrBlank()) return null
        val groupField = hints?.groupField?.takeIf { it.isNotBlank() } ?: GROUP_FIELD
        if (!declaredMode && (rows.isEmpty() || rows.any { groupField !in it })) return null
        val columns = hints?.columns ?: rows.map { it.getValue(groupField) }.distinct()
        val textKeys = rows.flatMap { it.keys }.filter { it != groupField }.distinct()
        if (!declaredMode && textKeys.isEmpty()) return null
        return runCatching {
            // Petunjuk kaya wajib koheren: elemen kartu menunjuk field papan (termasuk field
            // kelompoknya, mis. lencana status), metadata menunjuk kolom. Tak koheren ditolak
            // (null → gambar statis), bukan diabaikan.
            val declaredKeys = declared.map { it.key }.toSet()
            hints?.card?.forEach { el ->
                require(el.field == groupField || el.field in declaredKeys || (!declaredMode && el.field in textKeys)) {
                    "Elemen kartu menunjuk field '${el.field}' yang tidak ada di papan"
                }
            }
            hints?.columnMeta?.forEach { (col, _) ->
                require(col in columns) { "Metadata kolom '$col' tidak ada di kolom papan" }
            }
            declared.forEach { f ->
                require(f.key != groupField) { "FieldHint '${f.key}' tidak boleh menimpa field kelompok papan (type-nya dipaksa ENUM)" }
            }
            val group = FieldSpec(groupField, hints?.groupLabel ?: groupField, FieldType.ENUM, columns)
            val entityFields = if (declaredMode) declared.map { it.toFieldSpec() } else textKeys.map { FieldSpec(it, it, FieldType.TEXT) }
            val titleField = if (declaredMode) {
                hints?.card?.firstOrNull { it.style == CardStyle.TITLE }?.field ?: declared.first().key
            } else {
                textKeys.first()
            }
            val detailFields = if (declaredMode) entityFields.map { it.key }.filter { it != titleField } else textKeys.drop(1)
            val entity = EntitySpec(
                ENTITY_ID, title,
                listOf(group) + entityFields,
                hints?.transitions?.takeIf { it.isNotEmpty() }?.let { StateMachine(groupField, it) }
            )
            val kanban = KanbanConfig(
                groupField, columns, titleField, detailFields,
                card = hints?.card.orEmpty(),
                columnMeta = hints?.columnMeta.orEmpty(),
                detailForm = hints?.detailForm
            )
            val spec = PrototypeSpec(
                listOf(entity),
                listOf(ScreenSpec(screenId, title, WidgetKind.KANBAN, ENTITY_ID, kanban))
            )
            val seed = mapOf(ENTITY_ID to rows.mapIndexed { i, r -> PrototypeRow("$screenId-${i + 1}", r) })
            InteractiveScreen(spec, seed).also { it.newStore() }
        }.getOrNull()
    }

    /**
     * Tabel bisa dimainkan dari baris contoh berkunci sama (kunci baris pertama = kolom, urutan
     * dipertahankan). Null bila baris tak seragam atau nilai status di luar opsi — jatuh ke gambar statis.
     */
    fun table(screenId: String, title: String, rows: List<Map<String, String>>, hints: TableHints? = null): InteractiveScreen? {
        val columns = rows.firstOrNull()?.keys?.toList().orEmpty()
        if (columns.isEmpty() || rows.any { it.keys != rows.first().keys }) return null
        return runCatching {
            // Tipe field dari petunjuk (B2): kuncinya wajib kolom baris contoh, dan nilai seed wajib
            // lolos tipe — tak koheren ditolak (null → gambar statis), bukan diabaikan. Tanpa
            // hints/fields: perilaku lama (semua TEXT kecuali kolom status).
            hints?.fields?.forEach { f -> require(f.key in columns) { "FieldHint '${f.key}' tidak ada di kolom baris contoh" } }
            val hintsByColumn = hints?.fields.orEmpty().associateBy { it.key }
            val entity = EntitySpec(
                ENTITY_ID, title,
                columns.map { c ->
                    when {
                        hintsByColumn[c] != null -> hintsByColumn.getValue(c).toFieldSpec()
                        c == hints?.statusColumn -> FieldSpec(c, c, FieldType.ENUM, hints?.options.orEmpty())
                        else -> FieldSpec(c, c, FieldType.TEXT)
                    }
                },
                hints?.takeIf { it.transitions.isNotEmpty() }?.let { StateMachine(it.statusColumn, it.transitions) }
            )
            val spec = PrototypeSpec(
                listOf(entity),
                listOf(
                    ScreenSpec(
                        screenId, title, WidgetKind.TABLE, ENTITY_ID,
                        table = TableConfig(
                            columns, hints?.statusColumn,
                            inlineCreate = hints?.inlineCreate ?: false,
                            editableFields = hints?.editableFields.orEmpty()
                        )
                    )
                )
            )
            val seed = mapOf(ENTITY_ID to rows.mapIndexed { i, r -> PrototypeRow("$screenId-${i + 1}", r) })
            InteractiveScreen(spec, seed).also { it.newStore() }
        }.getOrNull()
    }

    /** Daftar periksa dari baris `Butir` + `Selesai` ("ya"/"tidak"); null bila bentuk baris tak cocok. */
    fun checklist(screenId: String, title: String, rows: List<Map<String, String>>): InteractiveScreen? {
        if (rows.isEmpty() || rows.any { LABEL_FIELD !in it || DONE_FIELD !in it }) return null
        return runCatching {
            val entity = EntitySpec(ENTITY_ID, title, listOf(FieldSpec(LABEL_FIELD, LABEL_FIELD, FieldType.TEXT), FieldSpec(DONE_FIELD, DONE_FIELD, FieldType.BOOL)))
            val spec = PrototypeSpec(
                listOf(entity),
                listOf(ScreenSpec(screenId, title, WidgetKind.CHECKLIST, ENTITY_ID, checklist = ChecklistConfig(LABEL_FIELD, DONE_FIELD)))
            )
            val seed = mapOf(ENTITY_ID to rows.mapIndexed { i, r -> PrototypeRow("$screenId-${i + 1}", mapOf(LABEL_FIELD to r.getValue(LABEL_FIELD), DONE_FIELD to r.getValue(DONE_FIELD))) })
            InteractiveScreen(spec, seed).also { it.newStore() }
        }.getOrNull()
    }

    /**
     * Dasbor dari baris satu-pasang (label → angka). Hanya "hidup" bila [hints] mengikat setidaknya
     * satu ubin ke layar lain; dasbor yang semuanya statis dikembalikan null (digambar statis).
     */
    fun dashboard(screenId: String, title: String, rows: List<Map<String, String>>, hints: DashboardHints?): InteractiveScreen? {
        if (hints == null || hints.counts.isEmpty() || rows.isEmpty() || rows.any { it.size != 1 }) return null
        return runCatching {
            val tiles = rows.map { r ->
                val (label, value) = r.entries.single()
                TileSpec(label, value, hints.counts[label])
            }
            if (tiles.none { it.count != null }) return null
            InteractiveScreen(PrototypeSpec(emptyList(), listOf(ScreenSpec(screenId, title, WidgetKind.DASHBOARD, null, dashboard = DashboardConfig(tiles)))), emptyMap())
        }.getOrNull()
    }

    /**
     * Formulir tambah data dari petunjuk pack (butir B2). Entitas form memakai id [ENTITY_ID] yang
     * **sama** dengan layar sumber satu modulnya — penghubung form → layar sumber adalah pasangan
     * *(moduleId, entityId)*: `PrototypeScreen.moduleId` form dan sumber sama, dan spec keduanya
     * menunjuk entityId sama, sehingga dalam satu `PrototypeSession` baris hasil `Create` lewat
     * reducer langsung tampil di tabel/papan sumbernya. Tidak ada seed — form tidak menampilkan
     * baris. Null bila petunjuknya tak bisa dibentuk jadi spec sah (digambar statis, bukan ditebak).
     */
    fun form(screenId: String, title: String, hints: FormHints): InteractiveScreen? {
        if (hints.fields.isEmpty() || hints.fields.map { it.trim() }.distinct().size != hints.fields.size) return null
        return runCatching {
            val fields = hints.fields.map { key ->
                val options = hints.options[key]
                if (options != null) FieldSpec(key, key, FieldType.ENUM, options, required = key in hints.required)
                else FieldSpec(key, key, FieldType.TEXT, required = key in hints.required)
            }
            val spec = PrototypeSpec(
                listOf(EntitySpec(ENTITY_ID, title, fields)),
                listOf(ScreenSpec(screenId, title, WidgetKind.FORM, ENTITY_ID, form = FormConfig(hints.fields, hints.submitLabel ?: "Simpan")))
            )
            InteractiveScreen(spec, emptyMap()).also { it.newStore() }
        }.getOrNull()
    }
}

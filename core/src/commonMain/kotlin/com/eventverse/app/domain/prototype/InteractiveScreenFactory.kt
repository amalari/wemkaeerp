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

    fun kanban(screenId: String, title: String, rows: List<Map<String, String>>, hints: KanbanHints? = null): InteractiveScreen? {
        if (rows.isEmpty() || rows.any { GROUP_FIELD !in it }) return null
        val columns = hints?.columns ?: rows.map { it.getValue(GROUP_FIELD) }.distinct()
        val textKeys = rows.flatMap { it.keys }.filter { it != GROUP_FIELD }.distinct()
        if (textKeys.isEmpty()) return null
        return runCatching {
            val group = FieldSpec(GROUP_FIELD, hints?.groupLabel ?: GROUP_FIELD, FieldType.ENUM, columns)
            val entity = EntitySpec(
                ENTITY_ID, title,
                listOf(group) + textKeys.map { FieldSpec(it, it, FieldType.TEXT) },
                hints?.transitions?.takeIf { it.isNotEmpty() }?.let { StateMachine(GROUP_FIELD, it) }
            )
            val spec = PrototypeSpec(
                listOf(entity),
                listOf(ScreenSpec(screenId, title, WidgetKind.KANBAN, ENTITY_ID, KanbanConfig(GROUP_FIELD, columns, textKeys.first(), textKeys.drop(1))))
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
            val entity = EntitySpec(
                ENTITY_ID, title,
                columns.map { c ->
                    if (c == hints?.statusColumn) FieldSpec(c, c, FieldType.ENUM, hints.options) else FieldSpec(c, c, FieldType.TEXT)
                },
                hints?.takeIf { it.transitions.isNotEmpty() }?.let { StateMachine(it.statusColumn, it.transitions) }
            )
            val spec = PrototypeSpec(
                listOf(entity),
                listOf(ScreenSpec(screenId, title, WidgetKind.TABLE, ENTITY_ID, table = TableConfig(columns, hints?.statusColumn)))
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
}

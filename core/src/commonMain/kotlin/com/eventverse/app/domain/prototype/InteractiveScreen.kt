package com.eventverse.app.domain.prototype

import com.eventverse.app.domain.discovery.WidgetKind

/** Layar yang bisa dimainkan: spec + isi awal (seed). Dibuat ulang deterministik dari data pack. */
data class InteractiveScreen(val spec: PrototypeSpec, val seed: Map<String, List<PrototypeRow>>) {
    fun newStore(): PrototypeStore = PrototypeStore.seeded(spec, seed)
}

/**
 * Petunjuk perilaku papan dari pack: urutan kolom (termasuk yang kosong) dan transisi yang boleh.
 * Tanpa [transitions] kartu bebas pindah ke kolom mana pun.
 */
data class KanbanHints(val columns: List<String>, val transitions: Map<String, Set<String>> = emptyMap())

/**
 * Menurunkan [InteractiveScreen] kanban dari baris contoh bergaya lama (kunci `Kolom` + kunci lain =
 * isi kartu). Adaptor ini yang menjaga draf lama tetap hidup (TRD-PLAT-003 F7): bentuk baris tidak
 * berubah, hanya kini ditafsirkan sebagai entitas. Mengembalikan null bila baris tak bisa dibentuk
 * jadi papan sah — pemanggil jatuh ke gambar statis, tidak menebak.
 */
object InteractiveScreenFactory {
    const val ENTITY_ID = "item"
    const val GROUP_FIELD = "Kolom"

    fun kanban(screenId: String, title: String, rows: List<Map<String, String>>, hints: KanbanHints? = null): InteractiveScreen? {
        if (rows.isEmpty() || rows.any { GROUP_FIELD !in it }) return null
        val columns = hints?.columns ?: rows.map { it.getValue(GROUP_FIELD) }.distinct()
        val textKeys = rows.flatMap { it.keys }.filter { it != GROUP_FIELD }.distinct()
        if (textKeys.isEmpty()) return null
        return runCatching {
            val group = FieldSpec(GROUP_FIELD, GROUP_FIELD, FieldType.ENUM, columns)
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
}

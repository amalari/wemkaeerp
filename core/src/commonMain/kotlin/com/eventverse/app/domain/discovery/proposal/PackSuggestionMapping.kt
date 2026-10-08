package com.eventverse.app.domain.discovery.proposal

import com.eventverse.app.domain.discovery.WidgetKind
import com.eventverse.app.domain.pack.ScreenSuggestion
import com.eventverse.app.domain.prototype.CardStyle
import com.eventverse.app.domain.prototype.FieldHint
import com.eventverse.app.domain.prototype.FieldType
import com.eventverse.app.domain.prototype.InteractiveScreenFactory
import com.eventverse.app.domain.prototype.TileSpec

/**
 * Penerjemah [ScreenSuggestion] → [ScreenProposal], satu fungsi per bentuk widget. Cermin
 * `InteractiveScreenFactory` (bentuk baris contoh dan petunjuk yang sama) tetapi menghasilkan **data usulan**,
 * bukan spec: entitas diberi id modulnya (bukan `item` bersama), sehingga banyak layar satu draf tak bertabrakan.
 *
 * Bentuk yang tak bisa diterjemahkan (mis. tabel tanpa baris contoh, status di luar kolom) **melempar** dengan
 * pesan jelas — dibungkus `Result.failure` oleh `PackScreenProposer`, tidak diam-diam menjadi layar kosong.
 * Petunjuk form (`formHints`) menjadi layar **kedua** berbagi entitas dengan layar sumbernya (id sama,
 * field = himpunan bagian), persis kontrak form lama.
 */
internal object PackSuggestionMapping {

    fun map(s: ScreenSuggestion): List<ScreenProposal> {
        val rationale = requireNotNull(s.rationale) { "Usulan layar ${s.moduleId.value} belum punya rationale; pack wajib menuliskannya" }
        val primary = when (s.widget) {
            WidgetKind.KANBAN -> kanban(s, rationale)
            WidgetKind.TABLE -> table(s, rationale)
            WidgetKind.CHECKLIST -> checklist(s, rationale)
            WidgetKind.DASHBOARD -> dashboard(s, rationale)
            WidgetKind.PRINT -> print(s, rationale)
            WidgetKind.FORM -> form(s, rationale, "default-${s.moduleId.value}", s.title)
            WidgetKind.CUSTOM_SCREEN -> base(s, rationale).copy(entity = null, view = ViewProposal.None)
        }
        val extraForm = s.formHints?.takeIf { s.widget != WidgetKind.FORM }
            ?.let { form(s, rationale, "${s.moduleId.value}-form", "${s.title} — formulir") }
        return listOfNotNull(primary, extraForm)
    }

    private fun base(s: ScreenSuggestion, rationale: String, screenId: String = "default-${s.moduleId.value}", title: String = s.title) =
        ScreenProposal(
            screenId, s.moduleId, title, s.widget, rationale,
            entity = null, view = ViewProposal.None, binding = s.dataBinding
        )

    private fun FieldHint.toProposal() = FieldProposal(key, key, type, required, options, format, currencyCode, withTime, validation)
    private fun text(key: String) = FieldProposal(key, key, FieldType.TEXT)
    private fun Map<String, Set<String>>.asLists() = mapValues { (_, v) -> v.toList() }

    private fun kanban(s: ScreenSuggestion, rationale: String): ScreenProposal {
        val hints = s.kanbanHints
        val declared = hints?.fields.orEmpty()
        val group = hints?.groupField?.takeIf { it.isNotBlank() } ?: InteractiveScreenFactory.GROUP_FIELD
        require(declared.isEmpty() || !hints?.groupField.isNullOrBlank()) { "Papan ${s.moduleId.value} mendeklarasikan fields tanpa groupField" }
        require(declared.isNotEmpty() || (s.sampleRows.isNotEmpty() && s.sampleRows.all { group in it })) {
            "Papan ${s.moduleId.value} butuh baris contoh berkunci '$group' (atau deklarasi fields)"
        }
        val columns = hints?.columns ?: s.sampleRows.map { it.getValue(group) }.distinct()
        val others = if (declared.isNotEmpty()) declared.map { it.toProposal() }
        else s.sampleRows.flatMap { it.keys }.filter { it != group }.distinct().map(::text)
        val entity = EntityProposal(
            s.moduleId.value, s.title,
            listOf(FieldProposal(group, hints?.groupLabel ?: group, FieldType.ENUM, options = columns)) + others,
            statusField = group, transitions = hints?.transitions.orEmpty().asLists()
        )
        val view = ViewProposal.Kanban(
            card = hints?.card.orEmpty(), columnMeta = hints?.columnMeta.orEmpty(),
            detailFormFields = hints?.detailForm?.fields.orEmpty(),
            detailFormSubmitLabel = hints?.detailForm?.submitLabel ?: "Simpan"
        )
        return base(s, rationale).copy(entity = entity, view = view, seed = s.sampleRows)
    }

    private fun table(s: ScreenSuggestion, rationale: String): ScreenProposal {
        val hints = s.tableHints
        val columns = s.sampleRows.firstOrNull()?.keys?.toList().orEmpty()
        require(columns.isNotEmpty() && s.sampleRows.all { it.keys == s.sampleRows.first().keys }) {
            "Tabel ${s.moduleId.value} butuh baris contoh berkunci seragam"
        }
        require(hints == null || hints.statusColumn in columns) { "Kolom status '${hints?.statusColumn}' tidak ada di kolom tabel ${s.moduleId.value}" }
        val byColumn = hints?.fields.orEmpty().associateBy { it.key }
        hints?.fields?.forEach { require(it.key in columns) { "FieldHint '${it.key}' tidak ada di kolom tabel ${s.moduleId.value}" } }
        val fields = columns.map { c ->
            when {
                byColumn[c] != null -> byColumn.getValue(c).toProposal()
                c == hints?.statusColumn -> FieldProposal(c, c, FieldType.ENUM, options = hints.options)
                else -> text(c)
            }
        }
        val entity = EntityProposal(s.moduleId.value, s.title, fields, hints?.statusColumn, hints?.transitions.orEmpty().asLists())
        val view = ViewProposal.Table(columns, hints?.inlineCreate ?: false, hints?.editableFields.orEmpty())
        return base(s, rationale).copy(entity = entity, view = view, seed = s.sampleRows)
    }

    private fun checklist(s: ScreenSuggestion, rationale: String): ScreenProposal {
        val (label, done) = InteractiveScreenFactory.LABEL_FIELD to InteractiveScreenFactory.DONE_FIELD
        require(s.sampleRows.isNotEmpty() && s.sampleRows.all { label in it && done in it }) {
            "Daftar periksa ${s.moduleId.value} butuh baris contoh berkunci '$label' dan '$done'"
        }
        val entity = EntityProposal(s.moduleId.value, s.title, listOf(text(label), FieldProposal(done, done, FieldType.BOOL)))
        return base(s, rationale).copy(
            entity = entity, view = ViewProposal.Checklist(label, done),
            seed = s.sampleRows.map { mapOf(label to it.getValue(label), done to it.getValue(done)) }
        )
    }

    private fun dashboard(s: ScreenSuggestion, rationale: String): ScreenProposal {
        require(s.sampleRows.isNotEmpty() && s.sampleRows.all { it.size == 1 }) { "Dasbor ${s.moduleId.value} butuh baris satu-pasang (label → angka)" }
        val counts = s.dashboardHints?.counts.orEmpty()
        val tiles = s.sampleRows.map { r -> r.entries.single().let { (label, value) -> TileSpec(label, value, counts[label]) } }
        return base(s, rationale).copy(view = ViewProposal.Dashboard(tiles))
    }

    private fun print(s: ScreenSuggestion, rationale: String): ScreenProposal {
        require(s.sampleRows.isNotEmpty()) { "Cetak ${s.moduleId.value} butuh baris contoh" }
        val keys = s.sampleRows.flatMap { it.keys }.distinct()
        return base(s, rationale).copy(
            entity = EntityProposal(s.moduleId.value, s.title, keys.map(::text)),
            view = ViewProposal.Print(keys), seed = s.sampleRows
        )
    }

    private fun form(s: ScreenSuggestion, rationale: String, screenId: String, title: String): ScreenProposal {
        val hints = requireNotNull(s.formHints) { "Usulan FORM ${s.moduleId.value} butuh formHints" }
        val fields = hints.fields.map { key ->
            val options = hints.options[key]
            FieldProposal(key, key, if (options != null) FieldType.ENUM else FieldType.TEXT, key in hints.required, options.orEmpty())
        }
        return base(s, rationale, screenId, title).copy(
            widget = WidgetKind.FORM,
            entity = EntityProposal(s.moduleId.value, title, fields),
            view = ViewProposal.Form(hints.fields, hints.submitLabel ?: "Simpan")
        )
    }
}

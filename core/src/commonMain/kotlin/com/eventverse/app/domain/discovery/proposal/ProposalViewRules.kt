package com.eventverse.app.domain.discovery.proposal

import com.eventverse.app.domain.discovery.WidgetKind
import com.eventverse.app.domain.prototype.FieldType

/** Aturan tampilan: varian cocok dengan widget, setiap rujukan field ada, kolom kanban = opsi status. */
internal object ProposalViewRules {

    fun check(p: ScreenProposal, sink: IssueSink, packModuleIds: Set<String>?) {
        val view = p.view
        if (!matches(p.widget, view)) {
            sink.add(".view", "view ${view::class.simpleName} tidak cocok dengan widget ${p.widget.code}; harapannya ${expected(p.widget)}")
            return
        }
        val entity = p.entity
        val fields = entity?.fields?.associateBy { it.key }.orEmpty()
        fun exists(sub: String, key: String, what: String): Boolean =
            (key in fields).also { if (!it) sink.add(sub, "$what '$key' tidak ada di field entity${entity?.let { e -> " '${e.id}'" }.orEmpty()}") }
        fun existAll(sub: String, keys: List<String>, what: String) =
            keys.forEachIndexed { i, k -> exists("$sub[$i]", k, what) }
        fun unique(sub: String, keys: List<String>, what: String) {
            if (keys.distinct().size != keys.size) sink.add(sub, "$what memuat field kembar")
        }

        when (view) {
            is ViewProposal.Kanban -> checkKanban(view, entity, sink, ::exists, ::existAll)
            is ViewProposal.Table -> {
                if (view.columns.isEmpty()) sink.add(".view.columns", "Tabel wajib punya minimal 1 kolom")
                existAll(".view.columns", view.columns, "Kolom")
                unique(".view.columns", view.columns, "Kolom tabel")
                existAll(".view.editableFields", view.editableFields, "Field sunting")
                unique(".view.editableFields", view.editableFields, "Field sunting")
                val machineField = entity?.takeIf { it.transitions.isNotEmpty() }?.statusField
                view.editableFields.forEachIndexed { i, k ->
                    if (k == machineField) sink.add(".view.editableFields[$i]", "Status '$k' diatur transisi; ubahnya lewat pilihan status, bukan sel teks")
                }
            }
            is ViewProposal.Form -> {
                if (view.fields.isEmpty()) sink.add(".view.fields", "Form wajib punya minimal 1 field")
                existAll(".view.fields", view.fields, "Field form")
                unique(".view.fields", view.fields, "Form")
                sink.text(".view.submitLabel", view.submitLabel, "submitLabel")
                entity?.fields?.filter { it.required && it.key !in view.fields }?.forEach { f ->
                    sink.add(".view.fields", "Field wajib '${f.key}' harus ada di form")
                }
            }
            is ViewProposal.Checklist -> {
                exists(".view.labelField", view.labelField, "labelField")
                if (exists(".view.doneField", view.doneField, "doneField") && fields.getValue(view.doneField).type != FieldType.BOOL) {
                    sink.add(".view.doneField", "doneField '${view.doneField}' wajib field BOOL, bukan ${fields.getValue(view.doneField).type.name}")
                }
            }
            is ViewProposal.Dashboard -> checkDashboard(view, sink, packModuleIds)
            is ViewProposal.Print -> {
                if (view.fields.isNotEmpty() && entity == null) sink.add(".entity", "Print dengan fields wajib punya entity yang mendefinisikannya")
                if (entity != null) existAll(".view.fields", view.fields, "Field cetak")
            }
            ViewProposal.None -> Unit
        }
    }

    private fun checkKanban(
        view: ViewProposal.Kanban, entity: EntityProposal?, sink: IssueSink,
        exists: (String, String, String) -> Boolean, existAll: (String, List<String>, String) -> Unit
    ) {
        val statusField = entity?.statusField
        if (entity != null && statusField == null) sink.add(".entity.statusField", "Kanban wajib punya statusField (field ENUM yang menjadi kolom papan)")
        val columns = entity?.fields?.firstOrNull { it.key == statusField }?.options.orEmpty()
        view.card.forEachIndexed { i, el -> exists(".view.card[$i].field", el.field, "Elemen kartu") }
        if (view.card.map { it.field }.distinct().size != view.card.size) sink.add(".view.card", "Elemen kartu memuat field kembar")
        existAll(".view.detailFormFields", view.detailFormFields, "Field form detail")
        sink.text(".view.detailFormSubmitLabel", view.detailFormSubmitLabel, "detailFormSubmitLabel")
        view.columnMeta.keys.forEach { col ->
            if (col !in columns) sink.add(".view.columnMeta.$col", "Metadata untuk kolom '$col' yang bukan pilihan status (${columns.joinToString()})")
        }
    }

    private fun checkDashboard(view: ViewProposal.Dashboard, sink: IssueSink, packModuleIds: Set<String>?) {
        if (view.tiles.isEmpty()) sink.add(".view.tiles", "Dasbor wajib punya minimal 1 ubin")
        if (view.tiles.size > ProposalLimits.TILES) sink.add(".view.tiles", "Terlalu banyak ubin (${view.tiles.size}); maksimum ${ProposalLimits.TILES}")
        view.tiles.forEachIndexed { i, t ->
            val at = ".view.tiles[$i]"
            sink.text("$at.label", t.label, "Label ubin")
            t.value?.let { sink.text("$at.value", it, "Nilai ubin", required = false) }
            val moduleId = t.count?.moduleId
            if (moduleId != null && packModuleIds != null && moduleId !in packModuleIds) {
                sink.add("$at.count.moduleId", "Ubin menghitung modul '$moduleId' yang tidak ada di pack")
            }
        }
    }

    private fun matches(widget: WidgetKind, view: ViewProposal): Boolean = when (widget) {
        WidgetKind.KANBAN -> view is ViewProposal.Kanban
        WidgetKind.TABLE -> view is ViewProposal.Table
        WidgetKind.FORM -> view is ViewProposal.Form
        WidgetKind.CHECKLIST -> view is ViewProposal.Checklist
        WidgetKind.DASHBOARD -> view is ViewProposal.Dashboard
        WidgetKind.PRINT -> view is ViewProposal.Print
        WidgetKind.CUSTOM_SCREEN -> view is ViewProposal.None
    }

    private fun expected(widget: WidgetKind): String = when (widget) {
        WidgetKind.KANBAN -> "Kanban"
        WidgetKind.TABLE -> "Table"
        WidgetKind.FORM -> "Form"
        WidgetKind.CHECKLIST -> "Checklist"
        WidgetKind.DASHBOARD -> "Dashboard"
        WidgetKind.PRINT -> "Print"
        WidgetKind.CUSTOM_SCREEN -> "None"
    }
}

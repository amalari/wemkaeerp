package com.eventverse.app.shared.discovery

import com.eventverse.app.domain.discovery.WidgetKind
import com.eventverse.app.domain.discovery.proposal.SkeletonBlock
import com.eventverse.app.domain.discovery.proposal.SkeletonHint
import com.eventverse.app.domain.discovery.proposal.SkeletonWidth
import com.eventverse.app.domain.discovery.proposal.ViewProposal
import com.eventverse.app.domain.prototype.TileSpec
import com.eventverse.app.shared.json.JsonValue
import com.eventverse.app.shared.json.jsonArrayOf
import com.eventverse.app.shared.json.jsonObjectOf
import com.eventverse.app.shared.json.jsonOf
import com.eventverse.app.shared.pack.InteractiveScreenCodec

/**
 * Kawat [ViewProposal]. Tanpa kunci jenis: varian ditentukan `widget` induknya (satu kebenaran), jadi tidak ada
 * jalan menulis `view` yang tak cocok dengan widget. Bentuk kartu/metadata kolom memakai ulang kawat
 * [InteractiveScreenCodec] — satu bentuk untuk layar interaktif dan usulan.
 */
internal object ViewProposalCodec {

    fun encode(v: ViewProposal): JsonValue = when (v) {
        is ViewProposal.Kanban -> jsonObjectOf(
            "card" to InteractiveScreenCodec.encodeCardElements(v.card),
            "columnMeta" to InteractiveScreenCodec.encodeColumnMetaMap(v.columnMeta),
            "detailFormFields" to jsonArrayOf(v.detailFormFields.map(::jsonOf)),
            "detailFormSubmitLabel" to jsonOf(v.detailFormSubmitLabel)
        )
        is ViewProposal.Table -> jsonObjectOf(
            "columns" to jsonArrayOf(v.columns.map(::jsonOf)), "inlineCreate" to jsonOf(v.inlineCreate),
            "editableFields" to jsonArrayOf(v.editableFields.map(::jsonOf))
        )
        is ViewProposal.Form -> jsonObjectOf("fields" to jsonArrayOf(v.fields.map(::jsonOf)), "submitLabel" to jsonOf(v.submitLabel))
        is ViewProposal.Checklist -> jsonObjectOf("labelField" to jsonOf(v.labelField), "doneField" to jsonOf(v.doneField))
        is ViewProposal.Dashboard -> jsonObjectOf("tiles" to jsonArrayOf(v.tiles.map { t ->
            jsonObjectOf(
                "label" to jsonOf(t.label), "value" to jsonOf(t.value),
                "count" to (t.count?.let(InteractiveScreenCodec::encodeCount) ?: JsonValue.Null)
            )
        }))
        is ViewProposal.Print -> jsonObjectOf("fields" to jsonArrayOf(v.fields.map(::jsonOf)))
        ViewProposal.None -> JsonValue.Null
        is ViewProposal.Skeleton -> jsonObjectOf("blocks" to jsonArrayOf(v.blocks.map { b ->
            jsonObjectOf("label" to jsonOf(b.label), "width" to jsonOf(b.width.name), "hint" to jsonOf(b.hint.name))
        }))
    }

    /** [parent] = pembaca proposal; `view` dibaca darinya menurut [widget]. */
    fun decode(widget: WidgetKind, parent: ProposalJsonReader): ViewProposal {
        if (widget == WidgetKind.CUSTOM_SCREEN) return decodeCustomScreen(parent)
        val r = parent.obj("view")
        return when (widget) {
            WidgetKind.KANBAN -> {
                val raw = r.rawNode()
                ViewProposal.Kanban(
                    card = r.parsed("card") { InteractiveScreenCodec.decodeCardElements(raw["card"]) },
                    columnMeta = r.parsed("columnMeta") { InteractiveScreenCodec.decodeColumnMetaMap(raw["columnMeta"]) },
                    detailFormFields = r.strings("detailFormFields"),
                    detailFormSubmitLabel = r.optString("detailFormSubmitLabel") ?: "Simpan"
                )
            }
            WidgetKind.TABLE -> ViewProposal.Table(r.strings("columns"), r.boolean("inlineCreate", false), r.strings("editableFields"))
            WidgetKind.FORM -> ViewProposal.Form(r.strings("fields"), r.optString("submitLabel") ?: "Simpan")
            WidgetKind.CHECKLIST -> ViewProposal.Checklist(r.string("labelField"), r.string("doneField"))
            WidgetKind.DASHBOARD -> ViewProposal.Dashboard(r.objects("tiles").map { t ->
                t.build {
                    TileSpec(
                        t.string("label"), t.optString("value"),
                        t.optObject("count")?.let { c -> InteractiveScreenCodec.decodeCount(c.rawNode()) }
                    )
                }
            })
            WidgetKind.PRINT -> ViewProposal.Print(r.strings("fields"))
            WidgetKind.CUSTOM_SCREEN -> decodeCustomScreen(parent)
        }
    }

    /** Tanpa `view` / null = [ViewProposal.None] (draf lama); `view.blocks` = [ViewProposal.Skeleton]. */
    private fun decodeCustomScreen(parent: ProposalJsonReader): ViewProposal {
        val r = parent.optObject("view") ?: return ViewProposal.None
        return ViewProposal.Skeleton(r.objects("blocks").map { b ->
            SkeletonBlock(
                label = b.string("label"),
                width = b.optString("width")?.let { raw ->
                    SkeletonWidth.entries.firstOrNull { it.name == raw }
                        ?: b.fail("width", "Lebar '$raw' bukan kosakata tertutup: ${SkeletonWidth.entries.joinToString { it.name }}")
                } ?: SkeletonWidth.FULL,
                hint = b.optString("hint")?.let { raw ->
                    SkeletonHint.entries.firstOrNull { it.name == raw }
                        ?: b.fail("hint", "Petunjuk '$raw' bukan kosakata tertutup: ${SkeletonHint.entries.joinToString { it.name }}")
                } ?: SkeletonHint.TABLE
            )
        })
    }
}

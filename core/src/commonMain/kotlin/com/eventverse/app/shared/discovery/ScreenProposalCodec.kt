package com.eventverse.app.shared.discovery

import com.eventverse.app.domain.discovery.WidgetKind
import com.eventverse.app.domain.discovery.proposal.EntityProposal
import com.eventverse.app.domain.discovery.proposal.FieldProposal
import com.eventverse.app.domain.discovery.proposal.ProposalSource
import com.eventverse.app.domain.discovery.proposal.ScreenProposal
import com.eventverse.app.domain.discovery.proposal.ViewProposal
import com.eventverse.app.domain.pack.ModuleId
import com.eventverse.app.domain.prototype.FieldType
import com.eventverse.app.domain.prototype.NumberFormat
import com.eventverse.app.domain.prototype.TextValidation
import com.eventverse.app.shared.json.JsonValue
import com.eventverse.app.shared.json.jsonArrayOf
import com.eventverse.app.shared.json.jsonObjectOf
import com.eventverse.app.shared.json.jsonOf
import com.eventverse.app.shared.json.jsonStringMapOf
import com.eventverse.app.shared.pack.InteractiveScreenCodec

/**
 * JSON ⇄ [ScreenProposal] / [ProposalSource] — bagian dokumen draf (`screens[i].proposal`/`source`) dan
 * **format keluaran agent AI** untuk isi layar. Decode ketat (tenant-variability Kontrak 4): jenis widget,
 * tipe field, gaya kartu, dan sumber tak dikenal **ditolak** dengan path; `view` dibaca menurut `widget`
 * sehingga ketidakcocokan varian tidak mungkin terjadi lewat dokumen. Aturan bisnis (batas, koherensi) milik
 * `ScreenProposalValidator`, bukan codec: codec hanya memastikan bentuk.
 */
object ScreenProposalCodec {

    fun encode(p: ScreenProposal): JsonValue.Obj = jsonObjectOf(
        "screenId" to jsonOf(p.screenId), "moduleId" to jsonOf(p.moduleId.value), "title" to jsonOf(p.title),
        "widget" to jsonOf(p.widget.code), "rationale" to jsonOf(p.rationale),
        "entity" to (p.entity?.let(::encodeEntity) ?: JsonValue.Null),
        "view" to ViewProposalCodec.encode(p.view),
        "seed" to jsonArrayOf(p.seed.map { jsonStringMapOf(it) }),
        "binding" to InteractiveScreenCodec.encodeBinding(p.binding)
    )

    /** @param path path objek ini di dokumen induk, mis. `$.screens[0].proposal`. */
    fun decode(root: JsonValue.Obj, path: String): ScreenProposal {
        val r = ProposalJsonReader(root, path)
        val widgetCode = r.string("widget")
        val widget = WidgetKind.fromCode(widgetCode)
            ?: r.fail("widget", "Widget '$widgetCode' bukan kosakata tertutup: ${WidgetKind.entries.joinToString { it.code }}")
        return ScreenProposal(
            screenId = r.string("screenId"),
            moduleId = r.string("moduleId").let { raw -> r.parsed("moduleId") { ModuleId(raw) } },
            title = r.string("title"),
            widget = widget,
            rationale = r.string("rationale"),
            entity = r.optObject("entity")?.let(::decodeEntity),
            view = ViewProposalCodec.decode(widget, r),
            seed = r.stringRows("seed"),
            binding = r.parsed("binding") { InteractiveScreenCodec.decodeBindingValue(root["binding"]) }
        )
    }

    fun encodeSource(s: ProposalSource): JsonValue.Obj = when (s) {
        ProposalSource.Pack -> jsonObjectOf("kind" to jsonOf("PACK"))
        ProposalSource.Deterministic -> jsonObjectOf("kind" to jsonOf("DETERMINISTIC"))
        is ProposalSource.Agent -> jsonObjectOf("kind" to jsonOf("AGENT"), "agentRef" to jsonOf(s.agentRef))
    }

    fun decodeSource(root: JsonValue.Obj, path: String): ProposalSource {
        val r = ProposalJsonReader(root, path)
        return when (val kind = r.string("kind")) {
            "PACK" -> ProposalSource.Pack
            "DETERMINISTIC" -> ProposalSource.Deterministic
            "AGENT" -> r.build { ProposalSource.Agent(r.string("agentRef")) }
            else -> r.fail("kind", "Sumber '$kind' tidak dikenal: PACK, DETERMINISTIC, AGENT")
        }
    }

    private fun encodeEntity(e: EntityProposal): JsonValue.Obj = jsonObjectOf(
        "id" to jsonOf(e.id), "label" to jsonOf(e.label),
        "fields" to jsonArrayOf(e.fields.map { f ->
            jsonObjectOf(
                "key" to jsonOf(f.key), "label" to jsonOf(f.label), "type" to jsonOf(f.type.name),
                "required" to jsonOf(f.required), "options" to jsonArrayOf(f.options.map(::jsonOf)),
                "format" to jsonOf(f.format.name), "currencyCode" to jsonOf(f.currencyCode),
                "withTime" to jsonOf(f.withTime), "validation" to jsonOf(f.validation.name)
            )
        }),
        "statusField" to jsonOf(e.statusField),
        "transitions" to jsonObjectOf(*e.transitions.map { (k, v) -> k to jsonArrayOf(v.map(::jsonOf)) }.toTypedArray())
    )

    private fun decodeEntity(r: ProposalJsonReader): EntityProposal = EntityProposal(
        id = r.string("id"),
        label = r.string("label"),
        fields = r.objects("fields").map { f ->
            val typeName = f.string("type")
            val formatName = f.optString("format")
            val validationName = f.optString("validation")
            FieldProposal(
                key = f.string("key"),
                label = f.string("label"),
                type = FieldType.entries.firstOrNull { it.name == typeName }
                    ?: f.fail("type", "Tipe field '$typeName' bukan kosakata tertutup: ${FieldType.entries.joinToString { it.name }}"),
                required = f.boolean("required", false),
                options = f.strings("options"),
                format = if (formatName == null) NumberFormat.PLAIN
                else NumberFormat.entries.firstOrNull { it.name == formatName }
                    ?: f.fail("format", "Format angka '$formatName' bukan kosakata tertutup: ${NumberFormat.entries.joinToString { it.name }}"),
                currencyCode = f.optString("currencyCode"),
                withTime = f.boolean("withTime", false),
                validation = if (validationName == null) TextValidation.NONE
                else TextValidation.entries.firstOrNull { it.name == validationName }
                    ?: f.fail("validation", "Validasi teks '$validationName' bukan kosakata tertutup: ${TextValidation.entries.joinToString { it.name }}")
            )
        },
        statusField = r.optString("statusField"),
        transitions = r.stringListMap("transitions")
    )
}

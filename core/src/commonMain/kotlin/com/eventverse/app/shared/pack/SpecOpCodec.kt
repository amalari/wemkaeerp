package com.eventverse.app.shared.pack

import com.eventverse.app.domain.discovery.WidgetKind
import com.eventverse.app.domain.discovery.brief.CaptureEntry
import com.eventverse.app.domain.prototype.CardStyle
import com.eventverse.app.domain.prototype.FieldSpec
import com.eventverse.app.domain.prototype.FieldType
import com.eventverse.app.domain.prototype.NumberFormat
import com.eventverse.app.domain.prototype.SpecOp
import com.eventverse.app.shared.json.JsonValue
import com.eventverse.app.shared.json.jsonArrayOf
import com.eventverse.app.shared.json.jsonObjectOf
import com.eventverse.app.shared.json.jsonOf

/**
 * Kawat JSON [SpecOp] dan [CaptureEntry] (kontrak v1) — dipakai body/respons `spec-ops` dan `brief`.
 * Penanda jenis di kunci `type`. Decode **ketat**: jenis/field tak dikenal = `Result.failure` berpesan,
 * tidak ada operasi yang "ditebak".
 */
object SpecOpCodec {
    fun encode(op: SpecOp): JsonValue.Obj = when (op) {
        is SpecOp.AddEnumOption -> jsonObjectOf("type" to jsonOf("AddEnumOption"), "entityId" to jsonOf(op.entityId), "field" to jsonOf(op.field), "option" to jsonOf(op.option), "after" to jsonOf(op.after))
        is SpecOp.RenameEnumOption -> jsonObjectOf("type" to jsonOf("RenameEnumOption"), "entityId" to jsonOf(op.entityId), "field" to jsonOf(op.field), "from" to jsonOf(op.from), "to" to jsonOf(op.to))
        is SpecOp.AddTransition -> jsonObjectOf("type" to jsonOf("AddTransition"), "entityId" to jsonOf(op.entityId), "field" to jsonOf(op.field), "from" to jsonOf(op.from), "to" to jsonOf(op.to))
        is SpecOp.AddField -> jsonObjectOf(
            "type" to jsonOf("AddField"), "entityId" to jsonOf(op.entityId),
            "field" to jsonObjectOf(
                "key" to jsonOf(op.field.key), "label" to jsonOf(op.field.label), "fieldType" to jsonOf(op.field.type.name),
                "options" to jsonArrayOf(op.field.options.map(::jsonOf)), "required" to jsonOf(op.field.required),
                "format" to jsonOf(op.field.format.name), "currencyCode" to jsonOf(op.field.currencyCode)
            )
        )
        is SpecOp.RenameFieldLabel -> jsonObjectOf("type" to jsonOf("RenameFieldLabel"), "entityId" to jsonOf(op.entityId), "key" to jsonOf(op.key), "label" to jsonOf(op.label))
        is SpecOp.ShowFieldOnCard -> jsonObjectOf("type" to jsonOf("ShowFieldOnCard"), "entityId" to jsonOf(op.entityId), "field" to jsonOf(op.field), "style" to jsonOf(op.style.name))
        is SpecOp.SetFieldRequired -> jsonObjectOf("type" to jsonOf("SetFieldRequired"), "entityId" to jsonOf(op.entityId), "field" to jsonOf(op.field), "required" to jsonOf(op.required))
        is SpecOp.ChangeWidget -> jsonObjectOf("type" to jsonOf("ChangeWidget"), "screenId" to jsonOf(op.screenId), "widget" to jsonOf(op.widget.code))
    }

    fun decode(o: JsonValue.Obj): Result<SpecOp> = runCatching {
        fun str(key: String) = requireNotNull(o.string(key)?.takeIf { it.isNotBlank() }) { "Bidang '$key' wajib diisi." }
        when (val type = o.string("type")) {
            "AddEnumOption" -> SpecOp.AddEnumOption(str("entityId"), str("field"), str("option"), o.string("after"))
            "RenameEnumOption" -> SpecOp.RenameEnumOption(str("entityId"), str("field"), str("from"), str("to"))
            "AddTransition" -> SpecOp.AddTransition(str("entityId"), str("field"), str("from"), str("to"))
            "AddField" -> {
                val f = requireNotNull(o.obj("field")) { "Bidang 'field' wajib diisi." }
                val ft = FieldType.entries.firstOrNull { it.name == f.string("fieldType") }
                val formatName = f.string("format")
                val format = if (formatName == null || formatName == "PLAIN") NumberFormat.PLAIN
                else NumberFormat.entries.firstOrNull { it.name == formatName }
                    ?: throw IllegalArgumentException("Format angka '${formatName}' bukan kosakata tertutup: ${NumberFormat.entries.joinToString { it.name }}")
                SpecOp.AddField(
                    str("entityId"),
                    FieldSpec(f.string("key").orEmpty(), f.string("label").orEmpty(), requireNotNull(ft) { "Tipe field '${f.string("fieldType")}' tidak dikenal." }, f.stringArray("options"), f.boolean("required") ?: false, format, f.string("currencyCode"))
                )
            }
            "RenameFieldLabel" -> SpecOp.RenameFieldLabel(str("entityId"), str("key"), str("label"))
            "ShowFieldOnCard" -> {
                val style = CardStyle.entries.firstOrNull { it.name == o.string("style") }
                SpecOp.ShowFieldOnCard(str("entityId"), str("field"), requireNotNull(style) { "Gaya kartu '${o.string("style").orEmpty()}' tidak dikenal." })
            }
            "SetFieldRequired" -> SpecOp.SetFieldRequired(
                str("entityId"), str("field"),
                requireNotNull(o.boolean("required")) { "Bidang 'required' wajib diisi." }
            )
            "ChangeWidget" -> SpecOp.ChangeWidget(
                str("screenId"),
                requireNotNull(WidgetKind.fromCode(o.string("widget").orEmpty())) { "Jenis tampilan '${o.string("widget").orEmpty()}' tidak dikenal." }
            )
            else -> throw IllegalArgumentException("Jenis operasi '${type.orEmpty()}' tidak dikenal.")
        }
    }

    fun encode(e: CaptureEntry): JsonValue.Obj =
        jsonObjectOf("at" to jsonOf(e.at), "op" to encode(e.op), "ok" to jsonOf(e.ok), "message" to jsonOf(e.message))

    fun decodeEntry(o: JsonValue.Obj): Result<CaptureEntry> = runCatching {
        val op = decode(requireNotNull(o.obj("op")) { "Bidang 'op' wajib diisi." }).getOrThrow()
        CaptureEntry(requireNotNull(o.string("at")?.takeIf { it.isNotBlank() }) { "Bidang 'at' wajib diisi." }, op, o.boolean("ok") ?: false, o.string("message"))
    }
}

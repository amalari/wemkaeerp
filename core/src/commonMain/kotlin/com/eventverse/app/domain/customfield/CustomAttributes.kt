package com.eventverse.app.domain.customfield

import com.eventverse.app.shared.json.JsonValue
import com.eventverse.app.shared.json.jsonObjectOf
import kotlinx.datetime.LocalDate

/**
 * The tagged, per-record custom field value bag stored in a `custom_attributes` JSONB
 * column (see V20/V21).
 *
 * Wraps a [JsonValue.Obj] whose keys are [CustomFieldId] strings — never a field's label or
 * key, since those are admin-editable and this bag must stay meaningful across a rename.
 * Each value is tagged with its [FieldType.code] (`{"t":"text","v":"..."}`) so a record read
 * during or after a type change stays deterministically interpretable, and so a key left
 * over from an archived field never gets misread as a different type.
 *
 * "Empty" is represented by the key being absent — never `null` or `""` — so containment
 * filters and "is empty" filters never need to disambiguate "set to null" from "never set".
 */
data class CustomAttributes(private val raw: JsonValue.Obj) {

    /** Untyped access to the raw tagged cell, or null if the field has no value set. */
    fun rawCell(fieldId: CustomFieldId): JsonValue.Obj? = raw.obj(fieldId.value)

    fun text(fieldId: CustomFieldId): String? = rawCell(fieldId)?.string("v")

    fun number(fieldId: CustomFieldId): String? =
        rawCell(fieldId)?.let { it.entries["v"] }?.let { (it as? JsonValue.Num)?.raw }

    fun selectedOption(fieldId: CustomFieldId): SelectOptionId? =
        rawCell(fieldId)?.string("v")?.let { SelectOptionId(it) }

    fun date(fieldId: CustomFieldId): LocalDate? =
        com.eventverse.app.shared.common.DateTimeCodec.parseLocalDateOrNull(rawCell(fieldId)?.string("v"))

    fun checked(fieldId: CustomFieldId): Boolean = rawCell(fieldId)?.boolean("v") ?: false

    /**
     * C7 (TRD-FIELD-001): id record target satu field [FieldType.Relation]. Sel = string id bertag `relation`.
     * Keberadaan record diverifikasi server lewat [RelationTargetResolver] (Track B) — pembaca ini hanya
     * mengembalikan id yang tersimpan, tidak pernah mengarang rujukan.
     */
    fun relation(fieldId: CustomFieldId): String? = rawCell(fieldId)?.string("v")

    fun hasValue(fieldId: CustomFieldId): Boolean = raw.has(fieldId.value)

    /** Returns a copy with [fieldId] set to a tagged cell, or removed if [cell] is null. */
    fun with(fieldId: CustomFieldId, cell: JsonValue.Obj?): CustomAttributes {
        val entries = LinkedHashMap(raw.entries)
        if (cell == null) entries.remove(fieldId.value) else entries[fieldId.value] = cell
        return CustomAttributes(JsonValue.Obj(entries))
    }

    fun toJsonValue(): JsonValue.Obj = raw

    fun encode(): String = raw.encode()

    companion object {
        val EMPTY = CustomAttributes(JsonValue.Obj(emptyMap()))

        fun fromJsonValue(value: JsonValue.Obj): CustomAttributes = CustomAttributes(value)

        fun textCell(value: String): JsonValue.Obj = jsonObjectOf("t" to jsonTag("text"), "v" to JsonValue.Str(value))
        fun numberCell(raw: String): JsonValue.Obj = jsonObjectOf("t" to jsonTag("number"), "v" to JsonValue.Num(raw))
        fun selectCell(optionId: SelectOptionId): JsonValue.Obj =
            jsonObjectOf("t" to jsonTag("select"), "v" to JsonValue.Str(optionId.value))
        fun dateCell(date: LocalDate): JsonValue.Obj =
            jsonObjectOf("t" to jsonTag("date"), "v" to JsonValue.Str(date.toString()))
        fun checkboxCell(checked: Boolean): JsonValue.Obj =
            jsonObjectOf("t" to jsonTag("checkbox"), "v" to JsonValue.Bool(checked))

        /** C7: sel rujukan = id record target (string) bertag `relation`; keberadaan diverifikasi server. */
        fun relationCell(targetRecordId: String): JsonValue.Obj =
            jsonObjectOf("t" to jsonTag("relation"), "v" to JsonValue.Str(targetRecordId))

        private fun jsonTag(tag: String): JsonValue = JsonValue.Str(tag)
    }
}

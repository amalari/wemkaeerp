package com.eventverse.app.domain.customfield

import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.shared.json.JsonParser
import com.eventverse.app.shared.json.JsonValue
import com.eventverse.app.shared.json.jsonArrayOf
import com.eventverse.app.shared.json.jsonObjectOf
import com.eventverse.app.shared.json.jsonOf
import kotlinx.datetime.Instant

/**
 * Single source of truth for turning [FieldType]/[CustomFieldDefinition] into JSON and back
 * — used by BOTH the Ktor server (`config` JSONB column, REST payloads) and the Compose
 * Multiplatform client, so the two can never drift the way two hand-written parsers would.
 * Modeled directly on [com.eventverse.app.shared.pipeline.PipelineGraphCodec].
 */
object CustomAttributesCodec {

    /** Bumped when the `config` JSON shape changes in a way readers must know about. */
    const val SCHEMA_VERSION: Int = 1

    private const val KEY_OPTIONS = "options"
    private const val KEY_FORMAT = "format"
    private const val KEY_CURRENCY_CODE = "currencyCode"
    private const val KEY_DECIMALS = "decimals"
    private const val KEY_WITH_TIME = "withTime"
    private const val KEY_MAX_COUNT = "maxCount"
    private const val KEY_TARGET_RESOURCE = "targetResource"
    private const val KEY_ID = "id"
    private const val KEY_LABEL = "label"
    private const val KEY_COLOR_HEX = "colorHex"
    private const val KEY_ARCHIVED_AT = "archivedAt"

    // -----------------------------------------------------------------------
    // FieldType <-> (field_type code, config JSONB)
    // -----------------------------------------------------------------------

    fun encodeConfig(type: FieldType): JsonValue.Obj = when (type) {
        is FieldType.Text, is FieldType.LongText, is FieldType.Checkbox ->
            JsonValue.Obj(emptyMap())

        is FieldType.Number -> jsonObjectOf(
            KEY_FORMAT to jsonOf(encodeNumberFormatTag(type.format)),
            KEY_CURRENCY_CODE to jsonOf((type.format as? NumberFormat.Currency)?.currencyCode),
            KEY_DECIMALS to jsonOf(type.decimals)
        )

        is FieldType.SingleSelect -> jsonObjectOf(
            KEY_OPTIONS to jsonArrayOf(type.options.map(::encodeOption))
        )

        is FieldType.DateField -> jsonObjectOf(KEY_WITH_TIME to jsonOf(type.withTime))

        is FieldType.UserRef -> jsonObjectOf(KEY_MAX_COUNT to jsonOf(type.maxCount))

        is FieldType.Relation -> jsonObjectOf(
            KEY_TARGET_RESOURCE to jsonOf(type.targetResource),
            KEY_MAX_COUNT to jsonOf(type.maxCount)
        )
    }

    /** Returns null for an unrecognised code — callers must treat that as data corruption. */
    fun decodeFieldType(code: String, config: JsonValue.Obj): FieldType? = when (code) {
        FieldType.Text.code -> FieldType.Text
        FieldType.LongText.code -> FieldType.LongText
        FieldType.Checkbox.code -> FieldType.Checkbox

        "NUMBER" -> decodeNumberFormat(config)?.let { format ->
            FieldType.Number(format = format, decimals = config.int(KEY_DECIMALS) ?: 0)
        }

        "SINGLE_SELECT" -> FieldType.SingleSelect(
            options = config.objectArray(KEY_OPTIONS).mapNotNull(::decodeOption)
        )

        "DATE" -> FieldType.DateField(withTime = config.boolean(KEY_WITH_TIME) ?: false)

        "USER_REF" -> FieldType.UserRef(maxCount = config.int(KEY_MAX_COUNT) ?: 1)

        // TRD-FIELD-001 §4.3: RELATION tanpa `targetResource` = korupsi (null), BUKAN fallback —
        // membacanya sebagai tipe lain mengubah data tanpa jejak.
        "RELATION" -> config.string(KEY_TARGET_RESOURCE)?.let {
            FieldType.Relation(targetResource = it, maxCount = config.int(KEY_MAX_COUNT) ?: 1)
        }

        else -> null
    }

    private fun encodeNumberFormatTag(format: NumberFormat): String = when (format) {
        NumberFormat.Plain -> "plain"
        NumberFormat.Percent -> "percent"
        is NumberFormat.Currency -> "currency"
    }

    /**
     * Format tak dikenal, atau `currency` tanpa kode mata uang, mengembalikan null — **bukan** `Plain`: membacanya
     * sebagai angka polos mengubah data tanpa jejak. Kunci `format` yang tidak ada = baris lama sebelum format
     * dikenal, tetap `Plain`.
     */
    private fun decodeNumberFormat(config: JsonValue.Obj): NumberFormat? = when (config.string(KEY_FORMAT)) {
        null, "plain" -> NumberFormat.Plain
        "percent" -> NumberFormat.Percent
        "currency" -> config.string(KEY_CURRENCY_CODE)?.let { NumberFormat.Currency(it) }
        else -> null
    }

    private fun encodeOption(option: SelectOption): JsonValue.Obj = jsonObjectOf(
        KEY_ID to jsonOf(option.id.value),
        KEY_LABEL to jsonOf(option.label),
        KEY_COLOR_HEX to jsonOf(option.colorHex),
        KEY_ARCHIVED_AT to jsonOf(option.archivedAt)
    )

    private fun decodeOption(obj: JsonValue.Obj): SelectOption? {
        val id = obj.string(KEY_ID) ?: return null
        val label = obj.string(KEY_LABEL) ?: return null
        val colorHex = obj.string(KEY_COLOR_HEX) ?: return null
        return SelectOption(
            id = SelectOptionId(id),
            label = label,
            colorHex = colorHex,
            archivedAt = obj.string(KEY_ARCHIVED_AT)
        )
    }

    // -----------------------------------------------------------------------
    // Full CustomFieldDefinition, for REST payloads
    // -----------------------------------------------------------------------

    fun encodeDefinition(def: CustomFieldDefinition): JsonValue.Obj = jsonObjectOf(
        "id" to jsonOf(def.id.value),
        "ownerResource" to jsonOf(def.ownerResource.value),
        "key" to jsonOf(def.key.value),
        "label" to jsonOf(def.label),
        "type" to jsonOf(def.type.code),
        "config" to encodeConfig(def.type),
        "position" to jsonOf(def.position),
        "isRequired" to jsonOf(def.isRequired),
        "isSystem" to jsonOf(def.isSystem),
        "defaultValue" to (def.defaultValue ?: JsonValue.Null),
        "archivedAt" to jsonOf(def.archivedAt?.toString())
    )

    fun encodeDefinitions(defs: List<CustomFieldDefinition>): String =
        jsonArrayOf(defs.map(::encodeDefinition)).encode()

    /**
     * Decodes one definition. [tenantId] is supplied by the caller (from the request
     * context or the stored row's own `tenant_id` column), never trusted from the body —
     * the same rule [com.eventverse.app.shared.pipeline.PipelineGraphCodec.decodePipeline]
     * follows, so a client cannot address another tenant's field by editing JSON.
     */
    fun decodeDefinition(tenantId: TenantId, obj: JsonValue.Obj): CustomFieldDefinition? {
        val id = obj.string("id") ?: return null
        val ownerResource = obj.string("ownerResource") ?: return null
        val key = obj.string("key") ?: return null
        val label = obj.string("label") ?: return null
        val typeCode = obj.string("type") ?: return null
        val config = obj.obj("config") ?: JsonValue.Obj(emptyMap())
        val type = decodeFieldType(typeCode, config) ?: return null

        return CustomFieldDefinition(
            id = CustomFieldId(id),
            tenantId = tenantId,
            ownerResource = OwnerResource(ownerResource),
            key = FieldKey(key),
            label = label,
            type = type,
            position = obj.double("position") ?: 0.0,
            isRequired = obj.boolean("isRequired") ?: false,
            defaultValue = obj.entries["defaultValue"]?.takeIf { it !is JsonValue.Null },
            isSystem = obj.boolean("isSystem") ?: false,
            archivedAt = com.eventverse.app.shared.common.DateTimeCodec.parseInstantOrNull(obj.string("archivedAt"))
        )
    }

    fun decodeDefinitions(tenantId: TenantId, rawJson: String): List<CustomFieldDefinition> =
        JsonParser.parseArray(rawJson).filterIsInstance<JsonValue.Obj>().mapNotNull { decodeDefinition(tenantId, it) }
}

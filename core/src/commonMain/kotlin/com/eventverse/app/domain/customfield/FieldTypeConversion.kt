package com.eventverse.app.domain.customfield

import com.eventverse.app.shared.json.JsonValue
import kotlinx.datetime.LocalDate

/** How safe it is to change a field's declared type from one shape to another. */
enum class ConversionSafety {
    /** Same shape; nothing to convert. */
    IDENTITY,

    /** Every existing value survives with no loss (e.g. Number -> Text). Applied immediately. */
    LOSSLESS,

    /** Some existing values may fail to convert. Requires a dry run + explicit confirmation. */
    LOSSY,

    /** Never permitted — the two shapes are not meaningfully related. */
    FORBIDDEN
}

/** The outcome of coercing one cell's value from its old type to a new one. */
sealed interface CoercionResult {
    data class Converted(val cell: JsonValue.Obj) : CoercionResult

    /** The value could not be interpreted as the new type. The original text is preserved. */
    data class Cleared(val orphanedRaw: String) : CoercionResult
}

/**
 * Declares which [FieldType] transitions are safe, and performs the actual value coercion.
 *
 * Preview (dry run) and apply MUST call [coerce] — the same pure function — so a preview
 * can never lie about what apply will do. This mirrors the reasoning behind
 * [com.eventverse.app.domain.orgchart.OrgChartVisibility]: one function, every caller,
 * impossible to diverge.
 */
object FieldTypeConversion {

    private const val MAX_DISTINCT_VALUES_FOR_SELECT = 50

    fun classify(from: FieldType, to: FieldType): ConversionSafety {
        if (from::class == to::class && from.code == to.code) return ConversionSafety.IDENTITY

        // Nothing may convert into or out of UserRef by coercion: a person reference is not
        // a coercion of a string. Forcing delete + re-add makes the admin notice the link
        // rows that would otherwise silently vanish.
        if (from is FieldType.UserRef || to is FieldType.UserRef) return ConversionSafety.FORBIDDEN

        return when {
            from is FieldType.Text && to is FieldType.LongText -> ConversionSafety.LOSSLESS
            from is FieldType.LongText && to is FieldType.Text -> ConversionSafety.LOSSLESS
            to is FieldType.Text || to is FieldType.LongText -> ConversionSafety.LOSSLESS

            from is FieldType.Number && to is FieldType.Number -> ConversionSafety.LOSSLESS
            from is FieldType.DateField && to is FieldType.DateField -> ConversionSafety.LOSSLESS

            (from is FieldType.Text || from is FieldType.LongText) && to is FieldType.Number -> ConversionSafety.LOSSY
            (from is FieldType.Text || from is FieldType.LongText) && to is FieldType.DateField -> ConversionSafety.LOSSY
            (from is FieldType.Text || from is FieldType.LongText) && to is FieldType.SingleSelect -> ConversionSafety.LOSSY
            from is FieldType.DateField && (to is FieldType.Text || to is FieldType.LongText) -> ConversionSafety.LOSSLESS
            from is FieldType.SingleSelect && (to is FieldType.Text || to is FieldType.LongText) -> ConversionSafety.LOSSLESS
            from is FieldType.Checkbox && (to is FieldType.Text || to is FieldType.LongText) -> ConversionSafety.LOSSLESS
            from is FieldType.Number && to is FieldType.Checkbox -> ConversionSafety.LOSSY

            else -> ConversionSafety.FORBIDDEN
        }
    }

    /**
     * Refuses a lossy conversion into [FieldType.SingleSelect] when the source data would
     * mint more than [MAX_DISTINCT_VALUES_FOR_SELECT] options — auto-minting hundreds of
     * statuses produces a board nobody can use.
     */
    fun canMintSelectOptions(distinctValueCount: Int): Boolean =
        distinctValueCount <= MAX_DISTINCT_VALUES_FOR_SELECT

    /**
     * Coerces one tagged cell from [from] to [to]. Never throws: an uninterpretable value is
     * [CoercionResult.Cleared], carrying the original text so it can be restored, rather
     * than silently dropped.
     */
    fun coerce(cell: JsonValue.Obj?, from: FieldType, to: FieldType): CoercionResult? {
        if (cell == null) return null
        val rawValueText = cellText(cell) ?: return null

        return when {
            to is FieldType.Text || to is FieldType.LongText ->
                CoercionResult.Converted(CustomAttributes.textCell(rawValueText))

            to is FieldType.Number -> rawValueText.trim().toDoubleOrNull()
                ?.let { CoercionResult.Converted(CustomAttributes.numberCell(it.toString())) }
                ?: CoercionResult.Cleared(rawValueText)

            to is FieldType.DateField -> runCatching { LocalDate.parse(rawValueText.trim()) }.getOrNull()
                ?.let { CoercionResult.Converted(CustomAttributes.dateCell(it)) }
                ?: CoercionResult.Cleared(rawValueText)

            to is FieldType.Checkbox -> rawValueText.trim().lowercase().let {
                when (it) {
                    "true", "1", "ya" -> CoercionResult.Converted(CustomAttributes.checkboxCell(true))
                    "false", "0", "tidak" -> CoercionResult.Converted(CustomAttributes.checkboxCell(false))
                    else -> CoercionResult.Cleared(rawValueText)
                }
            }

            to is FieldType.SingleSelect -> to.options.firstOrNull {
                it.label.equals(rawValueText.trim(), ignoreCase = true)
            }?.let { CoercionResult.Converted(CustomAttributes.selectCell(it.id)) }
                ?: CoercionResult.Cleared(rawValueText)

            else -> CoercionResult.Cleared(rawValueText)
        }
    }

    /** A human-readable rendering of a cell's raw value, regardless of its declared type. */
    private fun cellText(cell: JsonValue.Obj): String? = when (val v = cell.entries["v"]) {
        is JsonValue.Str -> v.value
        is JsonValue.Num -> v.raw
        is JsonValue.Bool -> v.value.toString()
        else -> null
    }
}

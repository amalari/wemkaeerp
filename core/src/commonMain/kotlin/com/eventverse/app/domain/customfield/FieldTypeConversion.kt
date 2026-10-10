package com.eventverse.app.domain.customfield

import com.eventverse.app.domain.prototype.FieldType
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

    fun classify(from: CrmFieldType, to: CrmFieldType): ConversionSafety {
        // C6 (Irisan 2): dua varian DATE punya kode sama, tapi bentuk nilai sahnya beda (ketat dua arah) —
        // varian sama = IDENTITY, ganti `withTime` = LOSSY (dry run + konfirmasi).
        if (from.kind == FieldType.DATE && to.kind == FieldType.DATE) {
            return if (from.withTime == to.withTime) ConversionSafety.IDENTITY else ConversionSafety.LOSSY
        }
        // Jenis sama = IDENTITY (perilaku lama: kelas+kode sama, apa pun parameternya).
        if (from.kind == to.kind) return ConversionSafety.IDENTITY

        // Nothing may convert into or out of UserRef by coercion: a person reference is not
        // a coercion of a string. Forcing delete + re-add makes the admin notice the link
        // rows that would otherwise silently vanish.
        // Relation (C7) padanannya: rujukan record juga bukan koersi teks, dan baris link-nya
        // (`custom_field_relation_links`) akan hilang senyap bila dipaksa konversi.
        if (from.kind == FieldType.USER_REF || to.kind == FieldType.USER_REF) return ConversionSafety.FORBIDDEN
        if (from.kind == FieldType.RELATION || to.kind == FieldType.RELATION) return ConversionSafety.FORBIDDEN

        return when {
            from.kind == FieldType.TEXT && to.kind == FieldType.LONG_TEXT -> ConversionSafety.LOSSLESS
            from.kind == FieldType.LONG_TEXT && to.kind == FieldType.TEXT -> ConversionSafety.LOSSLESS
            to.kind == FieldType.TEXT || to.kind == FieldType.LONG_TEXT -> ConversionSafety.LOSSLESS

            from.kind == FieldType.NUMBER && to.kind == FieldType.NUMBER -> ConversionSafety.LOSSLESS

            (from.kind == FieldType.TEXT || from.kind == FieldType.LONG_TEXT) && to.kind == FieldType.NUMBER -> ConversionSafety.LOSSY
            (from.kind == FieldType.TEXT || from.kind == FieldType.LONG_TEXT) && to.kind == FieldType.DATE -> ConversionSafety.LOSSY
            (from.kind == FieldType.TEXT || from.kind == FieldType.LONG_TEXT) && to.kind == FieldType.ENUM -> ConversionSafety.LOSSY
            from.kind == FieldType.DATE && (to.kind == FieldType.TEXT || to.kind == FieldType.LONG_TEXT) -> ConversionSafety.LOSSLESS
            from.kind == FieldType.ENUM && (to.kind == FieldType.TEXT || to.kind == FieldType.LONG_TEXT) -> ConversionSafety.LOSSLESS
            from.kind == FieldType.BOOL && (to.kind == FieldType.TEXT || to.kind == FieldType.LONG_TEXT) -> ConversionSafety.LOSSLESS
            from.kind == FieldType.NUMBER && to.kind == FieldType.BOOL -> ConversionSafety.LOSSY

            else -> ConversionSafety.FORBIDDEN
        }
    }

    /**
     * Refuses a lossy conversion into `ENUM` when the source data would
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
    fun coerce(cell: JsonValue.Obj?, from: CrmFieldType, to: CrmFieldType): CoercionResult? {
        if (cell == null) return null
        val rawValueText = cellText(cell) ?: return null

        return when (to.kind) {
            FieldType.TEXT, FieldType.LONG_TEXT ->
                CoercionResult.Converted(CustomAttributes.textCell(rawValueText))

            FieldType.NUMBER -> rawValueText.trim().toDoubleOrNull()
                ?.let { CoercionResult.Converted(CustomAttributes.numberCell(it.toString())) }
                ?: CoercionResult.Cleared(rawValueText)

            // C6 (Irisan 2): sadar `withTime` — target tanggal berwaktu hanya menerima TTTT-BB-HH'T'JJ:MM;
            // tanggal-saja pada target itu = Cleared (eksplisit), bukan dikonversi diam-diam.
            FieldType.DATE ->
                if (to.withTime) {
                    com.eventverse.app.shared.common.DateTimeCodec.parseLocalDateTimeMinuteOrNull(rawValueText.trim())
                        ?.let { CoercionResult.Converted(CustomAttributes.dateTimeCell(it)) }
                        ?: CoercionResult.Cleared(rawValueText)
                } else {
                    com.eventverse.app.shared.common.DateTimeCodec.parseLocalDateOrNull(rawValueText.trim())
                        ?.let { CoercionResult.Converted(CustomAttributes.dateCell(it)) }
                        ?: CoercionResult.Cleared(rawValueText)
                }

            FieldType.BOOL -> rawValueText.trim().lowercase().let {
                when (it) {
                    "true", "1", "ya" -> CoercionResult.Converted(CustomAttributes.checkboxCell(true))
                    "false", "0", "tidak" -> CoercionResult.Converted(CustomAttributes.checkboxCell(false))
                    else -> CoercionResult.Cleared(rawValueText)
                }
            }

            FieldType.ENUM -> to.options.firstOrNull {
                it.label.equals(rawValueText.trim(), ignoreCase = true)
            }?.let { CoercionResult.Converted(CustomAttributes.selectCell(it.id)) }
                ?: CoercionResult.Cleared(rawValueText)

            // Tidak ada konversi koersi menuju tipe-tipe ini (classify = FORBIDDEN untuk semuanya);
            // nilai asli dikembalikan apa adanya, bukan ditebak.
            FieldType.TIME, FieldType.MULTI_SELECT, FieldType.USER_REF, FieldType.RELATION, FieldType.FILE ->
                CoercionResult.Cleared(rawValueText)
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

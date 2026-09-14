package com.eventverse.app.domain.customfield

/**
 * The shape of one tenant-defined custom field value.
 *
 * Seven variants ship in this phase because the CRM Leads custom fields need exactly this
 * set and each one has a genuinely different storage/validation/sort shape — trimming any
 * of them would make this less than a real vertical slice. `MultiSelect`, `Relation`,
 * `Formula`, `Mirror`/`Rollup`, `File` and `Timeline` are deliberately deferred (see the
 * plan's "deferred" table); none of them require a schema change to add later.
 *
 * Currency is NOT its own variant — it is `Number(format = NumberFormat.Currency(...))`.
 * Storage, filtering, sorting and coercion are identical to a plain number; only rendering
 * differs, and giving it a separate variant would double every `when (fieldType)` branch in
 * this file for a formatting concern alone.
 */
sealed interface FieldType {
    /** Persisted discriminator, matches `custom_field_definitions.field_type`. */
    val code: String

    /** Whether this type needs rows in `custom_field_links` (referential integrity). */
    val isReferential: Boolean get() = false

    data object Text : FieldType {
        override val code: String = "TEXT"
    }

    data object LongText : FieldType {
        override val code: String = "LONG_TEXT"
    }

    data class Number(
        val format: NumberFormat = NumberFormat.Plain,
        val decimals: Int = 0
    ) : FieldType {
        override val code: String = "NUMBER"

        init {
            require(decimals in 0..6) { "Number.decimals must be between 0 and 6: $decimals" }
        }
    }

    data class SingleSelect(val options: List<SelectOption>) : FieldType {
        override val code: String = "SINGLE_SELECT"

        val activeOptions: List<SelectOption> get() = options.filter { it.archivedAt == null }

        fun findOption(id: SelectOptionId): SelectOption? = options.firstOrNull { it.id == id }
    }

    data class DateField(val withTime: Boolean = false) : FieldType {
        override val code: String = "DATE"
    }

    data object Checkbox : FieldType {
        override val code: String = "CHECKBOX"
    }

    /** A reference to one or more employees. Referential — backed by `custom_field_links`. */
    data class UserRef(val maxCount: Int = 1) : FieldType {
        override val code: String = "USER_REF"
        override val isReferential: Boolean = true

        init {
            require(maxCount >= 1) { "UserRef.maxCount must be at least 1: $maxCount" }
        }
    }

    companion object {
        /**
         * Codes accepted for `field_type`. Kept in sync with the sealed hierarchy above.
         *
         * Deliberately plain string literals, not `Text.code` etc: referencing a sibling
         * nested `data object`'s member from this companion's own class initializer
         * triggers a JVM static-init ordering bug (`FieldType.Companion.<clinit>` running
         * before `FieldType$Text.<clinit>` populates its INSTANCE), which throws
         * `ExceptionInInitializerError` the first time `FieldType` is touched at all.
         */
        val ALL_CODES: Set<String> = setOf(
            "TEXT", "LONG_TEXT", "NUMBER", "SINGLE_SELECT", "DATE", "CHECKBOX", "USER_REF"
        )
    }
}

sealed interface NumberFormat {
    data object Plain : NumberFormat
    data object Percent : NumberFormat
    data class Currency(val currencyCode: String) : NumberFormat {
        init {
            require(currencyCode.length == 3) { "Currency code must be ISO-4217 (3 letters): $currencyCode" }
        }
    }
}

/**
 * One choice in a [FieldType.SingleSelect] field.
 *
 * [colorHex] is domain data supplied by the tenant admin who created the option — the
 * sanctioned exception to "no color literals outside WeMadeTheme.kt", exactly like
 * `PipelineStage.colorHex`. Deleting an option soft-archives it ([archivedAt]) rather than
 * removing it: a cell already holding this option must keep rendering (greyed) rather than
 * silently losing data.
 */
data class SelectOption(
    val id: SelectOptionId,
    val label: String,
    val colorHex: String,
    val archivedAt: String? = null
) {
    init {
        require(label.isNotBlank()) { "SelectOption.label cannot be blank" }
        require(COLOR_HEX_REGEX.matches(colorHex)) { "SelectOption.colorHex must be #RRGGBB: $colorHex" }
    }

    companion object {
        private val COLOR_HEX_REGEX = Regex("^#[0-9A-Fa-f]{6}$")
    }
}

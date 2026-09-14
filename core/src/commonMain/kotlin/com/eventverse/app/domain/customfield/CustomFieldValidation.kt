package com.eventverse.app.domain.customfield

import com.eventverse.app.shared.json.JsonValue
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate

/** A validation failure for one custom field write. */
sealed interface CustomFieldValidationError {
    val fieldId: CustomFieldId

    data class Required(override val fieldId: CustomFieldId, val label: String) : CustomFieldValidationError
    data class TypeMismatch(override val fieldId: CustomFieldId, val label: String, val expected: String) :
        CustomFieldValidationError
    data class UnknownOption(override val fieldId: CustomFieldId, val label: String, val optionId: String) :
        CustomFieldValidationError
    data class ArchivedField(override val fieldId: CustomFieldId, val label: String) : CustomFieldValidationError
}

/**
 * Validates a single custom field cell write against its [CustomFieldDefinition].
 *
 * Required is validated on WRITE, never on READ: an existing record is never retroactively
 * invalid just because a field turned required after it was created (see
 * [CustomFieldDefinition.requiredSince]). Only a record created at or after that instant —
 * or an edit that touches the required cell itself — is checked.
 */
object CustomFieldValidation {

    /**
     * Validates a create: every active, required field must have a value in [values], and
     * every value actually supplied (required or not) must match its field's declared type.
     * A brand-new record is always subject to every currently-required field — there is no
     * "predates this field" grace period the way a patch has.
     */
    fun validateForCreate(
        definitions: List<CustomFieldDefinition>,
        values: Map<CustomFieldId, JsonValue.Obj?>
    ): List<CustomFieldValidationError> = definitions
        .filter { !it.isArchived }
        .mapNotNull { def ->
            val cell = values[def.id]
            when {
                def.isRequired && (cell == null || !hasMeaningfulValue(cell)) ->
                    CustomFieldValidationError.Required(def.id, def.label)
                cell != null -> validateType(def, cell)
                else -> null
            }
        }

    /**
     * Validates a partial patch against an existing record. Only fields actually present in
     * [patch] are checked — editing unrelated fields on a record that predates a newly
     * required field must never be blocked by that unrelated field being missing.
     */
    fun validateForPatch(
        definitions: List<CustomFieldDefinition>,
        recordCreatedAt: Instant,
        patch: Map<CustomFieldId, JsonValue.Obj?>
    ): List<CustomFieldValidationError> {
        val byId = definitions.associateBy { it.id }
        return patch.entries.mapNotNull { (fieldId, cell) ->
            val def = byId[fieldId] ?: return@mapNotNull null

            if (def.isArchived) return@mapNotNull CustomFieldValidationError.ArchivedField(fieldId, def.label)

            val requiredNow = def.isRequired &&
                (def.requiredSince == null || recordCreatedAt >= def.requiredSince || cell != null)

            if (requiredNow && (cell == null || !hasMeaningfulValue(cell))) {
                CustomFieldValidationError.Required(fieldId, def.label)
            } else if (cell != null) {
                validateType(def, cell)
            } else {
                null
            }
        }
    }

    private fun validateType(def: CustomFieldDefinition, cell: JsonValue.Obj): CustomFieldValidationError? {
        val v = cell.entries["v"]
        return when (def.type) {
            is FieldType.Text, is FieldType.LongText ->
                if (v !is JsonValue.Str) mismatch(def) else null

            is FieldType.Number ->
                if (v !is JsonValue.Num) mismatch(def) else null

            is FieldType.Checkbox ->
                if (v !is JsonValue.Bool) mismatch(def) else null

            is FieldType.DateField -> {
                val raw = (v as? JsonValue.Str)?.value
                if (raw == null || com.eventverse.app.shared.common.DateTimeCodec.parseLocalDateOrNull(raw) == null) mismatch(def) else null
            }

            is FieldType.SingleSelect -> {
                val optionId = (v as? JsonValue.Str)?.value
                when {
                    optionId == null -> mismatch(def)
                    def.type.findOption(SelectOptionId(optionId)) == null ->
                        CustomFieldValidationError.UnknownOption(def.id, def.label, optionId)
                    else -> null
                }
            }

            is FieldType.UserRef -> if (v !is JsonValue.Str) mismatch(def) else null
        }
    }

    private fun mismatch(def: CustomFieldDefinition) =
        CustomFieldValidationError.TypeMismatch(def.id, def.label, def.type.code)

    private fun hasMeaningfulValue(cell: JsonValue.Obj): Boolean = when (val v = cell.entries["v"]) {
        null, JsonValue.Null -> false
        is JsonValue.Str -> v.value.isNotBlank()
        else -> true
    }
}

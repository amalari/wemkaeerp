package com.eventverse.app.domain.customfield

import com.eventverse.app.domain.prototype.FieldType
import com.eventverse.app.domain.prototype.MultiSelectValues
import com.eventverse.app.domain.prototype.TimeFieldValues
import com.eventverse.app.domain.tenant.TenantId
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

    /**
     * C7 (TRD-FIELD-001): nilai field rujukan menunjuk record target yang tidak ada di tenant yang sama.
     * Ditolak saat tulis (fail-closed, 400) — bukan disimpan diam-diam; rendering "tidak ditemukan" hanya
     * untuk target yang hilang SETELAH tersimpan (target dihapus belakangan).
     */
    data class TargetNotFound(
        override val fieldId: CustomFieldId,
        val label: String,
        val resourceId: String
    ) : CustomFieldValidationError
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
        tenantId: TenantId,
        definitions: List<CustomFieldDefinition>,
        values: Map<CustomFieldId, JsonValue.Obj?>
    ): List<CustomFieldValidationError> = definitions
        .filter { !it.isArchived }
        .mapNotNull { def ->
            val cell = values[def.id]
            when {
                def.isRequired && (cell == null || !hasMeaningfulValue(cell)) ->
                    CustomFieldValidationError.Required(def.id, def.label)
                cell != null -> validateType(tenantId, def, cell)
                else -> null
            }
        }

    /**
     * Validates a partial patch against an existing record. Only fields actually present in
     * [patch] are checked — editing unrelated fields on a record that predates a newly
     * required field must never be blocked by that unrelated field being missing.
     */
    fun validateForPatch(
        tenantId: TenantId,
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
                validateType(tenantId, def, cell)
            } else {
                null
            }
        }
    }

    private fun validateType(tenantId: TenantId, def: CustomFieldDefinition, cell: JsonValue.Obj): CustomFieldValidationError? {
        val v = cell.entries["v"]
        return when (def.type.kind) {
            FieldType.TEXT, FieldType.LONG_TEXT ->
                if (v !is JsonValue.Str) mismatch(def) else null

            FieldType.NUMBER ->
                if (v !is JsonValue.Num) mismatch(def) else null

            FieldType.BOOL ->
                if (v !is JsonValue.Bool) mismatch(def) else null

            FieldType.DATE -> {
                val raw = (v as? JsonValue.Str)?.value
                // C6 (Irisan 2): field tanggal berwaktu wajib TTTT-BB-HH'T'JJ:MM (tanpa detik/zona);
                // tanggal-saja ditolak, dan sebaliknya — semantik sama dengan DateFieldValues di kosakata
                // prototype, tanpa koersi diam-diam.
                val valid = raw != null && if (def.type.withTime) {
                    com.eventverse.app.shared.common.DateTimeCodec.parseLocalDateTimeMinuteOrNull(raw) != null
                } else {
                    com.eventverse.app.shared.common.DateTimeCodec.parseLocalDateOrNull(raw) != null
                }
                if (valid) null else mismatch(def)
            }

            // C6/D7: jam dinding JJ:MM tepat menit; aturan bentuk dipegang tunggal oleh TimeFieldValues.
            FieldType.TIME -> {
                val raw = (v as? JsonValue.Str)?.value
                if (raw == null || !TimeFieldValues.isValid(raw)) mismatch(def) else null
            }

            FieldType.ENUM -> {
                val optionId = (v as? JsonValue.Str)?.value
                when {
                    optionId == null -> mismatch(def)
                    def.type.findOption(SelectOptionId(optionId)) == null ->
                        CustomFieldValidationError.UnknownOption(def.id, def.label, optionId)
                    else -> null
                }
            }

            // Larik JSON id opsi aktif (tanpa duplikat, tunduk `maxSelections`) — aturan bentuk dipegang
            // tunggal oleh MultiSelectValues (kosakata bersama), di sini hanya id opsi yang diteruskan.
            FieldType.MULTI_SELECT -> {
                val raw = (v as? JsonValue.Str)?.value
                val ids = def.type.activeOptions.map { it.id.value }
                if (raw == null || !MultiSelectValues.isValid(raw, ids, def.type.maxSelections)) mismatch(def) else null
            }

            FieldType.USER_REF -> if (v !is JsonValue.Str) mismatch(def) else null

            // Sel = id record target (string). Keberadaan id diverifikasi server via RelationTargetResolver
            // (Track B) — validasi bentuk di sini sejajar UserRef; resolver yang gagal = TargetNotFound.
            FieldType.RELATION -> if (v !is JsonValue.Str) mismatch(def) else null

            // C8 (TRD-FIELD-002 FR-5): sel = FileRef sah (key `fields/...`, tanpa `..`) DAN milik tenant
            // penulis (`isValidFor`, bukan `isValid` — ref `fields/<tenantLain>/...` memberi URL unduh objek
            // tenant lain). Referensi rusak/asing = TypeMismatch — TIDAK pernah fallback ke teks/TEXT.
            FieldType.FILE -> {
                val raw = (v as? JsonValue.Str)?.value
                if (raw == null || !com.eventverse.app.domain.storage.FileRef.isValidFor(tenantId.value, raw)) mismatch(def) else null
            }
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

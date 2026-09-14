package com.eventverse.app.domain.customfield.usecases

import com.eventverse.app.domain.customfield.CoercionResult
import com.eventverse.app.domain.customfield.ConversionSafety
import com.eventverse.app.domain.customfield.CustomFieldDefinitionRepository
import com.eventverse.app.domain.customfield.CustomFieldId
import com.eventverse.app.domain.customfield.FieldType
import com.eventverse.app.domain.customfield.FieldTypeConversion
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.shared.json.JsonValue

/**
 * Dry-runs a field type change without writing anything.
 *
 * Read-only: [FieldTypeConversion.coerce] is the SAME function [ChangeFieldTypeUseCase]
 * (fase 1.5, not built yet) will call to actually apply the change — a preview that used a
 * different code path would be worse than no preview at all, since it could tell an admin
 * "this is safe" right before the real apply clears half the column.
 */
class PreviewFieldTypeChangeUseCase(
    private val repository: CustomFieldDefinitionRepository,
    /** Reads every existing tagged cell for [fieldId] across all records of this resource. */
    private val readExistingCells: suspend (tenantId: TenantId, fieldId: CustomFieldId) -> List<JsonValue.Obj?>
) {
    data class Sample(val cellText: String?, val outcome: String)

    data class PreviewResult(
        val safety: ConversionSafety,
        val total: Int,
        val convertible: Int,
        val willBeCleared: Int,
        val samples: List<Sample>
    )

    suspend operator fun invoke(
        tenantId: TenantId,
        fieldId: CustomFieldId,
        newType: FieldType
    ): Result<PreviewResult> = runCatching {
        val definition = requireNotNull(repository.findById(tenantId, fieldId)) {
            "Custom field not found: ${fieldId.value}"
        }

        val safety = FieldTypeConversion.classify(definition.type, newType)
        require(safety != ConversionSafety.FORBIDDEN) {
            "Konversi dari ${definition.type.code} ke ${newType.code} tidak diizinkan"
        }

        val cells = readExistingCells(tenantId, fieldId)
        var convertible = 0
        var cleared = 0
        val samples = mutableListOf<Sample>()

        cells.forEach { cell ->
            when (val result = FieldTypeConversion.coerce(cell, definition.type, newType)) {
                is CoercionResult.Converted -> convertible++
                is CoercionResult.Cleared -> {
                    cleared++
                    if (samples.size < 10) samples += Sample(result.orphanedRaw, "CLEARED")
                }
                null -> Unit
            }
        }

        PreviewResult(
            safety = safety,
            total = cells.size,
            convertible = convertible,
            willBeCleared = cleared,
            samples = samples
        )
    }
}

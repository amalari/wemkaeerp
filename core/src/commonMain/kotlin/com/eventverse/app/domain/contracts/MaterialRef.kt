package com.eventverse.app.domain.contracts

import com.eventverse.app.domain.masterdata.MaterialCategory
import com.eventverse.app.domain.masterdata.MaterialCode
import com.eventverse.app.domain.masterdata.MaterialId
import com.eventverse.app.domain.masterdata.MaterialSummary

/**
 * Migration-safe material reference across module boundaries.
 *
 * [freeText] is ALWAYS populated with whatever the operator typed in sampling ("Viscose 2/30", "HITAM").
 * [materialId] is populated once master data has a corresponding resolved record.
 */
data class MaterialRef(
    val freeText: String,
    val materialId: MaterialId? = null,
    val resolvedCode: MaterialCode? = null,
    val resolvedName: String? = null
) {
    val isResolved: Boolean get() = materialId != null
    val displayLabel: String get() = resolvedName ?: freeText

    companion object {
        fun unresolved(text: String): MaterialRef = MaterialRef(freeText = text.trim())

        fun resolved(summary: MaterialSummary): MaterialRef = MaterialRef(
            freeText = summary.name,
            materialId = summary.id,
            resolvedCode = summary.code,
            resolvedName = summary.name
        )

        fun resolved(id: MaterialId, code: MaterialCode, name: String): MaterialRef = MaterialRef(
            freeText = name,
            materialId = id,
            resolvedCode = code,
            resolvedName = name
        )
    }
}

fun interface MaterialReferenceResolver {
    fun resolve(freeText: String, category: MaterialCategory?): MaterialRef

    companion object {
        val Passthrough = MaterialReferenceResolver { text, _ -> MaterialRef.unresolved(text) }
    }
}

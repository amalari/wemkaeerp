package com.eventverse.app.domain.prospect

import com.eventverse.app.domain.pipeline.defaultExpectedInputType

import com.eventverse.app.domain.pipeline.defaultProducedOutputType

import com.eventverse.app.domain.pipeline.displayName

import com.eventverse.app.domain.pack.GarmentSlots

import com.eventverse.app.domain.pipeline.ModuleArchetype

/**
 * Sanity checks on a pipeline proposed from a prospect's narrative.
 *
 * ## Why this does *not* validate port compatibility
 *
 * The obvious check — "does upstream's output type equal downstream's expected input type?" — was
 * tried and rejected, twice, for two independent reasons.
 *
 * **At module-specification level** it reports the built-in catalogue as broken: nothing produces
 * `CuttingOrderWithFabric` (required by `ProductionMrpModule`) and nothing produces
 * `FinishedGarmentUnit` (required by `QualityControlModule`).
 *
 * **At archetype level it is no better**, contrary to what it looks like at a glance. The declared
 * chain only lines up from `CUTTING` onward:
 *
 * ```
 * ORDER_INGESTION  → RAW_MATERIAL   ProductionOrderDraft     ≠ MaterialRequisition
 * RAW_MATERIAL     → COSTING_HPP    VerifiedMaterialStock    ≠ TechPackAndYieldData
 * COSTING_HPP      → CUTTING        CostingCalculationResult ≠ CuttingOrderWithFabric
 * CUTTING          → SEWING         CutPiecesBundle          = CutPiecesBundle          ✓
 * ```
 *
 * That is not a defect in the catalogue. The upstream slots are **planning** steps whose outputs are
 * *derived into* the next document rather than piped into it — a material requisition is worked out
 * from an order, it is not the order. Only the manufacturing half is a literal physical hand-off.
 *
 * And even there, equality is the wrong gate: this architecture exists so a tenant can bypass and
 * reorder slots. A CMT workshop that receives pre-cut panels has `SEWING` with no `CUTTING` before
 * it — a perfectly normal factory that strict port matching would reject. A validator that
 * contradicts the composable premise is worse than no validator, because its false alarms train
 * reviewers to ignore all of them.
 *
 * So this validator checks what can actually be wrong with a *translation*, and
 * [payloadMatches] is kept only as an informational helper — a mismatch there is normal.
 */
object ProposedFlowValidator {

    /** `CUSTOM_EXTENSION` declares this on both sides, making it a deliberate wildcard. */
    const val WILDCARD_PAYLOAD = "AnyOperationalPayload"

    /**
     * Whether two slots pass literally the same payload.
     *
     * **Informational only.** A `false` here is expected across planning slots and across any
     * bypassed step; do not treat it as an error. Useful for a canvas hint ("dokumen berubah bentuk
     * di sini"), not for gating a quote.
     */
    fun payloadMatches(upstream: ModuleArchetype, downstream: ModuleArchetype): Boolean {
        val produced = upstream.defaultProducedOutputType
        val expected = downstream.defaultExpectedInputType
        if (produced == WILDCARD_PAYLOAD || expected == WILDCARD_PAYLOAD) return true
        return produced == expected
    }

    /**
     * Problems worth a reviewer's attention, in Indonesian because they surface in the internal
     * review screen alongside the rest of the operator-facing copy.
     */
    fun warningsFor(requirements: List<CapabilityRequirement>): List<String> = buildList {
        addAll(duplicateSlotWarnings(requirements))
        missingOrderIntakeWarning(requirements)?.let { add(it) }
        addAll(unclassifiedWarnings(requirements))
    }

    /**
     * Two requirements landing in the same non-wildcard slot.
     *
     * Usually means the model split one need into two ("QC AQL" and "QC end-line" are one module),
     * which would double-count a gap and inflate the quote. `CUSTOM_EXTENSION` is excluded because
     * genuinely unrelated custom needs legitimately share that slot.
     */
    private fun duplicateSlotWarnings(requirements: List<CapabilityRequirement>): List<String> =
        requirements
            .filterNot { it.archetype == GarmentSlots.CUSTOM_EXTENSION }
            .groupBy { it.archetype }
            .filterValues { it.size > 1 }
            .map { (archetype, duplicates) ->
                "Slot \"${archetype.displayName}\" terisi ${duplicates.size} kebutuhan " +
                    "(${duplicates.joinToString(", ") { "\"${it.title}\"" }}). " +
                    "Kemungkinan satu kebutuhan terpecah dua - periksa sebelum dihitung sebagai dua modul."
            }

    /**
     * No order intake at all.
     *
     * Every factory takes work from somewhere. Its absence usually means the narrative described
     * only the production floor, so the quote would be missing a module the prospect will need on
     * day one.
     */
    private fun missingOrderIntakeWarning(requirements: List<CapabilityRequirement>): String? =
        if (requirements.none { it.archetype == GarmentSlots.ORDER_INGESTION }) {
            "Tidak ada tahap penerimaan pesanan di alur yang diusulkan. " +
                "Narasi mungkin hanya menjelaskan lantai produksi."
        } else {
            null
        }

    private fun unclassifiedWarnings(requirements: List<CapabilityRequirement>): List<String> =
        requirements.filter { it.isUnclassified }.map {
            "Kebutuhan \"${it.title}\" tidak cocok dengan slot mana pun dan diperlakukan sebagai " +
                "modul kustom - ini yang paling mungkin jadi biaya pembangunan baru."
        }
}

package com.eventverse.app.domain.stageflow

import com.eventverse.app.domain.tenant.TenantId

/**
 * Kerangka tahap alur sampling milik satu tenant — agregat yang menggantikan enum
 * `SamplingPipelineStage` sebagai sumber kebenaran urutan tahap (TRD-FLOW-001).
 *
 * Invarian:
 * - Diawali setidaknya satu [StageKind.ENTRY_ANCHOR] dan diakhiri setidaknya satu
 *   [StageKind.EXIT_ANCHOR]; tahap kerja selalu berada di antaranya.
 * - Kode tahap unik.
 * - Paling banyak [MAX_STAGES] tahap, supaya papan kanban tetap terbaca.
 */
data class TenantStageFlow(
    val tenantId: TenantId,
    val template: IndustryTemplateCode,
    val stages: List<StageDefinition>
) {
    init {
        require(stages.size <= MAX_STAGES) { "Kerangka alur maksimal $MAX_STAGES tahap, ada ${stages.size}" }
        val duplicates = stages.groupingBy { it.code }.eachCount().filterValues { it > 1 }.keys
        require(duplicates.isEmpty()) { "Kode tahap ganda: ${duplicates.joinToString { it.value }}" }
        require(stages.firstOrNull()?.kind == StageKind.ENTRY_ANCHOR) { "Kerangka alur wajib diawali tahap masuk" }
        require(stages.lastOrNull()?.kind == StageKind.EXIT_ANCHOR) { "Kerangka alur wajib diakhiri tahap keluar" }
        require(kindsAreGrouped()) { "Tahap kerja harus berada di antara tahap masuk dan tahap keluar" }
    }

    val workStages: List<StageDefinition> get() = stages.filter { it.kind == StageKind.WORK }

    fun find(code: StageCode): StageDefinition? = stages.firstOrNull { it.code == code }

    fun indexOf(code: StageCode): Int = stages.indexOfFirst { it.code == code }

    /** Tahap sesudah [code] menurut urutan tenant, atau `null` bila [code] terakhir / tak dikenal. */
    fun next(code: StageCode): StageDefinition? =
        indexOf(code).takeIf { it >= 0 }?.let { stages.getOrNull(it + 1) }

    fun withTrait(trait: StageTrait): List<StageDefinition> = stages.filter { it.has(trait) }

    /** ENTRY… lalu WORK… lalu EXIT… — urutan enum [StageKind] tidak pernah mundur. */
    private fun kindsAreGrouped(): Boolean =
        stages.zipWithNext().all { (a, b) -> a.kind.ordinal <= b.kind.ordinal }

    companion object {
        const val MAX_STAGES = 40
    }
}

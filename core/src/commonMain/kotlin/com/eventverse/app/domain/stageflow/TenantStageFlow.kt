package com.eventverse.app.domain.stageflow

import com.eventverse.app.domain.pipeline.code

import com.eventverse.app.domain.pack.GarmentSlots

import com.eventverse.app.domain.pipeline.ModuleArchetype
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

    // ── Suntingan tenant (TRD-FLOW-001 Tahap 3b) ────────────────────────────────────────────
    // Hanya tahap kerja yang bisa disunting; jangkar masuk/keluar dipakai invoice, surat jalan,
    // dan kustodi penyimpanan. Menyunting kerangka pabrik tidak mengubah SPK yang sudah beku (FR-5b).

    /** Menyisipkan tahap kerja baru tepat sesudah [after]. */
    fun insertAfter(after: StageCode, stage: StageDefinition): TenantStageFlow {
        require(stage.kind == StageKind.WORK) { "Hanya tahap kerja yang bisa ditambahkan" }
        require(find(stage.code) == null) { "Kode tahap ${stage.code.value} sudah dipakai" }
        val index = indexOf(after)
        require(index >= 0) { "Tahap jangkar ${after.value} tidak ada di kerangka" }
        require(stages[index].kind != StageKind.EXIT_ANCHOR) { "Tahap baru tidak bisa disisipkan sesudah tahap keluar" }
        val position = if (stages[index].kind == StageKind.ENTRY_ANCHOR) lastIndexOf(StageKind.ENTRY_ANCHOR) + 1 else index + 1
        return copy(stages = stages.toMutableList().apply { add(position, stage) })
    }

    fun remove(code: StageCode): TenantStageFlow {
        requireWork(code)
        return copy(stages = stages.filterNot { it.code == code }).also { it.requireEssentialRoles() }
    }

    /** Memindahkan tahap kerja [code] ke sesudah [after]. */
    fun move(code: StageCode, after: StageCode): TenantStageFlow {
        val stage = requireWork(code)
        require(code != after) { "Tahap tidak bisa dipindah ke sesudah dirinya sendiri" }
        return copy(stages = stages.filterNot { it.code == code }).insertAfter(after, stage)
    }

    fun rename(code: StageCode, displayName: String, shortLabel: String? = null): TenantStageFlow {
        val stage = requireNotNull(find(code)) { "Tahap ${code.value} tidak ada di kerangka" }
        val renamed = stage.copy(displayName = displayName.trim(), shortLabel = shortLabel?.trim()?.ifBlank { null } ?: stage.shortLabel)
        return copy(stages = stages.map { if (it.code == code) renamed else it })
    }

    private fun requireWork(code: StageCode): StageDefinition {
        val stage = requireNotNull(find(code)) { "Tahap ${code.value} tidak ada di kerangka" }
        require(stage.kind == StageKind.WORK) { "Tahap masuk/keluar tidak bisa diubah: ${stage.displayName}" }
        return stage
    }

    private fun lastIndexOf(kind: StageKind): Int = stages.indexOfLast { it.kind == kind }

    /**
     * Peran yang dicari aturan domain (QC lolos → pengemasan) wajib tetap ada. Tanpanya, setiap
     * inspeksi QC di tenant ini akan gagal — bukan kesalahan yang boleh dibuat lewat editor.
     */
    private fun requireEssentialRoles() {
        val work = stages.filter { it.kind == StageKind.WORK }
        require(work.any { it.archetype == GarmentSlots.QUALITY_CONTROL }) { "Kerangka wajib punya satu tahap QC" }
        require(work.any { it.archetype == GarmentSlots.FULFILLMENT }) { "Kerangka wajib punya satu tahap pengemasan" }
    }

    /** ENTRY… lalu WORK… lalu EXIT… — urutan enum [StageKind] tidak pernah mundur. */
    private fun kindsAreGrouped(): Boolean =
        stages.zipWithNext().all { (a, b) -> a.kind.ordinal <= b.kind.ordinal }

    companion object {
        const val MAX_STAGES = 40
    }
}

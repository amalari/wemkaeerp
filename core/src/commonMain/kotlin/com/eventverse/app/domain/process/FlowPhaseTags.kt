package com.eventverse.app.domain.process

import com.eventverse.app.domain.sampling.SamplingPipelineStage
import com.eventverse.app.domain.workqueue.WorkStationCatalog
import com.eventverse.app.domain.workqueue.WorkStationCode

/** Dua fase yang dijalani satu desain: dibuat sampelnya dulu, lalu diproduksi masal. */
enum class FlowPhase(val label: String) {
    SAMPLING("Sampling"),
    PRODUCTION("Produksi")
}

/**
 * Tahap wajib yang boleh dipilah per fase — padanan tahap sampling ↔ stasiun produksi.
 *
 * Hanya kerja basah/panas yang dipilah, karena hanya di situ pabrik benar-benar berbeda antar
 * fase: sampel sering tidak dicuci (cukup disetrika untuk foto buyer), sementara bulk dicuci
 * batch. Tahap lain — rajut, linking, QC, kemas — selalu dijalani kedua fase, jadi tidak diberi
 * pilihan yang hanya membuka peluang salah pilih.
 */
enum class PhaseTaggableStage(
    val samplingStage: SamplingPipelineStage,
    val productionStation: WorkStationCode
) {
    WASHING(SamplingPipelineStage.CUCI_SOFTENER, WorkStationCatalog.WASHING.code),
    PRESSING(SamplingPipelineStage.SETRIKA_UAP, WorkStationCatalog.STEAM.code);

    companion object {
        fun forSamplingStage(stage: SamplingPipelineStage): PhaseTaggableStage? =
            entries.firstOrNull { it.samplingStage == stage }

        fun parseOrNull(name: String?): PhaseTaggableStage? = entries.firstOrNull { it.name == name }
    }
}

/**
 * Fase mana saja yang menjalankan tiap [PhaseTaggableStage] — tag `[Sampling ×] [Produksi ×]`
 * di Penentuan Alur.
 *
 * Tahap yang tidak tercantum di [phases] dianggap dijalani **kedua** fase: default yang aman,
 * karena melompati tahap yang seharusnya dikerjakan jauh lebih mahal daripada mengerjakan tahap
 * yang ternyata tidak perlu. Kedua tag boleh dihapus sekaligus — tahap itu tidak dikerjakan sama
 * sekali untuk desain ini.
 */
data class StagePhaseTags(val phases: Map<PhaseTaggableStage, Set<FlowPhase>> = emptyMap()) {

    fun phasesOf(stage: PhaseTaggableStage): Set<FlowPhase> = phases[stage] ?: ALL_PHASES

    fun appliesTo(stage: PhaseTaggableStage, phase: FlowPhase): Boolean = phase in phasesOf(stage)

    /** Tombol × pada tag. */
    fun without(stage: PhaseTaggableStage, phase: FlowPhase): StagePhaseTags =
        copy(phases = phases + (stage to (phasesOf(stage) - phase)))

    /** Tag hantu "+ Sampling" — mengembalikan fase yang tadi dihapus. */
    fun with(stage: PhaseTaggableStage, phase: FlowPhase): StagePhaseTags =
        copy(phases = phases + (stage to (phasesOf(stage) + phase)))

    fun toggled(stage: PhaseTaggableStage, phase: FlowPhase): StagePhaseTags =
        if (appliesTo(stage, phase)) without(stage, phase) else with(stage, phase)

    /** Tahap sampling yang dilompati kartu sampling desain ini. */
    val skippedSamplingStages: Set<SamplingPipelineStage>
        get() = PhaseTaggableStage.entries
            .filterNot { appliesTo(it, FlowPhase.SAMPLING) }
            .mapTo(mutableSetOf()) { it.samplingStage }

    /**
     * Stasiun produksi yang aktif untuk desain ini, dari [line] lini produksinya.
     *
     * Bentuknya sengaja cocok dengan parameter `activeStations` milik
     * [WorkStationCatalog.nextAfter] — itulah titik warisnya ketika bulk order nanti tertaut ke
     * desain sampling.
     */
    fun productionActiveStations(
        line: List<WorkStationCode> = WorkStationCatalog.line().map { it.code }
    ): Set<WorkStationCode> {
        val skipped = PhaseTaggableStage.entries
            .filterNot { appliesTo(it, FlowPhase.PRODUCTION) }
            .mapTo(mutableSetOf()) { it.productionStation }
        return line.filterNot { it in skipped }.toSet()
    }

    /** Bentuk ringkas: hanya tahap yang menyimpang dari default yang perlu disimpan. */
    val normalized: StagePhaseTags
        get() = StagePhaseTags(phases.filterValues { it != ALL_PHASES })

    val isDefault: Boolean get() = normalized.phases.isEmpty()

    companion object {
        val ALL_PHASES: Set<FlowPhase> = FlowPhase.entries.toSet()
        val DEFAULT = StagePhaseTags()
    }
}

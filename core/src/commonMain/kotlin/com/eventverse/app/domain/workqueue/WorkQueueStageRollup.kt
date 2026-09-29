package com.eventverse.app.domain.workqueue

import com.eventverse.app.domain.pack.GarmentSlots

import com.eventverse.app.domain.pipeline.ModuleArchetype
import com.eventverse.app.domain.production.ProductionStage

/**
 * Jembatan (Seam) murni antara 11+ stasiun kanban dengan 3 tahap agregat [ProductionStage] di BulkWorkOrder.
 */
object WorkQueueStageRollup {

    /**
     * Memetakan kode stasiun kerja ke tahap makro [ProductionStage].
     */
    fun mapStationToStage(
        stationCode: WorkStationCode,
        customStations: List<WorkStationSpec> = emptyList()
    ): ProductionStage {
        val spec = WorkStationCatalog.resolve(stationCode, customStations)
        if (spec != null) {
            return when {
                spec.code == WorkStationCatalog.CUTTING.code -> ProductionStage.CUTTING
                spec.archetype == GarmentSlots.SEWING -> ProductionStage.SEWING
                else -> ProductionStage.FINISHING
            }
        }

        // Fallback berdasarkan konvensi kode
        val codeStr = stationCode.value.uppercase()
        return when {
            codeStr.contains("CUTTING") || codeStr.contains("POTONG") -> ProductionStage.CUTTING
            codeStr.contains("JAHIT") || codeStr.contains("OBRAS") || codeStr.contains("SUNTEK") ||
                codeStr.contains("KANCING") || codeStr.contains("ZIPER") || codeStr.contains("KNIT") -> ProductionStage.SEWING
            else -> ProductionStage.FINISHING
        }
    }

    /**
     * Menghitung total akumulasi pcs yang selesai per [ProductionStage].
     */
    fun rollupCompletedPcs(
        cards: List<WorkCard>,
        customStations: List<WorkStationSpec> = emptyList()
    ): Map<ProductionStage, Int> {
        val stageMap = mutableMapOf<ProductionStage, Int>()
        for (stage in ProductionStage.entries) {
            stageMap[stage] = 0
        }

        val cardsByStage = cards.groupBy { mapStationToStage(it.stationCode, customStations) }
        for ((stage, stageCards) in cardsByStage) {
            // Untuk tiap stage, progres adalah output dari stasiun terakhir di stage tersebut
            val maxCompleted = stageCards.groupBy { it.stationCode }
                .values
                .maxOfOrNull { stationCards -> stationCards.sumOf { it.completedPcs } } ?: 0
            stageMap[stage] = maxCompleted
        }

        return stageMap
    }
}

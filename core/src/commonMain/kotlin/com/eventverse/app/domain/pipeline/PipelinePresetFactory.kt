package com.eventverse.app.domain.pipeline

import com.eventverse.app.domain.blueprint.Blueprint

import com.eventverse.app.domain.pack.GarmentBlueprints

/**
 * Snapshot kanvas Factory Flow untuk sebuah preset bisnis (FOB, CMT, Brand D2C).
 *
 * Node dan sambungannya dirakit dari katalog modul oleh [CatalogPipelineBuilder]; file ini hanya
 * menghitung KPI agregat. Menambah modul operasional = mendaftarkannya di katalog — tidak ada
 * daftar node yang perlu disunting di sini (TRD-FLOW-002).
 */
object PipelinePresetFactory {

    fun createSnapshot(
        preset: Blueprint = GarmentBlueprints.DEFAULT,
        scenario: PipelineSimulationScenario = PipelineSimulationScenario.NORMAL
    ): FactoryPipelineSnapshot = snapshotOf(preset, CatalogPipelineBuilder.build((preset), scenario), scenario)

    /** KPI agregat atas sekumpulan node — dipakai juga oleh [TenantPipelineProjector]. */
    fun snapshotOf(
        preset: Blueprint,
        nodes: List<PipelineNode>,
        scenario: PipelineSimulationScenario
    ): FactoryPipelineSnapshot {
        val activeNodes = nodes.filterNot { it.isBypassed }
        // Rata-rata lead time: total cycle time aktif ÷ 8 jam kerja/hari + dampak skenario.
        val baseLeadDays = activeNodes.sumOf { it.cycleTimeHours } / 8.0
        val avgLeadDays = ((baseLeadDays + scenario.leadTimeImpactDays) * 10.0).let { kotlin.math.round(it) / 10.0 }

        val bottleneckPenalty = nodes.count { it.healthStatus == FlowHealthStatus.BOTTLENECK } * 7
        val criticalPenalty = nodes.count { it.healthStatus == FlowHealthStatus.CRITICAL } * 15
        val scenarioPenalty = when (scenario) {
            PipelineSimulationScenario.NORMAL -> 0
            PipelineSimulationScenario.QC_FABRIC_DEFECT -> 10
            PipelineSimulationScenario.QC_WORKMANSHIP_DEFECT -> 6
        }

        return FactoryPipelineSnapshot(
            preset = preset,
            nodes = nodes,
            overallHealthScore = (100 - bottleneckPenalty - criticalPenalty - scenarioPenalty).coerceIn(40, 100),
            totalWipPieces = activeNodes.sumOf { it.wipPieces },
            activeBottlenecks = nodes.count { it.isBottleneck },
            avgLeadTimeDays = avgLeadDays,
            activeModulesCount = activeNodes.size,
            bypassedModulesCount = nodes.count { it.isBypassed }
        )
    }
}

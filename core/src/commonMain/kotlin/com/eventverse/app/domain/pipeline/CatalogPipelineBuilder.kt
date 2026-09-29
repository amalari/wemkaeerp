package com.eventverse.app.domain.pipeline

/**
 * Merakit node kanvas Factory Flow **dari katalog modul** (TRD-FLOW-002 bagian A).
 *
 * - **Node mana & urutannya** = [OperationalModuleCatalog.all].
 * - **Bypass** = preset tidak ada di `supportedPresets` (seed boleh mengaktifkannya per skenario simulasi).
 * - **Sambungan** = port ([CatalogPortWiring]): aliran → `downstreamModuleCodes`, rujukan → port
 *   masuk otomatis di penerima.
 * - **Tampilan & telemetri contoh** = [PresetNodeSeeds]. Modul katalog tanpa seed mendapat node
 *   sintetis — itulah yang membuat modul baru otomatis muncul di kanvas.
 *
 * Untuk tiga preset bawaan hasilnya identik dengan seed (dijaga `CatalogPipelineBuilderTest`), jadi
 * kanvas, seed V10, dan pipeline tenant yang tersimpan tidak berubah.
 */
object CatalogPipelineBuilder {

    fun build(
        preset: GarmentBusinessPreset,
        scenario: PipelineSimulationScenario = PipelineSimulationScenario.NORMAL,
        specs: List<OperationalModuleSpecification> = OperationalModuleCatalog.all,
        seeds: List<PipelineNode> = PresetNodeSeeds.nodes(preset, scenario)
    ): List<PipelineNode> {
        val seedByModule = seeds.associateBy { it.module }
        val wiring = CatalogPortWiring.edges(preset, specs)
        return specs.mapIndexed { index, spec ->
            val isActive = preset in spec.supportedPresets
            val seed = seedByModule[spec.module]
            when {
                isActive -> (seed ?: synthesize(spec, preset)).wired(index + 1, wiring)
                // Di luar preset: seed boleh mengaktifkannya untuk skenario simulasi (CMT cacat kain →
                // gudang menerima kain pengganti buyer); tanpa seed, modul tampil ter-bypass.
                seed != null -> seed.copy(stepNumber = index + 1)
                else -> synthesize(spec, preset).asBypassed().copy(stepNumber = index + 1)
            }
        }
    }

    /** Menimpa bagian struktural seed dengan keputusan katalog; seed yang sudah cocok tidak disentuh. */
    private fun PipelineNode.wired(stepNumber: Int, wiring: List<CatalogPortEdge>): PipelineNode {
        val flowTargets = wiring.filter { it.from == module && !it.isReference }.map { it.to.code }
        val downstream = if (flowTargets.toSet() == downstreamModuleCodes.toSet()) downstreamModuleCodes else flowTargets
        val fedBy = inputs.filter { it.isAutomated }.mapNotNull { it.sourceModuleCode }.toSet() + downstreamSources(wiring)
        val missingReferencePorts = wiring
            .filter { it.to == module && it.isReference && it.from.code !in fedBy }
            .map { edge -> generatedPort(id, edge) }
        return copy(stepNumber = stepNumber, downstreamModuleCodes = downstream, inputs = inputs + missingReferencePorts)
    }

    /** Sumber yang sudah tergambar lewat `downstreamModuleCodes` pemasok — tidak perlu port kedua. */
    private fun PipelineNode.downstreamSources(wiring: List<CatalogPortEdge>): Set<String> =
        wiring.filter { it.to == module && !it.isReference }.map { it.from.code }.toSet()

    private fun generatedPort(nodeId: String, edge: CatalogPortEdge) = PipelineInputPort(
        id = "in-$nodeId-${edge.from.code}",
        name = edge.dataTypes.joinToString(" + "),
        isManual = false,
        sourceModuleCode = edge.from.code,
        sourceModuleName = edge.from.displayName,
        sourceOutputContract = edge.dataTypes.joinToString(" + "),
        description = "Diturunkan dari port katalog modul."
    )

    private fun PipelineNode.asBypassed(): PipelineNode =
        if (isBypassed) this else copy(
            healthStatus = FlowHealthStatus.BYPASSED,
            healthMessage = "Tidak termasuk preset ini; dapat diaktifkan per tenant.",
            wipPieces = 0
        )

    /** Node untuk modul katalog yang belum punya seed tampilan: data dari spec & archetype. */
    private fun synthesize(spec: OperationalModuleSpecification, preset: GarmentBusinessPreset): PipelineNode {
        val archetype = spec.archetype
        return PipelineNode(
            id = "${preset.nodeIdPrefix}-${spec.module.code.replace('_', '-')}",
            module = spec.module,
            stage = archetype.canvasPhase,
            stepNumber = 0,
            title = spec.module.displayName,
            description = spec.module.description,
            assignedDepartment = "Belum Ditugaskan",
            deptColorHex = archetype.canvasPhase.colorHex,
            inputContract = spec.inputsFor(preset).joinToString(" + ").ifBlank { archetype.defaultExpectedInputType },
            outputContract = spec.outputsFor(preset).joinToString(" + ").ifBlank { archetype.defaultProducedOutputType },
            wipPieces = 0,
            cycleTimeHours = 0.0,
            healthStatus = FlowHealthStatus.HEALTHY,
            healthMessage = "Modul baru dari katalog; telemetri operasional belum tersambung."
        )
    }

    private val GarmentBusinessPreset.nodeIdPrefix: String
        get() = when (this) {
            GarmentBusinessPreset.FOB_FULL_PACKAGE -> "fob"
            GarmentBusinessPreset.CMT_MAKLOON -> "cmt"
            GarmentBusinessPreset.BRAND_D2C -> "d2c"
        }
}


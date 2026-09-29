package com.eventverse.app.domain.contracts

import com.eventverse.app.domain.pack.GarmentPortTypes

import com.eventverse.app.domain.common.Quantity
import com.eventverse.app.domain.common.Ratio
import com.eventverse.app.domain.common.UnitOfMeasure
import com.eventverse.app.domain.masterdata.MaterialCategory
import com.eventverse.app.domain.pipeline.StockOwnershipSemantics
import kotlinx.datetime.Instant

object SampleSpecToTechPackAdapter {
    val descriptor = PortAdapterDescriptor(
        from = GarmentPortTypes.APPROVED_SAMPLE_SPECIFICATION.value,
        to = GarmentPortTypes.TECH_PACK_AND_YIELD_DATA.value,
        isLossy = true,
        explanation = "Tech Pack diturunkan otomatis dari spesifikasi sampel karena modul BOM di-bypass."
    )

    fun derive(
        spec: ApprovedSampleSpecification,
        defaultOwnership: StockOwnershipSemantics = StockOwnershipSemantics.CONSIGNED_CLIENT_MATERIAL,
        derivedAt: Instant
    ): TechPackAndYieldData {
        val totalWeight = spec.totalPanelWeight
        val yarnRefs = if (spec.yarns.isNotEmpty()) spec.yarns else listOf(MaterialRef.unresolved("Benang Standar Sampel"))
        val yarnCount = yarnRefs.size.coerceAtLeast(1)

        val bomLines = mutableListOf<BomLine>()
        var lineSeq = 1

        val weightPerYarnMicros = totalWeight.convertTo(UnitOfMeasure.GRAM).micros / yarnCount
        val remainderMicros = totalWeight.convertTo(UnitOfMeasure.GRAM).micros % yarnCount

        yarnRefs.forEachIndexed { idx, yarn ->
            val yarnWeightMicros = weightPerYarnMicros + (if (idx == 0) remainderMicros else 0L)
            bomLines += BomLine(
                lineId = "line-${lineSeq++}",
                material = yarn,
                category = MaterialCategory.YARN,
                netQuantityPerGarment = Quantity(yarnWeightMicros, UnitOfMeasure.GRAM),
                wasteAllowance = Ratio.percent(5.0),
                ownership = defaultOwnership,
                notes = if (yarnCount > 1) "Estimasi pembagian rata dari spesifikasi sampel." else "Diturunkan dari sampling order."
            )
        }

        spec.additionalProcesses.forEach { process ->
            if (process.quantityPerGarment != null) {
                bomLines += BomLine(
                    lineId = "line-${lineSeq++}",
                    material = process.material ?: MaterialRef.unresolved(process.name),
                    category = MaterialCategory.TRIM,
                    netQuantityPerGarment = process.quantityPerGarment,
                    wasteAllowance = Ratio.ZERO,
                    ownership = defaultOwnership,
                    notes = "Proses tambahan: ${process.name}"
                )
            }
        }

        val laborOperations = mutableListOf<LaborOperation>()
        var opSeq = 1

        if (spec.totalKnittingMinutes > 0) {
            laborOperations += LaborOperation(
                operationId = "op-${opSeq++}",
                name = "Rajut Mesin",
                samMinutes = Ratio(spec.totalKnittingMinutes, 1L),
                workstation = "Mesin Rajut",
                isSubcontracted = false
            )
        }

        if (spec.isWashed) {
            laborOperations += LaborOperation(
                operationId = "op-${opSeq++}",
                name = "Washing",
                samMinutes = Ratio.ZERO,
                workstation = "Washing",
                isSubcontracted = true
            )
        }

        spec.additionalProcesses.filter { it.quantityPerGarment == null }.forEach { process ->
            laborOperations += LaborOperation(
                operationId = "op-${opSeq++}",
                name = process.name,
                samMinutes = Ratio.ZERO,
                workstation = "Subkon / Finishing",
                isSubcontracted = true
            )
        }

        val sizeYieldFactors = if (spec.sizeCharts.isNotEmpty()) {
            spec.sizeCharts.map {
                SizeYieldFactor(
                    sizeLabel = it.sizeLabel,
                    scale = Ratio.ONE,
                    orderedQuantity = 0L
                )
            }
        } else {
            listOf(SizeYieldFactor(sizeLabel = "ALL SIZE", scale = Ratio.ONE, orderedQuantity = 0L))
        }

        return TechPackAndYieldData(
            techPackId = "derived-${spec.sourceOrderId}",
            tenantId = spec.tenantId,
            sourceSampleSpecId = spec.sourceOrderId,
            styleCode = spec.spkNumber,
            styleName = spec.styleName,
            bomLines = bomLines,
            laborOperations = laborOperations,
            sizeYieldFactors = sizeYieldFactors,
            preparedAt = derivedAt
        )
    }
}

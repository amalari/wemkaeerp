package com.eventverse.app.domain.techpack

import com.eventverse.app.domain.common.Quantity
import com.eventverse.app.domain.common.Ratio
import com.eventverse.app.domain.common.UnitOfMeasure
import com.eventverse.app.domain.contracts.ApprovedSampleSpecification
import com.eventverse.app.domain.contracts.BomLine
import com.eventverse.app.domain.contracts.LaborOperation
import com.eventverse.app.domain.contracts.MaterialRef
import com.eventverse.app.domain.contracts.SizeYieldFactor
import com.eventverse.app.domain.masterdata.MaterialCategory
import com.eventverse.app.domain.pipeline.StockOwnershipSemantics
import kotlinx.datetime.Instant

object TechPackDraftFactory {

    fun createFromSample(
        spec: ApprovedSampleSpecification,
        techPackId: TechPackId,
        styleCode: StyleCode,
        defaultOwnership: StockOwnershipSemantics = StockOwnershipSemantics.OWNED_RAW_MATERIAL,
        defaultWasteAllowance: Ratio = Ratio.percent(5.0),
        now: Instant
    ): TechPack {
        val totalWeight = spec.totalPanelWeight
        val yarnRefs = if (spec.yarns.isNotEmpty()) spec.yarns else listOf(MaterialRef.unresolved("Benang Sampel SPK"))
        val yarnCount = yarnRefs.size.coerceAtLeast(1)

        val bomLines = mutableListOf<BomLine>()
        var lineSeq = 1

        val totalMicros = totalWeight.convertTo(UnitOfMeasure.GRAM).micros
        val weightPerYarnMicros = totalMicros / yarnCount
        val remainderMicros = totalMicros % yarnCount

        yarnRefs.forEachIndexed { idx, yarn ->
            val yarnWeightMicros = weightPerYarnMicros + (if (idx == 0) remainderMicros else 0L)
            bomLines += BomLine(
                lineId = "line-${lineSeq++}",
                material = yarn,
                category = MaterialCategory.YARN,
                netQuantityPerGarment = Quantity(yarnWeightMicros, UnitOfMeasure.GRAM),
                wasteAllowance = defaultWasteAllowance,
                ownership = defaultOwnership,
                notes = if (yarnCount > 1) "Perkiraan dari pembagian ply feeder; perlu verifikasi timbangan." else "Diturunkan dari sampling order."
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

        return TechPack(
            id = techPackId,
            tenantId = spec.tenantId,
            styleCode = styleCode,
            styleName = spec.styleName,
            clientName = spec.clientName,
            status = TechPackStatus.DRAFT,
            version = 1,
            sourceSampleSpecId = spec.sourceOrderId,
            sourceSpkNumber = spec.spkNumber,
            bomLines = bomLines,
            laborOperations = laborOperations,
            sizeYieldFactors = sizeYieldFactors,
            notes = "Diturunkan dari SPK Sample #${spec.spkNumber}",
            createdAt = now,
            updatedAt = now
        )
    }
}

fun ApprovedSampleSpecification.toTechPackDraft(
    techPackId: TechPackId,
    styleCode: StyleCode,
    defaultOwnership: StockOwnershipSemantics = StockOwnershipSemantics.OWNED_RAW_MATERIAL,
    defaultWasteAllowance: Ratio = Ratio.percent(5.0),
    now: Instant
): TechPack = TechPackDraftFactory.createFromSample(
    spec = this,
    techPackId = techPackId,
    styleCode = styleCode,
    defaultOwnership = defaultOwnership,
    defaultWasteAllowance = defaultWasteAllowance,
    now = now
)

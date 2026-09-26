package com.eventverse.app.domain.sampling

import com.eventverse.app.domain.common.Money
import com.eventverse.app.domain.common.Quantity
import com.eventverse.app.domain.contracts.*
import com.eventverse.app.domain.masterdata.MaterialCategory
import kotlinx.datetime.Instant

/**
 * Pure domain mapper: transforms an ACC-approved [SamplingOrder] into the downstream port contract
 * [ApprovedSampleSpecification].
 */
fun SamplingOrder.toApprovedSampleSpecification(
    approvedAt: Instant,
    resolver: MaterialReferenceResolver = MaterialReferenceResolver.Passthrough
): Result<ApprovedSampleSpecification> {
    if (status != SamplingStatus.ACC_APPROVED) {
        return Result.failure(
            IllegalStateException("Order SPK '${spkNumber.value}' belum berstatus ACC_APPROVED (status saat ini: ${status.displayName})")
        )
    }

    val panelYields = buildList {
        if (yieldAndTiming.panelWeights.front > 0.0 || yieldAndTiming.panelMinutes.front > 0) {
            add(
                PanelYield(
                    panel = GarmentPanel.BODY_FRONT,
                    weight = Quantity.grams(yieldAndTiming.panelWeights.front),
                    knittingMinutes = yieldAndTiming.panelMinutes.front.toLong()
                )
            )
        }
        if (yieldAndTiming.panelWeights.back > 0.0 || yieldAndTiming.panelMinutes.back > 0) {
            add(
                PanelYield(
                    panel = GarmentPanel.BODY_BACK,
                    weight = Quantity.grams(yieldAndTiming.panelWeights.back),
                    knittingMinutes = yieldAndTiming.panelMinutes.back.toLong()
                )
            )
        }
        if (yieldAndTiming.panelWeights.sleeve > 0.0 || yieldAndTiming.panelMinutes.sleeve > 0) {
            add(
                PanelYield(
                    panel = GarmentPanel.SLEEVE_LEFT,
                    weight = Quantity.grams(yieldAndTiming.panelWeights.sleeve),
                    knittingMinutes = yieldAndTiming.panelMinutes.sleeve.toLong()
                )
            )
        }
        if (yieldAndTiming.panelWeights.collar > 0.0 || yieldAndTiming.panelMinutes.collar > 0) {
            add(
                PanelYield(
                    panel = GarmentPanel.COLLAR_RIB,
                    weight = Quantity.grams(yieldAndTiming.panelWeights.collar),
                    knittingMinutes = yieldAndTiming.panelMinutes.collar.toLong()
                )
            )
        }
        if (yieldAndTiming.panelWeights.placket > 0.0 || yieldAndTiming.panelMinutes.placket > 0) {
            add(
                PanelYield(
                    panel = GarmentPanel.PLACKET,
                    weight = Quantity.grams(yieldAndTiming.panelWeights.placket),
                    knittingMinutes = yieldAndTiming.panelMinutes.placket.toLong()
                )
            )
        }
    }

    val yarns = listOf(resolver.resolve(knitSpec.yarnType, MaterialCategory.YARN))

    val colorways = machineProgram.feederInstructions.map { entry ->
        val plyInt = entry.ply.filter { it.isDigit() }.toIntOrNull() ?: 1
        ColorwayFeeder(
            feederNumber = entry.feederNumber,
            role = entry.name,
            ply = plyInt,
            colorName = entry.color,
            yarn = resolver.resolve("${knitSpec.yarnType} ${entry.color}".trim(), MaterialCategory.YARN)
        )
    }

    val additionalProcesses = buildList {
        if (yieldAndTiming.additionalProcess.isNotBlank()) {
            add(
                AdditionalProcess(
                    name = yieldAndTiming.additionalProcess,
                    material = resolver.resolve(yieldAndTiming.additionalProcess, MaterialCategory.TRIM)
                )
            )
        }
    }

    val sizeCharts = finishedSizeCharts.map { sm ->
        val finishedMap = buildMap {
            put("Panjang Badan", sm.bodyLength)
            put("Lebar Badan", sm.bodyWidth)
            put("Panjang Lengan", sm.sleeveLength)
            put("Armhole", sm.armHole)
            put("Tinggi Rib", sm.ribHeight)
        }
        SpecSizeMeasurement(
            sizeLabel = sm.sizeLabel,
            finishedMeasurements = finishedMap
        )
    }

    val legacyHpp = if (yieldAndTiming.estimatedHppIdr > 0L) {
        Money.idr(yieldAndTiming.estimatedHppIdr)
    } else null

    return Result.success(
        ApprovedSampleSpecification(
            sourceOrderId = id.value,
            tenantId = tenantId,
            spkNumber = spkNumber.value,
            styleName = styleName,
            clientName = clientName,
            approvedAt = approvedAt,
            sizeMode = sizeMode.name,
            sizeCharts = sizeCharts,
            panelYields = panelYields,
            yarns = yarns,
            colorways = colorways,
            isWashed = yieldAndTiming.isWashed,
            additionalProcesses = additionalProcesses,
            legacyEstimatedHpp = legacyHpp
        )
    )
}

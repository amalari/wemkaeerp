package com.eventverse.app.shared.sampling

import com.eventverse.app.domain.sampling.*
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.shared.json.*
import com.eventverse.app.shared.process.ProcessCatalogCodec
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate

object SamplingOrderCodec {

    fun encode(order: SamplingOrder): JsonValue.Obj = jsonObjectOf(
        "id" to jsonOf(order.id.value),
        "tenantId" to jsonOf(order.tenantId.value),
        "spkNumber" to jsonOf(order.spkNumber.value),
        "clientName" to jsonOf(order.clientName),
        "styleName" to jsonOf(order.styleName),
        "status" to jsonOf(order.status.name),
        "pipelineStage" to jsonOf(order.pipelineStage.name),
        "finishingPath" to jsonOf(order.finishingPath.name),
        "vendorInfo" to encodeVendorInfo(order.vendorInfo),
        "sizeMode" to jsonOf(order.sizeMode.name),
        "deadlineProgram" to jsonOf(order.deadlineProgram?.toString()),
        "deadlineFinishing" to jsonOf(order.deadlineFinishing?.toString()),
        "deadlineDelivery" to jsonOf(order.deadlineDelivery?.toString()),
        "leadId" to jsonOf(order.leadId),
        "dealId" to jsonOf(order.dealId),
        "sampleQuantity" to jsonOf(order.sampleQuantity),
        "courierTracking" to jsonOf(order.courierTracking),
        "samplingFeeIdr" to jsonOf(order.samplingFeeIdr),
        "revisionCount" to jsonOf(order.revisionCount),
        "revisionHistory" to jsonArrayOf(order.revisionHistory.map { entry ->
            val pairs = mutableListOf(
                "revision" to jsonOf(entry.revision),
                "notes" to jsonOf(entry.notes),
                "at" to jsonOf(entry.at.toString())
            )
            entry.snapshot?.let { snap ->
                pairs.add("snapshot" to jsonObjectOf(
                    "mockupFrontKey" to jsonOf(snap.mockupFrontKey),
                    "mockupBackKey" to jsonOf(snap.mockupBackKey),
                    "sampleQuantity" to jsonOf(snap.sampleQuantity),
                    "samplingFeeIdr" to jsonOf(snap.samplingFeeIdr),
                    "notes" to jsonOf(snap.notes),
                    "pipelineStage" to jsonOf(snap.pipelineStage.name),
                    "finishingPath" to jsonOf(snap.finishingPath.name),
                    "vendorInfo" to encodeVendorInfo(snap.vendorInfo),
                    "finishingDeposits" to jsonArrayOf(snap.finishingDeposits.map(::encodeFinishingDeposit)),
                    "qcInspections" to jsonArrayOf(snap.qcInspections.map(::encodeQcInspectionReport)),
                    "sizeMatrix" to jsonArrayOf(snap.sizeMatrix.map { row ->
                        jsonObjectOf(
                            "id" to jsonOf(row.id),
                            "pomName" to jsonOf(row.pomName),
                            "values" to jsonStringMapOf(row.values)
                        )
                    })
                ))
            }
            JsonValue.Obj(pairs.toMap())
        }),
        "accNotes" to jsonOf(order.accNotes),
        "notes" to jsonOf(order.notes),
        "knitSpec" to encodeKnitSpec(order.knitSpec),
        "finishedSizeCharts" to jsonArrayOf(order.finishedSizeCharts.map(::encodeSizeMeasurement)),
        "rawKnitSizeCharts" to jsonArrayOf(order.rawKnitSizeCharts.map(::encodeSizeMeasurement)),
        "sizeMatrix" to jsonArrayOf(order.sizeMatrix.map { row ->
            jsonObjectOf(
                "id" to jsonOf(row.id),
                "pomName" to jsonOf(row.pomName),
                "values" to jsonStringMapOf(row.values)
            )
        }),
        "machineProgram" to SamplingProgramCodec.encodeMachineProgram(order.machineProgram),
        "yieldAndTiming" to SamplingProgramCodec.encodeYieldAndTiming(order.yieldAndTiming),
        "finishingDeposits" to jsonArrayOf(order.finishingDeposits.map(::encodeFinishingDeposit)),
        "qcInspections" to jsonArrayOf(order.qcInspections.map(::encodeQcInspectionReport)),
        "milestones" to jsonArrayOf(order.milestones.map(::encodeMilestoneProgress)),
        // jsonb dikirim sebagai string — konsisten dengan kolom jsonbText di server
        "stageInputs" to jsonOf(StageWorkInputCodec.encodeInputs(order.stageInputs)),
        "stageHistory" to jsonOf(StageWorkInputCodec.encodeHistory(order.stageHistory)),
        "activeWork" to jsonOf(StageWorkInputCodec.encodeClaim(order.activeWork)),
        "isCustomFlow" to jsonOf(order.isCustomFlow),
        "customFlowProcesses" to (order.customFlowProcesses?.let { ProcessCatalogCodec.encodeProcesses(it) } ?: JsonValue.Null),
        "createdAt" to jsonOf(order.createdAt.toString()),
        "updatedAt" to jsonOf(order.updatedAt.toString()),
        "archivedAt" to jsonOf(order.archivedAt?.toString())
    )

    fun decode(obj: JsonValue.Obj): SamplingOrder {
        val id = SamplingOrderId(obj.string("id") ?: "")
        val tenantId = TenantId(obj.string("tenantId") ?: "")
        val spkNumber = SpkNumber(obj.string("spkNumber") ?: "")
        val clientName = obj.string("clientName") ?: ""
        val styleName = obj.string("styleName") ?: ""
        val status = obj.string("status")?.let { runCatching { SamplingStatus.valueOf(it) }.getOrNull() } ?: SamplingStatus.DRAFT
        val pipelineStage = SamplingPipelineStage.parseOrNull(obj.string("pipelineStage"))
            ?: when (status) {
                SamplingStatus.DRAFT -> SamplingPipelineStage.NEW_INTAKE
                SamplingStatus.IN_PROGRESS -> SamplingPipelineStage.MACHINE_KNITTING
                SamplingStatus.REVISION -> SamplingPipelineStage.CAM_PROGRAMMING
                SamplingStatus.ACC_APPROVED -> SamplingPipelineStage.ACC_APPROVED
                SamplingStatus.CANCELLED -> SamplingPipelineStage.NEW_INTAKE
            }
        val finishingPath = obj.string("finishingPath")?.let { runCatching { FinishingPath.valueOf(it) }.getOrNull() } ?: FinishingPath.INTERNAL
        val vendorInfo = decodeVendorInfo(obj.obj("vendorInfo")) ?: MakloonVendorInfo()
        val sizeMode = obj.string("sizeMode")?.let { runCatching { SizeMode.valueOf(it) }.getOrNull() } ?: SizeMode.ALL_SIZE

        val deadlineProgram = com.eventverse.app.shared.common.DateTimeCodec.parseLocalDateOrNull(obj.string("deadlineProgram"))
        val deadlineFinishing = com.eventverse.app.shared.common.DateTimeCodec.parseLocalDateOrNull(obj.string("deadlineFinishing"))
        val deadlineDelivery = com.eventverse.app.shared.common.DateTimeCodec.parseLocalDateOrNull(obj.string("deadlineDelivery"))

        val leadId = obj.string("leadId")
        val dealId = obj.string("dealId")
        val sampleQuantity = obj.int("sampleQuantity") ?: 2
        val courierTracking = obj.string("courierTracking")
        val samplingFeeIdr = obj.long("samplingFeeIdr") ?: 0L
        val revisionCount = obj.int("revisionCount") ?: 0
        val accNotes = obj.string("accNotes") ?: ""
        val notes = obj.string("notes") ?: ""

        val knitSpec = obj.obj("knitSpec")?.let(::decodeKnitSpec) ?: KnitSpec()
        val finishedSizes = obj.objectArray("finishedSizeCharts").map(::decodeSizeMeasurement).ifEmpty {
            listOf(FactorySizePresets.ALL_SIZE_CARDIGAN_FINISHED)
        }
        val rawSizes = obj.objectArray("rawKnitSizeCharts").map(::decodeSizeMeasurement).ifEmpty {
            listOf(FactorySizePresets.ALL_SIZE_CARDIGAN_RAW_KNIT)
        }
        val sizeMatrix = obj.objectArray("sizeMatrix").map { rowObj ->
            val valuesMap = mutableMapOf<String, String>()
            rowObj.obj("values")?.entries?.forEach { (k, v) ->
                when (v) {
                    is JsonValue.Str -> valuesMap[k] = v.value
                    is JsonValue.Num -> valuesMap[k] = v.raw
                    else -> Unit
                }
            }
            SizeChartRow(
                id = rowObj.string("id") ?: "",
                pomName = rowObj.string("pomName") ?: "",
                values = valuesMap
            )
        }.ifEmpty { defaultSamplingSizeMatrix() }.let(::ensureSamplingQtyRow)
        val machineProgram = obj.obj("machineProgram")?.let { SamplingProgramCodec.decodeMachineProgram(it) } ?: MachineProgram()
        val yieldAndTiming = obj.obj("yieldAndTiming")?.let { SamplingProgramCodec.decodeYieldAndTiming(it) } ?: YieldAndTiming()
        val finishingDeposits = obj.objectArray("finishingDeposits").map(::decodeFinishingDeposit)
        val qcInspections = obj.objectArray("qcInspections").map(::decodeQcInspectionReport)
        val milestones = obj.objectArray("milestones").map(::decodeMilestoneProgress).ifEmpty {
            SamplingOrder.defaultMilestones()
        }

        val createdAt = com.eventverse.app.shared.common.DateTimeCodec.parseInstantOrFallback(
            obj.string("createdAt"),
            Instant.fromEpochMilliseconds(0)
        )
        val updatedAt = com.eventverse.app.shared.common.DateTimeCodec.parseInstantOrFallback(
            obj.string("updatedAt"),
            createdAt
        )
        val revisionHistory = obj.objectArray("revisionHistory").map { entry ->
            val snapObj = entry.obj("snapshot")
            val snap = snapObj?.let { sObj ->
                val snapMatrix = sObj.objectArray("sizeMatrix").map { rowObj ->
                    val valuesMap = mutableMapOf<String, String>()
                    rowObj.obj("values")?.entries?.forEach { (k, v) ->
                        when (v) {
                            is JsonValue.Str -> valuesMap[k] = v.value
                            is JsonValue.Num -> valuesMap[k] = v.raw
                            else -> Unit
                        }
                    }
                    SizeChartRow(
                        id = rowObj.string("id") ?: "",
                        pomName = rowObj.string("pomName") ?: "",
                        values = valuesMap
                    )
                }.ifEmpty { defaultSamplingSizeMatrix() }.let(::ensureSamplingQtyRow)
                val snapStage = SamplingPipelineStage.parseOrNull(sObj.string("pipelineStage")) ?: SamplingPipelineStage.NEW_INTAKE
                val snapPath = sObj.string("finishingPath")?.let { runCatching { FinishingPath.valueOf(it) }.getOrNull() } ?: FinishingPath.INTERNAL
                val snapVendor = decodeVendorInfo(sObj.obj("vendorInfo")) ?: MakloonVendorInfo()
                val snapDeposits = sObj.objectArray("finishingDeposits").map(::decodeFinishingDeposit)
                val snapInspections = sObj.objectArray("qcInspections").map(::decodeQcInspectionReport)
                SamplingSnapshot(
                    mockupFrontKey = sObj.string("mockupFrontKey"),
                    mockupBackKey = sObj.string("mockupBackKey"),
                    sizeMatrix = snapMatrix,
                    sampleQuantity = sObj.int("sampleQuantity") ?: 1,
                    samplingFeeIdr = sObj.long("samplingFeeIdr") ?: 0L,
                    notes = sObj.string("notes") ?: "",
                    pipelineStage = snapStage,
                    finishingPath = snapPath,
                    vendorInfo = snapVendor,
                    finishingDeposits = snapDeposits,
                    qcInspections = snapInspections
                )
            }
            RevisionFeedback(
                revision = entry.int("revision") ?: 0,
                notes = entry.string("notes") ?: "",
                at = com.eventverse.app.shared.common.DateTimeCodec.parseInstantOrFallback(
                    entry.string("at"),
                    updatedAt
                ),
                snapshot = snap
            )
        }
        val archivedAt = com.eventverse.app.shared.common.DateTimeCodec.parseInstantOrNull(obj.string("archivedAt"))
        val isCustomFlow = obj.boolean("isCustomFlow") ?: false
        val customFlowProcesses = if (isCustomFlow) {
            obj.array("customFlowProcesses")?.let { ProcessCatalogCodec.decodeProcesses(it, tenantId) }
        } else null

        return SamplingOrder(
            id = id,
            tenantId = tenantId,
            spkNumber = spkNumber,
            clientName = clientName,
            styleName = styleName,
            status = status,
            pipelineStage = pipelineStage,
            finishingPath = finishingPath,
            vendorInfo = vendorInfo,
            sizeMode = sizeMode,
            deadlineProgram = deadlineProgram,
            deadlineFinishing = deadlineFinishing,
            deadlineDelivery = deadlineDelivery,
            leadId = leadId,
            dealId = dealId,
            sampleQuantity = sampleQuantity,
            courierTracking = courierTracking,
            samplingFeeIdr = samplingFeeIdr,
            revisionCount = revisionCount,
            revisionHistory = revisionHistory,
            accNotes = accNotes,
            notes = notes,
            knitSpec = knitSpec,
            finishedSizeCharts = finishedSizes,
            rawKnitSizeCharts = rawSizes,
            sizeMatrix = sizeMatrix,
            machineProgram = machineProgram,
            yieldAndTiming = yieldAndTiming,
            finishingDeposits = finishingDeposits,
            qcInspections = qcInspections,
            milestones = milestones,
            stageInputs = StageWorkInputCodec.decodeInputs(obj.string("stageInputs")),
            stageHistory = StageWorkInputCodec.decodeHistory(obj.string("stageHistory"), createdAt),
            activeWork = StageWorkInputCodec.decodeClaim(obj.string("activeWork")),
            customFlowProcesses = customFlowProcesses,
            isCustomFlow = isCustomFlow,
            createdAt = createdAt,
            updatedAt = updatedAt,
            archivedAt = archivedAt
        )
    }

    private fun encodeKnitSpec(spec: KnitSpec): JsonValue.Obj = jsonObjectOf(
        "yarnType" to jsonOf(spec.yarnType),
        "knitType" to jsonOf(spec.knitType),
        "ribSpec" to jsonOf(spec.ribSpec),
        "collarSpec" to jsonOf(spec.collarSpec),
        "placketSpec" to jsonOf(spec.placketSpec),
        "colorwayNotes" to jsonOf(spec.colorwayNotes),
        "mockupImageUrls" to jsonArrayOf(spec.mockupImageUrls.map { jsonOf(it) })
    )

    private fun decodeKnitSpec(obj: JsonValue.Obj): KnitSpec = KnitSpec(
        yarnType = obj.string("yarnType") ?: "Viscose",
        knitType = obj.string("knitType") ?: "Jaquard 3-Color",
        ribSpec = obj.string("ribSpec") ?: "1x1 (2 Play)",
        collarSpec = obj.string("collarSpec") ?: "1x1 (2 Play)",
        placketSpec = obj.string("placketSpec") ?: "Fullneedle",
        colorwayNotes = obj.string("colorwayNotes") ?: "",
        mockupImageUrls = obj.stringArray("mockupImageUrls")
    )

    private fun encodeSizeMeasurement(m: SizeMeasurement): JsonValue.Obj = jsonObjectOf(
        "sizeLabel" to jsonOf(m.sizeLabel),
        "bodyLength" to jsonOf(m.bodyLength),
        "bodyWidth" to jsonOf(m.bodyWidth),
        "sleeveLength" to jsonOf(m.sleeveLength),
        "armHole" to jsonOf(m.armHole),
        "neckDrop" to jsonOf(m.neckDrop),
        "neckWidth" to jsonOf(m.neckWidth),
        "shoulderWidth" to jsonOf(m.shoulderWidth),
        "ribHeight" to jsonOf(m.ribHeight),
        "collarHeight" to jsonOf(m.collarHeight),
        "placketWidth" to jsonOf(m.placketWidth),
        "sleeveOpening" to jsonOf(m.sleeveOpening)
    )

    private fun decodeSizeMeasurement(obj: JsonValue.Obj): SizeMeasurement = SizeMeasurement(
        sizeLabel = obj.string("sizeLabel") ?: "ALL SIZE",
        bodyLength = obj.double("bodyLength") ?: 0.0,
        bodyWidth = obj.double("bodyWidth") ?: 0.0,
        sleeveLength = obj.double("sleeveLength") ?: 0.0,
        armHole = obj.double("armHole") ?: 0.0,
        neckDrop = obj.double("neckDrop") ?: 0.0,
        neckWidth = obj.double("neckWidth") ?: 0.0,
        shoulderWidth = obj.double("shoulderWidth") ?: 0.0,
        ribHeight = obj.double("ribHeight") ?: 0.0,
        collarHeight = obj.double("collarHeight") ?: 0.0,
        placketWidth = obj.double("placketWidth") ?: 0.0,
        sleeveOpening = obj.double("sleeveOpening") ?: 0.0
    )

    private fun encodeMilestoneProgress(m: MilestoneProgress): JsonValue.Obj = jsonObjectOf(
        "step" to jsonOf(m.step.name),
        "isCompleted" to jsonOf(m.isCompleted),
        "completedAt" to jsonOf(m.completedAt?.toString()),
        "notes" to jsonOf(m.notes)
    )

    private fun decodeMilestoneProgress(obj: JsonValue.Obj): MilestoneProgress {
        val step = obj.string("step")?.let { runCatching { MilestoneStep.valueOf(it) }.getOrNull() } ?: MilestoneStep.PROGRAM
        val isCompleted = obj.boolean("isCompleted") ?: false
        val completedAt = com.eventverse.app.shared.common.DateTimeCodec.parseLocalDateOrNull(obj.string("completedAt"))
        val notes = obj.string("notes") ?: ""
        return MilestoneProgress(step, isCompleted, completedAt, notes)
    }

    private fun encodeVendorInfo(vendor: MakloonVendorInfo?): JsonValue = if (vendor == null) JsonValue.Null else jsonObjectOf(
        "vendorName" to jsonOf(vendor.vendorName),
        "vendorPhone" to jsonOf(vendor.vendorPhone),
        "sentAt" to jsonOf(vendor.sentAt?.toString()),
        "expectedReturnAt" to jsonOf(vendor.expectedReturnAt?.toString()),
        "returnedAt" to jsonOf(vendor.returnedAt?.toString()),
        "costPerPcsIdr" to jsonOf(vendor.costPerPcsIdr),
        "status" to jsonOf(vendor.status.name),
        "notes" to jsonOf(vendor.notes)
    )

    private fun decodeVendorInfo(obj: JsonValue.Obj?): MakloonVendorInfo? {
        if (obj == null) return null
        return MakloonVendorInfo(
            vendorName = obj.string("vendorName") ?: "",
            vendorPhone = obj.string("vendorPhone") ?: "",
            sentAt = com.eventverse.app.shared.common.DateTimeCodec.parseLocalDateOrNull(obj.string("sentAt")),
            expectedReturnAt = com.eventverse.app.shared.common.DateTimeCodec.parseLocalDateOrNull(obj.string("expectedReturnAt")),
            returnedAt = com.eventverse.app.shared.common.DateTimeCodec.parseLocalDateOrNull(obj.string("returnedAt")),
            costPerPcsIdr = obj.long("costPerPcsIdr") ?: 0L,
            status = obj.string("status")?.let { runCatching { VendorFollowUpStatus.valueOf(it) }.getOrNull() } ?: VendorFollowUpStatus.NONE,
            notes = obj.string("notes") ?: ""
        )
    }

    private fun encodeFinishingDeposit(dep: FinishingDeposit): JsonValue.Obj = jsonObjectOf(
        "id" to jsonOf(dep.id),
        "samplingOrderId" to jsonOf(dep.samplingOrderId),
        "depositDate" to jsonOf(dep.depositDate.toString()),
        "qtyPcs" to jsonOf(dep.qtyPcs),
        "weightKg" to jsonOf(dep.weightKg),
        "scalePhotoKey" to jsonOf(dep.scalePhotoKey),
        "garmentPhotoKey" to jsonOf(dep.garmentPhotoKey),
        "operatorName" to jsonOf(dep.operatorName),
        "notes" to jsonOf(dep.notes),
        "createdAt" to jsonOf(dep.createdAt?.toString())
    )

    private fun decodeFinishingDeposit(obj: JsonValue.Obj): FinishingDeposit = FinishingDeposit(
        id = obj.string("id") ?: "",
        samplingOrderId = obj.string("samplingOrderId") ?: "",
        depositDate = com.eventverse.app.shared.common.DateTimeCodec.parseLocalDateOrNull(obj.string("depositDate")) ?: LocalDate(2026, 1, 1),
        qtyPcs = obj.int("qtyPcs") ?: 0,
        weightKg = obj.double("weightKg") ?: 0.0,
        scalePhotoKey = obj.string("scalePhotoKey"),
        garmentPhotoKey = obj.string("garmentPhotoKey"),
        operatorName = obj.string("operatorName") ?: "",
        notes = obj.string("notes") ?: "",
        createdAt = com.eventverse.app.shared.common.DateTimeCodec.parseInstantOrNull(obj.string("createdAt"))
    )

    private fun encodeQcInspectionReport(report: QcInspectionReport): JsonValue.Obj = jsonObjectOf(
        "id" to jsonOf(report.id),
        "samplingOrderId" to jsonOf(report.samplingOrderId),
        "kind" to jsonOf(report.kind.name),
        "inspectorName" to jsonOf(report.inspectorName),
        "inspectedAt" to jsonOf(report.inspectedAt.toString()),
        "pieceNo" to jsonOf(report.pieceNo),
        "inspectedQty" to jsonOf(report.inspectedQty),
        "pomMeasurements" to jsonArrayOf(report.pomMeasurements.map {
            jsonObjectOf(
                "pomName" to jsonOf(it.pomName),
                "targetCm" to jsonOf(it.targetCm),
                "actualCm" to jsonOf(it.actualCm),
                "toleranceCm" to jsonOf(it.toleranceCm),
                "notes" to jsonOf(it.notes),
                "carriedOver" to jsonOf(it.carriedOver)
            )
        }),
        "defectsFound" to jsonArrayOf(report.defectsFound.map { jsonOf(it) }),
        "qcResult" to jsonOf(report.qcResult.name),
        "qcNotes" to jsonOf(report.qcNotes),
        "verifiedPhotoFrontKey" to jsonOf(report.verifiedPhotoFrontKey),
        "verifiedPhotoBackKey" to jsonOf(report.verifiedPhotoBackKey)
    )

    private fun decodeQcInspectionReport(obj: JsonValue.Obj): QcInspectionReport = QcInspectionReport(
        id = obj.string("id") ?: "",
        samplingOrderId = obj.string("samplingOrderId") ?: "",
        // Lembar lama tidak punya "kind" — semuanya dulu inspeksi finishing.
        kind = obj.string("kind")?.let { runCatching { QcInspectionKind.valueOf(it) }.getOrNull() }
            ?: QcInspectionKind.FINISHING,
        inspectorName = obj.string("inspectorName") ?: "",
        inspectedAt = com.eventverse.app.shared.common.DateTimeCodec.parseInstantOrFallback(obj.string("inspectedAt"), Instant.fromEpochMilliseconds(0)),
        pieceNo = (obj.int("pieceNo") ?: 1).coerceAtLeast(1),
        inspectedQty = (obj.int("inspectedQty") ?: 1).coerceAtLeast(1),
        pomMeasurements = obj.objectArray("pomMeasurements").map {
            QcPomMeasurement(
                pomName = it.string("pomName") ?: "",
                targetCm = it.double("targetCm") ?: 0.0,
                actualCm = it.double("actualCm") ?: 0.0,
                toleranceCm = it.double("toleranceCm") ?: 1.0,
                notes = it.string("notes") ?: "",
                carriedOver = it.boolean("carriedOver") ?: false
            )
        },
        defectsFound = obj.stringArray("defectsFound"),
        qcResult = obj.string("qcResult")?.let { runCatching { QcInspectionResult.valueOf(it) }.getOrNull() } ?: QcInspectionResult.PASSED,
        qcNotes = obj.string("qcNotes") ?: "",
        verifiedPhotoFrontKey = obj.string("verifiedPhotoFrontKey"),
        verifiedPhotoBackKey = obj.string("verifiedPhotoBackKey")
    )
}

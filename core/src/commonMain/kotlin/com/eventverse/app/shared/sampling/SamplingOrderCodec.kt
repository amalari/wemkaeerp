package com.eventverse.app.shared.sampling

import com.eventverse.app.domain.sampling.*
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.shared.json.*
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
        "machineProgram" to encodeMachineProgram(order.machineProgram),
        "yieldAndTiming" to encodeYieldAndTiming(order.yieldAndTiming),
        "finishingDeposits" to jsonArrayOf(order.finishingDeposits.map(::encodeFinishingDeposit)),
        "qcInspections" to jsonArrayOf(order.qcInspections.map(::encodeQcInspectionReport)),
        "milestones" to jsonArrayOf(order.milestones.map(::encodeMilestoneProgress)),
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
        val pipelineStage = obj.string("pipelineStage")?.let { runCatching { SamplingPipelineStage.valueOf(it) }.getOrNull() }
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
        val machineProgram = obj.obj("machineProgram")?.let(::decodeMachineProgram) ?: MachineProgram()
        val yieldAndTiming = obj.obj("yieldAndTiming")?.let(::decodeYieldAndTiming) ?: YieldAndTiming()
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
                val snapStage = sObj.string("pipelineStage")?.let { runCatching { SamplingPipelineStage.valueOf(it) }.getOrNull() } ?: SamplingPipelineStage.NEW_INTAKE
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

    private fun encodeMachineProgram(prog: MachineProgram): JsonValue.Obj = jsonObjectOf(
        "programFront" to jsonOf(prog.programFront),
        "programBack" to jsonOf(prog.programBack),
        "programSleeve" to jsonOf(prog.programSleeve),
        "programCollar" to jsonOf(prog.programCollar),
        "programPlacket" to jsonOf(prog.programPlacket),
        "feederInstructions" to jsonArrayOf(prog.feederInstructions.map {
            jsonObjectOf(
                "feederNumber" to jsonOf(it.feederNumber),
                "name" to jsonOf(it.name),
                "ply" to jsonOf(it.ply),
                "color" to jsonOf(it.color)
            )
        }),
        "patternFormulas" to jsonObjectOf(
            "bodyLengthK" to jsonOf(prog.patternFormulas.bodyLengthK),
            "bodyWidthN" to jsonOf(prog.patternFormulas.bodyWidthN),
            "ribK" to jsonOf(prog.patternFormulas.ribK)
        ),
        "tensionSettings" to JsonValue.Obj(prog.tensionSettings.mapValues { jsonOf(it.value) }),
        "tenselityEntries" to jsonArrayOf(prog.tenselityEntries.map(::encodeTenselityEntry))
    )

    private fun decodeMachineProgram(obj: JsonValue.Obj): MachineProgram {
        val feederList = obj.objectArray("feederInstructions").map {
            FeederEntry(
                feederNumber = it.int("feederNumber") ?: 1,
                name = it.string("name") ?: "",
                ply = it.string("ply") ?: "",
                color = it.string("color") ?: ""
            )
        }
        val formulasObj = obj.obj("patternFormulas")
        val formulas = PatternFormulas(
            bodyLengthK = formulasObj?.double("bodyLengthK") ?: 2.94,
            bodyWidthN = formulasObj?.double("bodyWidthN") ?: 6.6,
            ribK = formulasObj?.double("ribK") ?: 4.7
        )
        val tension = obj.stringMap("tensionSettings")
        val tenselityList = obj.objectArray("tenselityEntries").map(::decodeTenselityEntry)

        return MachineProgram(
            programFront = obj.string("programFront") ?: "",
            programBack = obj.string("programBack") ?: "",
            programSleeve = obj.string("programSleeve") ?: "",
            programCollar = obj.string("programCollar") ?: "",
            programPlacket = obj.string("programPlacket") ?: "",
            feederInstructions = feederList,
            patternFormulas = formulas,
            tensionSettings = tension,
            tenselityEntries = tenselityList
        )
    }

    private fun encodeYieldAndTiming(y: YieldAndTiming): JsonValue.Obj = jsonObjectOf(
        "panelWeights" to jsonObjectOf(
            "front" to jsonOf(y.panelWeights.front),
            "back" to jsonOf(y.panelWeights.back),
            "sleeve" to jsonOf(y.panelWeights.sleeve),
            "collar" to jsonOf(y.panelWeights.collar),
            "placket" to jsonOf(y.panelWeights.placket)
        ),
        "panelMinutes" to jsonObjectOf(
            "front" to jsonOf(y.panelMinutes.front),
            "back" to jsonOf(y.panelMinutes.back),
            "sleeve" to jsonOf(y.panelMinutes.sleeve),
            "collar" to jsonOf(y.panelMinutes.collar),
            "placket" to jsonOf(y.panelMinutes.placket)
        ),
        "linkingNotes" to jsonOf(y.linkingNotes),
        "additionalProcess" to jsonOf(y.additionalProcess),
        "isWashed" to jsonOf(y.isWashed),
        "estimatedHppIdr" to jsonOf(y.estimatedHppIdr)
    )

    private fun decodeYieldAndTiming(obj: JsonValue.Obj): YieldAndTiming {
        val weightsObj = obj.obj("panelWeights")
        val minutesObj = obj.obj("panelMinutes")
        val weights = PanelWeightGrams(
            front = weightsObj?.double("front") ?: 0.0,
            back = weightsObj?.double("back") ?: 0.0,
            sleeve = weightsObj?.double("sleeve") ?: 0.0,
            collar = weightsObj?.double("collar") ?: 0.0,
            placket = weightsObj?.double("placket") ?: 0.0
        )
        val minutes = PanelKnittingMinutes(
            front = minutesObj?.int("front") ?: 0,
            back = minutesObj?.int("back") ?: 0,
            sleeve = minutesObj?.int("sleeve") ?: 0,
            collar = minutesObj?.int("collar") ?: 0,
            placket = minutesObj?.int("placket") ?: 0
        )
        return YieldAndTiming(
            panelWeights = weights,
            panelMinutes = minutes,
            linkingNotes = obj.string("linkingNotes") ?: "",
            additionalProcess = obj.string("additionalProcess") ?: "Pasang Kancing",
            isWashed = obj.boolean("isWashed") ?: false,
            estimatedHppIdr = obj.long("estimatedHppIdr") ?: 0L
        )
    }

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

    private fun encodeTenselityEntry(entry: TenselityEntry): JsonValue.Obj = jsonObjectOf(
        "parameter" to jsonOf(entry.parameter),
        "body" to jsonOf(entry.body),
        "sleeve" to jsonOf(entry.sleeve),
        "collar" to jsonOf(entry.collar)
    )

    private fun decodeTenselityEntry(obj: JsonValue.Obj): TenselityEntry = TenselityEntry(
        parameter = obj.string("parameter") ?: "",
        body = obj.string("body") ?: "",
        sleeve = obj.string("sleeve") ?: "",
        collar = obj.string("collar") ?: ""
    )

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
        "inspectorName" to jsonOf(report.inspectorName),
        "inspectedAt" to jsonOf(report.inspectedAt.toString()),
        "pomMeasurements" to jsonArrayOf(report.pomMeasurements.map {
            jsonObjectOf(
                "pomName" to jsonOf(it.pomName),
                "targetCm" to jsonOf(it.targetCm),
                "actualCm" to jsonOf(it.actualCm),
                "toleranceCm" to jsonOf(it.toleranceCm)
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
        inspectorName = obj.string("inspectorName") ?: "",
        inspectedAt = com.eventverse.app.shared.common.DateTimeCodec.parseInstantOrFallback(obj.string("inspectedAt"), Instant.fromEpochMilliseconds(0)),
        pomMeasurements = obj.objectArray("pomMeasurements").map {
            QcPomMeasurement(
                pomName = it.string("pomName") ?: "",
                targetCm = it.double("targetCm") ?: 0.0,
                actualCm = it.double("actualCm") ?: 0.0,
                toleranceCm = it.double("toleranceCm") ?: 1.0
            )
        },
        defectsFound = obj.stringArray("defectsFound"),
        qcResult = obj.string("qcResult")?.let { runCatching { QcInspectionResult.valueOf(it) }.getOrNull() } ?: QcInspectionResult.PASSED,
        qcNotes = obj.string("qcNotes") ?: "",
        verifiedPhotoFrontKey = obj.string("verifiedPhotoFrontKey"),
        verifiedPhotoBackKey = obj.string("verifiedPhotoBackKey")
    )
}

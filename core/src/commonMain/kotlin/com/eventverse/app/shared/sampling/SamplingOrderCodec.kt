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
        "sizeMode" to jsonOf(order.sizeMode.name),
        "deadlineProgram" to jsonOf(order.deadlineProgram?.toString()),
        "deadlineFinishing" to jsonOf(order.deadlineFinishing?.toString()),
        "deadlineDelivery" to jsonOf(order.deadlineDelivery?.toString()),
        "leadId" to jsonOf(order.leadId),
        "accNotes" to jsonOf(order.accNotes),
        "notes" to jsonOf(order.notes),
        "knitSpec" to encodeKnitSpec(order.knitSpec),
        "finishedSizeCharts" to jsonArrayOf(order.finishedSizeCharts.map(::encodeSizeMeasurement)),
        "rawKnitSizeCharts" to jsonArrayOf(order.rawKnitSizeCharts.map(::encodeSizeMeasurement)),
        "machineProgram" to encodeMachineProgram(order.machineProgram),
        "yieldAndTiming" to encodeYieldAndTiming(order.yieldAndTiming),
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
        val sizeMode = obj.string("sizeMode")?.let { runCatching { SizeMode.valueOf(it) }.getOrNull() } ?: SizeMode.ALL_SIZE

        val deadlineProgram = obj.string("deadlineProgram")?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val deadlineFinishing = obj.string("deadlineFinishing")?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val deadlineDelivery = obj.string("deadlineDelivery")?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

        val leadId = obj.string("leadId")
        val accNotes = obj.string("accNotes") ?: ""
        val notes = obj.string("notes") ?: ""

        val knitSpec = obj.obj("knitSpec")?.let(::decodeKnitSpec) ?: KnitSpec()
        val finishedSizes = obj.objectArray("finishedSizeCharts").map(::decodeSizeMeasurement).ifEmpty {
            listOf(FactorySizePresets.ALL_SIZE_CARDIGAN_FINISHED)
        }
        val rawSizes = obj.objectArray("rawKnitSizeCharts").map(::decodeSizeMeasurement).ifEmpty {
            listOf(FactorySizePresets.ALL_SIZE_CARDIGAN_RAW_KNIT)
        }
        val machineProgram = obj.obj("machineProgram")?.let(::decodeMachineProgram) ?: MachineProgram()
        val yieldAndTiming = obj.obj("yieldAndTiming")?.let(::decodeYieldAndTiming) ?: YieldAndTiming()
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
        val archivedAt = com.eventverse.app.shared.common.DateTimeCodec.parseInstantOrNull(obj.string("archivedAt"))

        return SamplingOrder(
            id = id,
            tenantId = tenantId,
            spkNumber = spkNumber,
            clientName = clientName,
            styleName = styleName,
            status = status,
            sizeMode = sizeMode,
            deadlineProgram = deadlineProgram,
            deadlineFinishing = deadlineFinishing,
            deadlineDelivery = deadlineDelivery,
            leadId = leadId,
            accNotes = accNotes,
            notes = notes,
            knitSpec = knitSpec,
            finishedSizeCharts = finishedSizes,
            rawKnitSizeCharts = rawSizes,
            machineProgram = machineProgram,
            yieldAndTiming = yieldAndTiming,
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
        "tensionSettings" to JsonValue.Obj(prog.tensionSettings.mapValues { jsonOf(it.value) })
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

        return MachineProgram(
            programFront = obj.string("programFront") ?: "",
            programBack = obj.string("programBack") ?: "",
            programSleeve = obj.string("programSleeve") ?: "",
            programCollar = obj.string("programCollar") ?: "",
            programPlacket = obj.string("programPlacket") ?: "",
            feederInstructions = feederList,
            patternFormulas = formulas,
            tensionSettings = tension
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
        val completedAt = obj.string("completedAt")?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val notes = obj.string("notes") ?: ""
        return MilestoneProgress(step, isCompleted, completedAt, notes)
    }
}

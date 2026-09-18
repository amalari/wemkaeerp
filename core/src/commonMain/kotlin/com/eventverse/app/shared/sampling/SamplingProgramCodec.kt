package com.eventverse.app.shared.sampling

import com.eventverse.app.domain.sampling.FeederEntry
import com.eventverse.app.domain.sampling.MachineProgram
import com.eventverse.app.domain.sampling.PanelKnittingMinutes
import com.eventverse.app.domain.sampling.PanelWeightGrams
import com.eventverse.app.domain.sampling.PatternFormulas
import com.eventverse.app.domain.sampling.TenselityEntry
import com.eventverse.app.domain.sampling.YieldAndTiming
import com.eventverse.app.shared.json.JsonValue
import com.eventverse.app.shared.json.jsonArrayOf
import com.eventverse.app.shared.json.jsonObjectOf
import com.eventverse.app.shared.json.jsonOf

/**
 * Codec jsonb sub-bagian "program mesin & yield" — dipisah dari [SamplingOrderCodec]
 * yang sudah di atas batas ukuran lapisan core. Satu sub-konsep, satu file.
 */
internal object SamplingProgramCodec {

    fun encodeMachineProgram(prog: MachineProgram): JsonValue.Obj = jsonObjectOf(
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

    fun decodeMachineProgram(obj: JsonValue.Obj): MachineProgram {
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

    fun encodeYieldAndTiming(y: YieldAndTiming): JsonValue.Obj = jsonObjectOf(
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

    fun decodeYieldAndTiming(obj: JsonValue.Obj): YieldAndTiming {
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
}


package com.eventverse.app.shared.sampling

import com.eventverse.app.domain.sampling.FeederEntry
import com.eventverse.app.domain.sampling.MachineProgram
import com.eventverse.app.domain.sampling.PanelKnittingMinutes
import com.eventverse.app.domain.sampling.PanelSizeSpec
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
 *
 * Publik, bukan internal, sejak repository Postgres di modul `server` ikut menyimpan
 * `panel_size_specs` pada kolomnya sendiri dan butuh bentuk JSON yang sama persis dengan
 * yang dipakai API. Dua penulis dengan dua format untuk satu data adalah cara termurah
 * menciptakan bug yang hanya muncul setelah restart.
 */
object SamplingProgramCodec {

    /** Dipakai repository server untuk kolom `sampling_yield_timings.panel_size_specs`. */
    fun encodePanelSizeSpecs(specs: List<PanelSizeSpec>): JsonValue =
        jsonArrayOf(specs.map(::encodePanelSizeSpec))

    fun decodePanelSizeSpecs(raw: String?): List<PanelSizeSpec> {
        if (raw.isNullOrBlank()) return emptyList()
        val parsed = runCatching { com.eventverse.app.shared.json.JsonParser.parse(raw) }.getOrNull()
        val array = parsed as? JsonValue.Arr ?: return emptyList()
        return array.items.filterIsInstance<JsonValue.Obj>().mapNotNull(::decodePanelSizeSpec)
    }

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
        "panelWeights" to encodeWeights(y.panelWeights),
        "panelMinutes" to encodeMinutes(y.panelMinutes),
        "perSize" to jsonArrayOf(y.perSize.map(::encodePanelSizeSpec)),
        "linkingNotes" to jsonOf(y.linkingNotes),
        "additionalProcess" to jsonOf(y.additionalProcess),
        "isWashed" to jsonOf(y.isWashed),
        "estimatedHppIdr" to jsonOf(y.estimatedHppIdr)
    )

    fun decodeYieldAndTiming(obj: JsonValue.Obj): YieldAndTiming {
        return YieldAndTiming(
            panelWeights = decodeWeights(obj.obj("panelWeights")),
            panelMinutes = decodeMinutes(obj.obj("panelMinutes")),
            perSize = obj.objectArray("perSize").mapNotNull(::decodePanelSizeSpec),
            linkingNotes = obj.string("linkingNotes") ?: "",
            additionalProcess = obj.string("additionalProcess") ?: "Pasang Kancing",
            isWashed = obj.boolean("isWashed") ?: false,
            estimatedHppIdr = obj.long("estimatedHppIdr") ?: 0L
        )
    }

    private fun encodeWeights(w: PanelWeightGrams): JsonValue.Obj = jsonObjectOf(
        "front" to jsonOf(w.front),
        "back" to jsonOf(w.back),
        "sleeve" to jsonOf(w.sleeve),
        "collar" to jsonOf(w.collar),
        "placket" to jsonOf(w.placket)
    )

    private fun decodeWeights(obj: JsonValue.Obj?): PanelWeightGrams = PanelWeightGrams(
        front = obj?.double("front") ?: 0.0,
        back = obj?.double("back") ?: 0.0,
        sleeve = obj?.double("sleeve") ?: 0.0,
        collar = obj?.double("collar") ?: 0.0,
        placket = obj?.double("placket") ?: 0.0
    )

    private fun encodeMinutes(m: PanelKnittingMinutes): JsonValue.Obj = jsonObjectOf(
        "front" to jsonOf(m.front),
        "back" to jsonOf(m.back),
        "sleeve" to jsonOf(m.sleeve),
        "collar" to jsonOf(m.collar),
        "placket" to jsonOf(m.placket)
    )

    private fun decodeMinutes(obj: JsonValue.Obj?): PanelKnittingMinutes = PanelKnittingMinutes(
        front = obj?.int("front") ?: 0,
        back = obj?.int("back") ?: 0,
        sleeve = obj?.int("sleeve") ?: 0,
        collar = obj?.int("collar") ?: 0,
        placket = obj?.int("placket") ?: 0
    )

    private fun encodePanelSizeSpec(spec: PanelSizeSpec): JsonValue.Obj = jsonObjectOf(
        "sizeLabel" to jsonOf(spec.sizeLabel),
        "weights" to encodeWeights(spec.weights),
        "minutes" to encodeMinutes(spec.minutes),
        "programs" to (spec.programs?.let(::encodeMachineProgram) ?: JsonValue.Null),
        "derivedFromSize" to jsonOf(spec.derivedFromSize)
    )

    /** Baris tanpa label ukuran dibuang, bukan dipaksa jadi objek tak sah yang meledak saat dibaca. */
    private fun decodePanelSizeSpec(obj: JsonValue.Obj): PanelSizeSpec? {
        val sizeLabel = obj.string("sizeLabel")?.takeIf { it.isNotBlank() } ?: return null
        val derived = obj.string("derivedFromSize")
            ?.takeIf { it.isNotBlank() && !it.equals(sizeLabel, ignoreCase = true) }
        return PanelSizeSpec(
            sizeLabel = sizeLabel,
            weights = decodeWeights(obj.obj("weights")),
            minutes = decodeMinutes(obj.obj("minutes")),
            programs = obj.obj("programs")?.let(::decodeMachineProgram),
            derivedFromSize = derived
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


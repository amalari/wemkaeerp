package com.eventverse.app.shared.sampling

import com.eventverse.app.domain.sampling.StageInputRow
import com.eventverse.app.domain.sampling.StageInputSection
import com.eventverse.app.domain.sampling.StageTransitionAudit
import com.eventverse.app.domain.sampling.StageWorkInput
import com.eventverse.app.domain.sampling.SamplingPipelineStage
import com.eventverse.app.shared.common.DateTimeCodec
import com.eventverse.app.shared.json.JsonParser
import com.eventverse.app.shared.json.JsonValue
import com.eventverse.app.shared.json.jsonArrayOf
import com.eventverse.app.shared.json.jsonObjectOf
import com.eventverse.app.shared.json.jsonOf
import kotlinx.datetime.Instant

/**
 * Codec jsonb untuk lembar input dinamis per tahap (`stage_inputs`) dan jejak audit
 * perpindahan tahap (`stage_history`).
 *
 * Sengaja dipisah dari [SamplingOrderCodec]: file induk sudah di atas batas ukuran lapisan
 * core, jadi setiap penambahan kontrak jsonb baru wajib punya file codec-nya sendiri.
 */
object StageWorkInputCodec {

    fun encodeInputs(inputs: List<StageWorkInput>): String =
        jsonArrayOf(inputs.map(::encodeInput)).encode()

    fun decodeInputs(raw: String?): List<StageWorkInput> =
        raw?.let {
            runCatching {
                (com.eventverse.app.shared.json.JsonParser.parse(it) as? JsonValue.Arr)
                    ?.items?.mapNotNull { item -> (item as? JsonValue.Obj)?.let(::decodeInput) }
            }.getOrNull()
        }.orEmpty()

    fun encodeHistory(history: List<StageTransitionAudit>): String =
        jsonArrayOf(history.map { entry ->
            jsonObjectOf(
                "fromStage" to jsonOf(entry.fromStage.name),
                "toStage" to jsonOf(entry.toStage.name),
                "actorEmail" to jsonOf(entry.actorEmail),
                "actorRole" to jsonOf(entry.actorRole),
                "at" to jsonOf(entry.at.toString())
            )
        }).encode()

    fun decodeHistory(raw: String?, fallbackAt: Instant): List<StageTransitionAudit> =
        raw?.let {
            runCatching {
                (com.eventverse.app.shared.json.JsonParser.parse(it) as? JsonValue.Arr)
                    ?.items?.mapNotNull { item ->
                        val obj = item as? JsonValue.Obj ?: return@mapNotNull null
                        StageTransitionAudit(
                            fromStage = parseStage(obj.string("fromStage")) ?: return@mapNotNull null,
                            toStage = parseStage(obj.string("toStage")) ?: return@mapNotNull null,
                            actorEmail = obj.string("actorEmail") ?: "",
                            actorRole = obj.string("actorRole") ?: "",
                            at = DateTimeCodec.parseInstantOrFallback(obj.string("at"), fallbackAt)
                        )
                    }
            }.getOrNull()
        }.orEmpty()

    fun encodeInput(input: StageWorkInput): JsonValue.Obj = jsonObjectOf(
        "stage" to jsonOf(input.stage.name),
        "sections" to jsonArrayOf(input.sections.map(::encodeSection))
    )

    private fun decodeInput(obj: JsonValue.Obj): StageWorkInput? {
        val stage = parseStage(obj.string("stage")) ?: return null
        return StageWorkInput(
            stage = stage,
            sections = obj.objectArray("sections").map(::decodeSection)
        )
    }

    private fun encodeSection(section: StageInputSection): JsonValue.Obj = jsonObjectOf(
        "section" to jsonOf(section.section),
        "rows" to jsonArrayOf(section.rows.map { row ->
            jsonObjectOf(
                "label" to jsonOf(row.label),
                "value" to jsonOf(row.value)
            )
        })
    )

    private fun decodeSection(obj: JsonValue.Obj): StageInputSection = StageInputSection(
        section = obj.string("section") ?: "",
        rows = obj.objectArray("rows").map { row ->
            StageInputRow(
                label = row.string("label") ?: "",
                value = row.string("value") ?: ""
            )
        }
    )

    private fun parseStage(name: String?): SamplingPipelineStage? =
        SamplingPipelineStage.parseOrNull(name)
}

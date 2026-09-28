package com.eventverse.app.shared.process

import com.eventverse.app.domain.process.FlowPhase
import com.eventverse.app.domain.process.PhaseTaggableStage
import com.eventverse.app.domain.process.StagePhaseTags
import com.eventverse.app.shared.json.JsonValue
import com.eventverse.app.shared.json.jsonArrayOf
import com.eventverse.app.shared.json.jsonOf

/**
 * Codec tag fase — dipakai wire REST (server ↔ client) dan kolom JSONB `sampling_orders`.
 *
 * Bentuknya `{"WASHING": ["PRODUCTION"]}`: hanya tahap yang menyimpang dari default yang ditulis.
 * `{}` berarti "desain ini sudah punya tag sendiri, dan isinya default" — berbeda dari `null`
 * yang berarti "belum punya, warisi template pabrik".
 */
object StagePhaseTagsCodec {

    fun encode(tags: StagePhaseTags?): JsonValue {
        if (tags == null) return JsonValue.Null
        return JsonValue.Obj(
            tags.normalized.phases.entries
                .sortedBy { it.key.ordinal }
                .associate { (stage, phases) ->
                    stage.name to jsonArrayOf(phases.sortedBy { it.ordinal }.map { jsonOf(it.name) })
                }
        )
    }

    /** Dekode toleran: nama tahap/fase tak dikenal diabaikan, bukan meledak. */
    fun decode(value: JsonValue?): StagePhaseTags? {
        val obj = value as? JsonValue.Obj ?: return null
        val phases = obj.entries.mapNotNull { (key, raw) ->
            val stage = PhaseTaggableStage.parseOrNull(key) ?: return@mapNotNull null
            val names = (raw as? JsonValue.Arr)?.items.orEmpty()
                .mapNotNull { (it as? JsonValue.Str)?.value }
            stage to names.mapNotNull { n -> FlowPhase.entries.firstOrNull { it.name == n } }.toSet()
        }.toMap()
        return StagePhaseTags(phases)
    }
}

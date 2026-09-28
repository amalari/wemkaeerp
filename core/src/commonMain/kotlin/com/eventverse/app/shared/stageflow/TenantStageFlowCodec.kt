package com.eventverse.app.shared.stageflow

import com.eventverse.app.domain.pipeline.ModuleArchetype
import com.eventverse.app.domain.stageflow.IndustryTemplateCode
import com.eventverse.app.domain.stageflow.StageCode
import com.eventverse.app.domain.stageflow.StageDefinition
import com.eventverse.app.domain.stageflow.StageKind
import com.eventverse.app.domain.stageflow.StageOrigin
import com.eventverse.app.domain.stageflow.StageTrait
import com.eventverse.app.domain.stageflow.TenantStageFlow
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.domain.workqueue.WorkExecutionMode
import com.eventverse.app.shared.json.JsonValue
import com.eventverse.app.shared.json.jsonArrayOf
import com.eventverse.app.shared.json.jsonObjectOf
import com.eventverse.app.shared.json.jsonOf

/**
 * Codec kerangka tahap — dipakai kolom JSONB `tenant_stage_flows.stages` dan wire REST.
 *
 * Dekode **ketat**: nilai yang tak dikenal melempar, bukan diabaikan. Kerangka yang diam-diam
 * kehilangan satu tahap akan membuat SPK di tahap itu tampak "hilang" dari papan — kegagalan
 * yang jauh lebih sulit dilacak daripada error saat dibaca.
 */
object TenantStageFlowCodec {

    fun encode(flow: TenantStageFlow): JsonValue.Obj = jsonObjectOf(
        "template" to jsonOf(flow.template.name),
        "stages" to encodeStages(flow.stages)
    )

    fun encodeStages(stages: List<StageDefinition>): JsonValue.Arr = jsonArrayOf(
        stages.map { stage ->
            jsonObjectOf(
                "code" to jsonOf(stage.code.value),
                "displayName" to jsonOf(stage.displayName),
                "kind" to jsonOf(stage.kind.name),
                "archetype" to jsonOf(stage.archetype.code),
                "traits" to jsonArrayOf(stage.traits.sortedBy { it.ordinal }.map { jsonOf(it.name) }),
                "origin" to jsonOf(stage.origin.name),
                "executionMode" to jsonOf(stage.executionMode.name),
                "shortLabel" to jsonOf(stage.shortLabel),
                "colorHex" to jsonOf(stage.colorHex),
                "remainingWorkFactor" to jsonOf(stage.remainingWorkFactor)
            )
        }
    )

    fun decode(tenantId: TenantId, obj: JsonValue.Obj): TenantStageFlow {
        val template = requireNotNull(IndustryTemplateCode.parseOrNull(obj.string("template"))) {
            "Template industri tidak dikenal: ${obj.string("template")}"
        }
        return TenantStageFlow(tenantId, template, decodeStages(obj.array("stages")))
    }

    fun decodeStages(items: List<JsonValue>): List<StageDefinition> = items.map { raw ->
        val obj = requireNotNull(raw as? JsonValue.Obj) { "Tahap harus berupa objek JSON" }
        val displayName = obj.string("displayName").orEmpty()
        StageDefinition(
            code = requireNotNull(StageCode.parseOrNull(obj.string("code"))) { "Kode tahap tidak valid: ${obj.string("code")}" },
            displayName = displayName,
            kind = enumOf<StageKind>(obj.string("kind"), "kind"),
            archetype = requireNotNull(ModuleArchetype.fromCode(obj.string("archetype"))) {
                "Archetype tidak dikenal: ${obj.string("archetype")}"
            },
            traits = obj.stringArray("traits").map { enumOf<StageTrait>(it, "trait") }.toSet(),
            origin = obj.string("origin")?.let { enumOf<StageOrigin>(it, "origin") } ?: StageOrigin.TEMPLATE,
            executionMode = obj.string("executionMode")?.let { enumOf<WorkExecutionMode>(it, "executionMode") }
                ?: WorkExecutionMode.IN_HOUSE,
            // Tampilan saja — tanpa nilai, tahap tetap utuh; jadi di sini boleh jatuh ke default.
            shortLabel = obj.string("shortLabel")?.takeIf { it.isNotBlank() } ?: displayName,
            colorHex = obj.long("colorHex") ?: StageDefinition.DEFAULT_COLOR_HEX,
            remainingWorkFactor = obj.double("remainingWorkFactor") ?: 1.0
        )
    }

    private inline fun <reified E : Enum<E>> enumOf(raw: String?, field: String): E =
        requireNotNull(enumValues<E>().firstOrNull { it.name == raw }) { "Nilai $field tidak dikenal: $raw" }
}

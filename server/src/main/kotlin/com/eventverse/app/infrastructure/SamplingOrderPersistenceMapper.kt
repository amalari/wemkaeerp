package com.eventverse.app.infrastructure

import com.eventverse.app.domain.process.StagePhaseTags
import com.eventverse.app.domain.process.TenantOptionalProcess
import com.eventverse.app.domain.sampling.*
import com.eventverse.app.domain.stageflow.StageCode
import com.eventverse.app.domain.stageflow.StageDefinition
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.shared.process.ProcessCatalogCodec
import com.eventverse.app.shared.process.StagePhaseTagsCodec
import com.eventverse.app.shared.json.*
import com.eventverse.app.shared.stageflow.TenantStageFlowCodec
import kotlinx.datetime.Instant

/**
 * Mapper helper untuk serialisasi/deserialisasi kolom jsonb pada tabel `sampling_orders`.
 */
internal object SamplingOrderPersistenceMapper {

    /** Alur proses kustom desain; `null` bila desain mewarisi template pabrik. */
    fun parseCustomFlow(raw: String?, isCustomFlow: Boolean, tenantId: TenantId): List<TenantOptionalProcess>? {
        if (!isCustomFlow || raw == null) return null
        return runCatching {
            (JsonParser.parse(raw) as? JsonValue.Arr)?.let { ProcessCatalogCodec.decodeProcesses(it.items, tenantId) }
        }.getOrNull()
    }

    /** Tag fase beku desain; `null` = belum dibekukan, warisi template pabrik. */
    fun parsePhaseTags(raw: String?): StagePhaseTags? =
        raw?.let { runCatching { StagePhaseTagsCodec.decode(JsonParser.parse(it)) }.getOrNull() }

    fun encodePhaseTags(tags: StagePhaseTags?): String? = tags?.let { StagePhaseTagsCodec.encode(it).encode() }

    fun encodeCustomFlow(processes: List<TenantOptionalProcess>?): String? =
        processes?.let { ProcessCatalogCodec.encodeProcesses(it).encode() }

    /**
     * Kerangka tahap beku SPK (V73). Dekode **ketat**: kerangka yang rusak dibiarkan meledak
     * alih-alih dianggap `null`, karena `null` berarti "pakai kerangka default" — kartu bordir
     * akan diam-diam dirender di atas kerangka rajut.
     */
    fun parseStageFlow(raw: String?): List<StageDefinition>? =
        raw?.let { TenantStageFlowCodec.decodeStages((JsonParser.parse(it) as JsonValue.Arr).items) }

    /** Tahap tersimpan, divalidasi terhadap kerangka beku SPK; tak dikenali → `NEW_INTAKE` seperti sebelumnya. */
    fun parseStageCode(rawStage: String?, rawFlow: String?): StageCode =
        resolveStoredStageCode(rawStage, parseStageFlow(rawFlow) ?: SamplingRoute.DEFAULT_STAGES)
            ?: SamplingPipelineStage.NEW_INTAKE.toStageCode()

    fun encodeStageFlow(stages: List<StageDefinition>?): String? =
        stages?.let { TenantStageFlowCodec.encodeStages(it).encode() }

    fun parseSizeMatrix(raw: String?): List<SizeChartRow> =
        runCatching {
            if (raw.isNullOrBlank()) return@runCatching defaultSamplingSizeMatrix()
            JsonParser.parseArray(raw)
                .filterIsInstance<JsonValue.Obj>()
                .map(::parseSizeChartRow)
                .ifEmpty { defaultSamplingSizeMatrix() }.let(::ensureSamplingQtyRow)
        }.getOrDefault(defaultSamplingSizeMatrix()).let(::ensureSamplingQtyRow)

    fun parseSizeChartRow(rowObj: JsonValue.Obj): SizeChartRow {
        val valuesMap = mutableMapOf<String, String>()
        rowObj.obj("values")?.entries?.forEach { (k, v) ->
            when (v) {
                is JsonValue.Str -> valuesMap[k] = v.value
                is JsonValue.Num -> valuesMap[k] = v.raw
                else -> Unit
            }
        }
        return SizeChartRow(
            id = rowObj.string("id") ?: "",
            pomName = rowObj.string("pomName") ?: "",
            values = valuesMap
        )
    }

    fun sizeMatrixJson(rows: List<SizeChartRow>): JsonValue.Arr =
        jsonArrayOf(rows.map { row ->
            jsonObjectOf(
                "id" to jsonOf(row.id),
                "pomName" to jsonOf(row.pomName),
                "values" to jsonStringMapOf(row.values)
            )
        })

    fun encodeRevisionHistory(history: List<RevisionFeedback>): String =
        jsonArrayOf(history.map { entry ->
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
                    "sizeMatrix" to sizeMatrixJson(snap.sizeMatrix)
                ))
            }
            JsonValue.Obj(pairs.toMap())
        }).encode()

    fun parseRevisionHistory(raw: String, fallbackAt: Instant): List<RevisionFeedback> =
        runCatching {
            JsonParser.parseArray(raw)
                .filterIsInstance<JsonValue.Obj>()
                .mapNotNull { entry ->
                    val at = runCatching { Instant.parse(entry.string("at") ?: "") }
                        .getOrDefault(fallbackAt)
                    val revision = entry.int("revision") ?: return@mapNotNull null
                    val snapObj = entry.obj("snapshot")
                    val snap = snapObj?.let { sObj ->
                        val snapMatrix = sObj.objectArray("sizeMatrix")
                            .map(::parseSizeChartRow)
                            .ifEmpty { defaultSamplingSizeMatrix() }.let(::ensureSamplingQtyRow)
                        SamplingSnapshot(
                            mockupFrontKey = sObj.string("mockupFrontKey"),
                            mockupBackKey = sObj.string("mockupBackKey"),
                            sizeMatrix = snapMatrix,
                            sampleQuantity = sObj.int("sampleQuantity") ?: 1,
                            samplingFeeIdr = sObj.long("samplingFeeIdr") ?: 0L,
                            notes = sObj.string("notes") ?: ""
                        )
                    }
                    RevisionFeedback(
                        revision = revision,
                        notes = entry.string("notes") ?: "",
                        at = at,
                        snapshot = snap
                    )
                }
        }.getOrDefault(emptyList())
}

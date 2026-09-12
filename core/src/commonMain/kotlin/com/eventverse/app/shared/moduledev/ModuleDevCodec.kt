package com.eventverse.app.shared.moduledev

import com.eventverse.app.domain.moduledev.BuildFeatureVector
import com.eventverse.app.domain.moduledev.EmbeddingVector
import com.eventverse.app.shared.json.JsonParser
import com.eventverse.app.shared.json.JsonValue
import com.eventverse.app.shared.json.jsonArrayOf
import com.eventverse.app.shared.json.jsonObjectOf
import com.eventverse.app.shared.json.jsonOf

/**
 * JSON encoding for the ledger's JSONB columns.
 *
 * Hand-rolled on [JsonParser] / [JsonValue] like the pipeline codecs, because the project does not
 * depend on kotlinx-serialization anywhere and adding it for one package would pull a compiler
 * plugin into all five KMP targets.
 *
 * Decoding is deliberately forgiving: a malformed or partial payload falls back to defaults rather
 * than throwing. These columns are read while listing history and computing prices, and one bad
 * legacy row should degrade a single neighbour, not take down the estimate.
 */
object ModuleDevCodec {

    // ---- Feature vector ------------------------------------------------------

    fun encodeFeatures(features: BuildFeatureVector): String = jsonObjectOf(
        BuildFeatureVector.KEY_ENTITY to jsonOf(features.entityCount),
        BuildFeatureVector.KEY_USE_CASE to jsonOf(features.useCaseCount),
        BuildFeatureVector.KEY_SCREEN to jsonOf(features.screenCount),
        BuildFeatureVector.KEY_API_ENDPOINT to jsonOf(features.apiEndpointCount),
        BuildFeatureVector.KEY_DB_TABLE to jsonOf(features.dbTableCount),
        BuildFeatureVector.KEY_REPORT to jsonOf(features.reportCount),
        BuildFeatureVector.KEY_INTEGRATION to jsonOf(features.integrationCount),
        "target_platform_count" to jsonOf(features.targetPlatformCount),
        BuildFeatureVector.KEY_AFFECTED_MODULE to jsonOf(features.affectedExistingModuleCount),
        BuildFeatureVector.KEY_CUSTOM_FORMULA to jsonOf(features.requiresCustomFormula),
        BuildFeatureVector.KEY_EXTERNAL_INTEGRATION to jsonOf(features.requiresExternalIntegration),
        BuildFeatureVector.KEY_REALTIME to jsonOf(features.requiresRealtime),
        BuildFeatureVector.KEY_OFFLINE_SYNC to jsonOf(features.requiresOfflineSync),
        BuildFeatureVector.KEY_FILE_UPLOAD to jsonOf(features.requiresFileUpload),
        BuildFeatureVector.KEY_NEW_DESIGN_COMPONENT to jsonOf(features.requiresNewDesignComponent)
    ).encode()

    fun decodeFeatures(json: String?): BuildFeatureVector {
        val obj = JsonParser.parseObjectOrNull(json) ?: return BuildFeatureVector.EMPTY
        return BuildFeatureVector(
            entityCount = obj.int(BuildFeatureVector.KEY_ENTITY) ?: 0,
            useCaseCount = obj.int(BuildFeatureVector.KEY_USE_CASE) ?: 0,
            screenCount = obj.int(BuildFeatureVector.KEY_SCREEN) ?: 0,
            apiEndpointCount = obj.int(BuildFeatureVector.KEY_API_ENDPOINT) ?: 0,
            dbTableCount = obj.int(BuildFeatureVector.KEY_DB_TABLE) ?: 0,
            reportCount = obj.int(BuildFeatureVector.KEY_REPORT) ?: 0,
            integrationCount = obj.int(BuildFeatureVector.KEY_INTEGRATION) ?: 0,
            // A stored zero would violate the value object's own invariant, so an absent or
            // corrupt platform count falls back to one rather than failing the whole read.
            targetPlatformCount = (obj.int("target_platform_count") ?: 1).coerceAtLeast(1),
            affectedExistingModuleCount = obj.int(BuildFeatureVector.KEY_AFFECTED_MODULE) ?: 0,
            requiresCustomFormula = obj.boolean(BuildFeatureVector.KEY_CUSTOM_FORMULA) ?: false,
            requiresExternalIntegration =
                obj.boolean(BuildFeatureVector.KEY_EXTERNAL_INTEGRATION) ?: false,
            requiresRealtime = obj.boolean(BuildFeatureVector.KEY_REALTIME) ?: false,
            requiresOfflineSync = obj.boolean(BuildFeatureVector.KEY_OFFLINE_SYNC) ?: false,
            requiresFileUpload = obj.boolean(BuildFeatureVector.KEY_FILE_UPLOAD) ?: false,
            requiresNewDesignComponent =
                obj.boolean(BuildFeatureVector.KEY_NEW_DESIGN_COMPONENT) ?: false
        )
    }

    // ---- Embedding -----------------------------------------------------------

    /**
     * Stored as a bare JSON array of numbers; the model and version live in their own columns.
     *
     * Keeping them out of the payload means a "which generation is this?" query is a plain
     * indexed column read, and it leaves the array shaped exactly as `vector(N)` will want if the
     * corpus ever outgrows cosine-in-Kotlin and moves to pgvector.
     */
    fun encodeEmbedding(embedding: EmbeddingVector): String =
        jsonArrayOf(embedding.values.map { jsonOf(it) }).encode()

    fun decodeEmbedding(json: String?, model: String?, version: String?): EmbeddingVector? {
        if (json == null || model.isNullOrBlank()) return null
        val values = runCatching { JsonParser.parseArray(json) }
            .getOrNull()
            ?.filterIsInstance<JsonValue.Num>()
            ?.mapNotNull { it.asDouble }
            ?: return null
        if (values.isEmpty()) return null
        return EmbeddingVector(values, model = model, version = version ?: "1")
    }

    // ---- Small collections ---------------------------------------------------

    fun encodeStrings(values: List<String>): String =
        jsonArrayOf(values.map { jsonOf(it) }).encode()

    fun decodeStrings(json: String?): List<String> =
        runCatching { JsonParser.parseArray(json ?: "[]") }
            .getOrDefault(emptyList())
            .filterIsInstance<JsonValue.Str>()
            .map { it.value }

    fun encodeBreakdown(breakdown: Map<String, Long>): String =
        JsonValue.Obj(breakdown.mapValues { (_, value) -> jsonOf(value) }).encode()

    fun decodeBreakdown(json: String?): Map<String, Long> {
        val obj = JsonParser.parseObjectOrNull(json) ?: return emptyMap()
        return obj.entries.mapNotNull { (key, value) ->
            (value as? JsonValue.Num)?.asLong?.let { key to it }
        }.toMap()
    }
}

package com.eventverse.app.routes.dto

import com.eventverse.app.domain.pipeline.CustomTenantPipeline
import com.eventverse.app.domain.pipeline.TenantModuleAvailability
import com.eventverse.app.domain.pipeline.TenantModuleEntitlement
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.shared.json.JsonParser
import com.eventverse.app.shared.json.JsonValue
import com.eventverse.app.shared.pipeline.PipelineGraphCodec
import com.eventverse.app.shared.pipeline.TenantModuleCatalogCodec

/**
 * HTTP boundary mapping for pipeline endpoints.
 *
 * Pipeline topology (de)serialisation is delegated to [PipelineGraphCodec] in `:core`, which
 * the Compose Multiplatform client shares, so server and client cannot disagree about the
 * wire format. Only response shapes unique to the API are built here.
 */
object PipelineDto {

    fun toJson(pipeline: CustomTenantPipeline): String = PipelineGraphCodec.encodePipeline(pipeline)

    fun fromJson(tenantId: TenantId, jsonStr: String): CustomTenantPipeline =
        PipelineGraphCodec.decodePipeline(tenantId, jsonStr)

    /** Reads the requested preset from a `{"preset":"cmt_makloon"}` style body. */
    fun readPresetCode(rawBody: String): String? =
        JsonParser.parseObjectOrNull(rawBody)?.string("preset")?.takeIf { it.isNotBlank() }

    /** Reads `{"moduleId":"...","isActive":true}` for module activation requests. */
    data class ModuleActivationRequest(val moduleId: String, val isActive: Boolean)

    fun readModuleActivation(rawBody: String): ModuleActivationRequest? {
        val root = JsonParser.parseObjectOrNull(rawBody) ?: return null
        val moduleId = root.string("moduleId")?.takeIf { it.isNotBlank() } ?: return null
        val isActive = root.boolean("isActive") ?: return null
        return ModuleActivationRequest(moduleId, isActive)
    }

    /** Reads `{"displayName":"...","formulaParameters":{...}}` for module rename requests. */
    data class ModuleRenameRequest(
        val displayName: String,
        val formulaParameters: Map<String, String>?
    )

    fun readModuleRename(rawBody: String): ModuleRenameRequest? {
        val root = JsonParser.parseObjectOrNull(rawBody) ?: return null
        val displayName = root.string("displayName")?.takeIf { it.isNotBlank() } ?: return null
        val parameters = if (root.has("formulaParameters")) {
            root.stringMap("formulaParameters")
        } else {
            null
        }
        return ModuleRenameRequest(displayName, parameters)
    }

    /** Reads a custom plugin installation request. */
    data class CustomModuleRequest(
        val moduleId: String,
        val name: String,
        val description: String,
        val archetypeCode: String?,
        val attachAfterNodeId: String?,
        val configSchemaJson: String?,
        val formulaParameters: Map<String, String>
    )

    fun readCustomModule(rawBody: String): CustomModuleRequest? {
        val root = JsonParser.parseObjectOrNull(rawBody) ?: return null
        val moduleId = root.string("moduleId")?.takeIf { it.isNotBlank() } ?: return null
        val name = root.string("name")?.takeIf { it.isNotBlank() } ?: return null
        return CustomModuleRequest(
            moduleId = moduleId,
            name = name,
            description = root.string("description") ?: "",
            archetypeCode = root.string("archetype"),
            attachAfterNodeId = root.string("attachAfterNodeId")?.takeIf { it.isNotBlank() },
            configSchemaJson = root["configSchema"]
                ?.takeIf { it !is JsonValue.Null }
                ?.encode(),
            formulaParameters = root.stringMap("formulaParameters")
        )
    }

    /**
     * Serialises the tenant's module catalogue together with its plan limits, using the
     * codec the Compose client also decodes with.
     */
    fun catalogToJson(
        entitlement: TenantModuleEntitlement,
        modules: List<TenantModuleAvailability>
    ): String = TenantModuleCatalogCodec.encode(entitlement, modules)
}

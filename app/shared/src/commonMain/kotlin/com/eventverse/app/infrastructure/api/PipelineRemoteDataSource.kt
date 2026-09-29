package com.eventverse.app.infrastructure.api

import com.eventverse.app.domain.blueprint.Blueprint

import com.eventverse.app.domain.pipeline.CustomTenantPipeline
import com.eventverse.app.domain.pipeline.TenantModuleCatalogSnapshot

/**
 * Remote operations the factory flow screen needs.
 *
 * Declared as an interface so the state holder can be unit tested against a fake instead of
 * a live HTTP client, per the project's presentation-layer testing rule.
 */
interface PipelineRemoteDataSource {

    suspend fun getPipeline(tenantSlug: String): Result<CustomTenantPipeline>

    suspend fun savePipeline(
        tenantSlug: String,
        pipeline: CustomTenantPipeline
    ): Result<CustomTenantPipeline>

    suspend fun resetPipeline(
        tenantSlug: String,
        preset: Blueprint
    ): Result<CustomTenantPipeline>

    suspend fun getModuleCatalog(tenantSlug: String): Result<TenantModuleCatalogSnapshot>

    suspend fun setModuleActivation(
        tenantSlug: String,
        moduleId: String,
        isActive: Boolean
    ): Result<CustomTenantPipeline>

    suspend fun renameModule(
        tenantSlug: String,
        nodeId: String,
        displayName: String,
        formulaParameters: Map<String, String>? = null
    ): Result<CustomTenantPipeline>

    /** Kerangka tahap tenant untuk kanvas level 2. Default gagal → kanvas memakai kerangka rajut. */
    suspend fun getStageFlow(): Result<List<com.eventverse.app.domain.stageflow.StageDefinition>> =
        Result.failure(UnsupportedOperationException())

    /** Telemetri nyata per modul. Default gagal → semua node aktif ditandai "estimasi". */
    suspend fun getTelemetry(tenantSlug: String): Result<List<com.eventverse.app.domain.pipeline.ModuleTelemetry>> =
        Result.failure(UnsupportedOperationException())
}

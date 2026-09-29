package com.eventverse.app.presentation.pipeline

import com.eventverse.app.domain.blueprint.Blueprint

import com.eventverse.app.domain.pipeline.CustomTenantPipeline
import com.eventverse.app.domain.pipeline.TenantModuleCatalogSnapshot
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.infrastructure.api.PipelineRemoteDataSource

/**
 * In-memory stand-in for the pipeline API.
 *
 * Presentation tests must not construct a real `HttpClient` — there is no engine on the test
 * classpath, and a unit test should not depend on one regardless.
 */
class FakePipelineRemoteDataSource(
    var pipeline: CustomTenantPipeline? = null,
    var catalog: TenantModuleCatalogSnapshot = TenantModuleCatalogSnapshot.EMPTY,
    var failure: Throwable? = null,
    private val tenantId: TenantId = TenantId("ten-fake")
) : PipelineRemoteDataSource {

    /** Ordered log of what the state holder asked for, for behavioural assertions. */
    val calls = mutableListOf<String>()

    private fun currentOrFailure(call: String): Result<CustomTenantPipeline> {
        calls += call
        failure?.let { return Result.failure(it) }
        return pipeline
            ?.let { Result.success(it) }
            ?: Result.failure(IllegalStateException("Tenant belum memiliki pipeline"))
    }

    override suspend fun getPipeline(tenantSlug: String): Result<CustomTenantPipeline> =
        currentOrFailure("get")

    override suspend fun savePipeline(
        tenantSlug: String,
        pipeline: CustomTenantPipeline
    ): Result<CustomTenantPipeline> {
        calls += "save"
        failure?.let { return Result.failure(it) }
        this.pipeline = pipeline
        return Result.success(pipeline)
    }

    override suspend fun resetPipeline(
        tenantSlug: String,
        preset: Blueprint
    ): Result<CustomTenantPipeline> {
        calls += "reset:${preset.code.value}"
        failure?.let { return Result.failure(it) }
        val fresh = CustomTenantPipeline.fromPreset(pipeline?.tenantId ?: tenantId, preset)
        pipeline = fresh
        return Result.success(fresh)
    }

    override suspend fun getModuleCatalog(tenantSlug: String): Result<TenantModuleCatalogSnapshot> {
        calls += "catalog"
        return Result.success(catalog)
    }

    override suspend fun setModuleActivation(
        tenantSlug: String,
        moduleId: String,
        isActive: Boolean
    ): Result<CustomTenantPipeline> {
        calls += "activation:$moduleId:$isActive"
        failure?.let { return Result.failure(it) }
        val current = pipeline ?: return Result.failure(IllegalStateException("no pipeline"))
        val node = current.nodes.firstOrNull { it.moduleId == moduleId }
            ?: return Result.failure(IllegalArgumentException("Modul tidak ditemukan: $moduleId"))
        val updated = current.setNodeBypassed(node.nodeId, !isActive)
        pipeline = updated
        return Result.success(updated)
    }

    override suspend fun renameModule(
        tenantSlug: String,
        nodeId: String,
        displayName: String,
        formulaParameters: Map<String, String>?
    ): Result<CustomTenantPipeline> {
        calls += "rename:$nodeId:$displayName"
        failure?.let { return Result.failure(it) }
        val current = pipeline ?: return Result.failure(IllegalStateException("no pipeline"))
        val updated = current.renameNode(nodeId, displayName).let { renamed ->
            formulaParameters?.let { renamed.updateNodeFormulaParameters(nodeId, it) } ?: renamed
        }
        pipeline = updated
        return Result.success(updated)
    }
}

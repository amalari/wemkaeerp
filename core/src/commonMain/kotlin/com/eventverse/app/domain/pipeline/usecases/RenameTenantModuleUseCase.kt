package com.eventverse.app.domain.pipeline.usecases

import com.eventverse.app.domain.pipeline.CustomTenantPipeline
import com.eventverse.app.domain.pipeline.GarmentBusinessPreset
import com.eventverse.app.domain.pipeline.TenantModuleEntitlement
import com.eventverse.app.domain.pipeline.TenantPipelineRepository
import com.eventverse.app.domain.tenant.TenantId

/**
 * Renames a module for one tenant only — factory A calls a node "Gudang Kain Roll" while
 * factory B calls the same node "Penerimaan Kain Buyer".
 *
 * Optionally updates that node's tenant-specific calculation parameters in the same
 * operation, since renaming and re-tariffing a station tend to happen together.
 */
class RenameTenantModuleUseCase(
    private val pipelineRepository: TenantPipelineRepository,
    private val getPipelineUseCase: GetTenantPipelineUseCase = GetTenantPipelineUseCase(pipelineRepository),
    private val savePipelineUseCase: SaveTenantPipelineUseCase = SaveTenantPipelineUseCase(pipelineRepository)
) {
    suspend operator fun invoke(
        tenantId: TenantId,
        nodeId: String,
        newDisplayName: String,
        formulaParameters: Map<String, String>? = null,
        entitlement: TenantModuleEntitlement? = null,
        fallbackPreset: GarmentBusinessPreset = GarmentBusinessPreset.DEFAULT
    ): Result<CustomTenantPipeline> = runCatching {
        val pipeline = getPipelineUseCase(tenantId, fallbackPreset).getOrThrow()
        val renamed = pipeline.renameNode(nodeId, newDisplayName.trim())
        val updated = formulaParameters
            ?.let { renamed.updateNodeFormulaParameters(nodeId, it) }
            ?: renamed

        savePipelineUseCase(updated, entitlement).getOrThrow()
    }
}

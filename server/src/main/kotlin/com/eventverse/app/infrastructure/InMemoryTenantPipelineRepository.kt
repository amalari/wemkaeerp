package com.eventverse.app.infrastructure

import com.eventverse.app.domain.pipeline.CustomTenantPipeline
import com.eventverse.app.domain.pipeline.GarmentBusinessPreset
import com.eventverse.app.domain.pipeline.TenantPipelineRepository
import com.eventverse.app.domain.tenant.TenantId
import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe in-memory implementation of TenantPipelineRepository.
 * Pre-seeded with starter pipelines for demo tenants.
 */
class InMemoryTenantPipelineRepository : TenantPipelineRepository {
    private val pipelines = ConcurrentHashMap<TenantId, CustomTenantPipeline>()

    init {
        // 1. Seed FOB pipeline for wemade-demo (ten-demo-001)
        val fobId = TenantId("ten-demo-001")
        pipelines[fobId] = CustomTenantPipeline.fromPreset(fobId, GarmentBusinessPreset.FOB_FULL_PACKAGE)

        // 2. Seed CMT pipeline for cv-berkah-makloon
        val cmtId = TenantId("ten-demo-cmt")
        pipelines[cmtId] = CustomTenantPipeline.fromPreset(cmtId, GarmentBusinessPreset.CMT_MAKLOON)

        // 3. Seed Brand D2C pipeline for urbanwear-d2c
        val d2cId = TenantId("ten-demo-d2c")
        pipelines[d2cId] = CustomTenantPipeline.fromPreset(d2cId, GarmentBusinessPreset.BRAND_D2C)
    }

    override suspend fun findByTenantId(tenantId: TenantId): CustomTenantPipeline? = pipelines[tenantId]

    override suspend fun save(pipeline: CustomTenantPipeline): Result<CustomTenantPipeline> {
        pipelines[pipeline.tenantId] = pipeline
        return Result.success(pipeline)
    }

    override suspend fun deleteByTenantId(tenantId: TenantId): Result<Unit> {
        pipelines.remove(tenantId)
        return Result.success(Unit)
    }
}

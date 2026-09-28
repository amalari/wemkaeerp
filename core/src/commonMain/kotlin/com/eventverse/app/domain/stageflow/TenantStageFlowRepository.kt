package com.eventverse.app.domain.stageflow

import com.eventverse.app.domain.tenant.TenantId

/** Penyimpanan kerangka tahap per tenant. Satu kerangka per tenant, ditulis utuh. */
interface TenantStageFlowRepository {
    suspend fun findByTenantId(tenantId: TenantId): TenantStageFlow?
    suspend fun save(flow: TenantStageFlow): Result<TenantStageFlow>
}

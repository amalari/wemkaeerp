package com.eventverse.app.domain.sampling

import com.eventverse.app.domain.tenant.TenantId

interface SamplingOrderRepository {
    suspend fun findById(id: SamplingOrderId): SamplingOrder?
    suspend fun findBySpkNumber(tenantId: TenantId, spkNumber: SpkNumber): SamplingOrder?
    suspend fun findAll(tenantId: TenantId, status: SamplingStatus? = null): List<SamplingOrder>

    /** Seluruh order sampling yang menempel pada satu deal — dasar gerbang Tab Produksi Massal. */
    suspend fun findByDealId(tenantId: TenantId, dealId: String): List<SamplingOrder>
    suspend fun save(order: SamplingOrder): SamplingOrder
    suspend fun nextSpkNumber(tenantId: TenantId): SpkNumber
    suspend fun archive(id: SamplingOrderId): Boolean
}

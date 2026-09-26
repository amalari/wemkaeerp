package com.eventverse.app.domain.costing

import com.eventverse.app.domain.pipeline.CostingBehavior
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Instant

interface CostingRateCardRepository {
    suspend fun findById(tenantId: TenantId, id: CostingRateCardId): CostingRateCard?
    /**
     * Rate card aktif ([CostingRateCard.effectiveTo] == null) per behavior.
     */
    suspend fun findActive(tenantId: TenantId, behavior: CostingBehavior): CostingRateCard?
    /**
     * Rate card yang berlaku pada tanggal tertentu (half-open interval check).
     * Digunakan saat me-replay kalkulasi historis.
     */
    suspend fun findEffectiveAt(tenantId: TenantId, behavior: CostingBehavior, at: Instant): CostingRateCard?
    suspend fun findAll(tenantId: TenantId, behavior: CostingBehavior): List<CostingRateCard>
    suspend fun save(rateCard: CostingRateCard)
}

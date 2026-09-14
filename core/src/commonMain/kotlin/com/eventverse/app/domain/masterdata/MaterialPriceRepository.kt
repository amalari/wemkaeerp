package com.eventverse.app.domain.masterdata

import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Instant

interface MaterialPriceRepository {
    suspend fun historyFor(tenantId: TenantId, materialId: MaterialId): MaterialPriceHistory
    suspend fun effectivePriceAt(
        tenantId: TenantId,
        materialId: MaterialId,
        at: Instant,
        source: PriceSource = PriceSource.STANDARD
    ): MaterialPrice?
    suspend fun effectivePricesAt(
        tenantId: TenantId,
        materialIds: Collection<MaterialId>,
        at: Instant,
        source: PriceSource = PriceSource.STANDARD
    ): Map<MaterialId, MaterialPrice>
    suspend fun append(price: MaterialPrice): MaterialPrice
    suspend fun policyFor(tenantId: TenantId): TenantPricePolicy
    suspend fun savePolicy(policy: TenantPricePolicy): TenantPricePolicy
}

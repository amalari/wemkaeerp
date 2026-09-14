package com.eventverse.app.infrastructure

import com.eventverse.app.domain.masterdata.*
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

class InMemoryMaterialPriceRepository : MaterialPriceRepository {
    private val prices = ConcurrentHashMap<String, CopyOnWriteArrayList<MaterialPrice>>()
    private val policies = ConcurrentHashMap<String, TenantPricePolicy>()

    override suspend fun historyFor(tenantId: TenantId, materialId: MaterialId): MaterialPriceHistory {
        val list = prices["${tenantId.value}:${materialId.value}"] ?: emptyList()
        return MaterialPriceHistory(materialId, list.sortedBy { it.effectiveFrom })
    }

    override suspend fun effectivePriceAt(
        tenantId: TenantId,
        materialId: MaterialId,
        at: Instant,
        source: PriceSource
    ): MaterialPrice? {
        val history = historyFor(tenantId, materialId)
        return history.priceAt(at, source)
    }

    override suspend fun effectivePricesAt(
        tenantId: TenantId,
        materialIds: Collection<MaterialId>,
        at: Instant,
        source: PriceSource
    ): Map<MaterialId, MaterialPrice> {
        val result = mutableMapOf<MaterialId, MaterialPrice>()
        for (id in materialIds) {
            val price = effectivePriceAt(tenantId, id, at, source)
            if (price != null) {
                result[id] = price
            }
        }
        return result
    }

    override suspend fun append(price: MaterialPrice): MaterialPrice {
        val key = "${price.tenantId.value}:${price.materialId.value}"
        val list = prices.computeIfAbsent(key) { CopyOnWriteArrayList() }
        list.add(price)
        return price
    }

    override suspend fun policyFor(tenantId: TenantId): TenantPricePolicy {
        return policies[tenantId.value] ?: TenantPricePolicy(tenantId)
    }

    override suspend fun savePolicy(policy: TenantPricePolicy): TenantPricePolicy {
        policies[policy.tenantId.value] = policy
        return policy
    }
}

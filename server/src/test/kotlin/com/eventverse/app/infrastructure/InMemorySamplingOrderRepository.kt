package com.eventverse.app.infrastructure

import com.eventverse.app.domain.sampling.*
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Clock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

class InMemorySamplingOrderRepository : SamplingOrderRepository {
    private val storage = ConcurrentHashMap<String, SamplingOrder>()
    private val sequence = AtomicLong(0L)

    override suspend fun findById(id: SamplingOrderId): SamplingOrder? =
        storage[id.value]

    override suspend fun findBySpkNumber(tenantId: TenantId, spkNumber: SpkNumber): SamplingOrder? =
        storage.values.find { it.tenantId == tenantId && it.spkNumber == spkNumber && it.archivedAt == null }

    override suspend fun findAll(tenantId: TenantId, status: SamplingStatus?): List<SamplingOrder> =
        storage.values
            .filter { it.tenantId == tenantId && it.archivedAt == null && (status == null || it.status == status) }
            .sortedByDescending { it.updatedAt }

    override suspend fun findByDealId(tenantId: TenantId, dealId: String): List<SamplingOrder> =
        storage.values
            .filter { it.tenantId == tenantId && it.dealId == dealId && it.archivedAt == null }
            .sortedBy { it.updatedAt }

    override suspend fun save(order: SamplingOrder): SamplingOrder {
        storage[order.id.value] = order
        return order
    }

    override suspend fun nextSpkNumber(tenantId: TenantId): SpkNumber {
        val nextVal = sequence.incrementAndGet()
        return SpkNumber("SPK-SMP-${nextVal.toString().padStart(4, '0')}")
    }

    override suspend fun archive(id: SamplingOrderId): Boolean {
        val existing = storage[id.value] ?: return false
        storage[id.value] = existing.copy(archivedAt = Clock.System.now())
        return true
    }
}

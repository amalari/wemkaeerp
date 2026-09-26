package com.eventverse.app.domain.production.usecases

import com.eventverse.app.domain.production.BulkProductionStatus
import com.eventverse.app.domain.production.BulkWorkOrder
import com.eventverse.app.domain.production.BulkWorkOrderId
import com.eventverse.app.domain.production.BulkWorkOrderRepository
import com.eventverse.app.domain.tenant.TenantId

class GetBulkWorkOrderListUseCase(
    private val repository: BulkWorkOrderRepository
) {
    suspend operator fun invoke(
        tenantId: TenantId,
        status: BulkProductionStatus? = null
    ): Result<List<BulkWorkOrder>> = runCatching {
        repository.findAll(tenantId, status)
    }
}

class GetBulkWorkOrderDetailUseCase(
    private val repository: BulkWorkOrderRepository
) {
    suspend operator fun invoke(id: BulkWorkOrderId): Result<BulkWorkOrder> = runCatching {
        repository.findById(id) ?: error("SPK massal tidak ditemukan: ${id.value}")
    }
}

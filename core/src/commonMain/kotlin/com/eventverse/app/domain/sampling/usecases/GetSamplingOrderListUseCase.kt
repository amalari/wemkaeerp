package com.eventverse.app.domain.sampling.usecases

import com.eventverse.app.domain.sampling.SamplingOrder
import com.eventverse.app.domain.sampling.SamplingOrderId
import com.eventverse.app.domain.sampling.SamplingOrderRepository
import com.eventverse.app.domain.sampling.SamplingStatus
import com.eventverse.app.domain.tenant.TenantId

class GetSamplingOrderListUseCase(
    private val repository: SamplingOrderRepository
) {
    suspend operator fun invoke(tenantId: TenantId, status: SamplingStatus? = null): Result<List<SamplingOrder>> = runCatching {
        repository.findAll(tenantId, status)
    }
}

class GetSamplingOrderDetailUseCase(
    private val repository: SamplingOrderRepository
) {
    suspend operator fun invoke(orderId: SamplingOrderId): Result<SamplingOrder> = runCatching {
        repository.findById(orderId) ?: error("SPK Sample dengan ID ${orderId.value} tidak ditemukan.")
    }
}

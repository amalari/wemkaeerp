package com.eventverse.app.domain.costing.usecases

import com.eventverse.app.domain.costing.CostingSheet
import com.eventverse.app.domain.costing.CostingSheetId
import com.eventverse.app.domain.costing.CostingSheetRepository
import com.eventverse.app.domain.costing.CostingSheetStatus
import com.eventverse.app.domain.tenant.TenantId

class GetCostingSheetListUseCase(
    private val sheetRepository: CostingSheetRepository
) {
    suspend operator fun invoke(
        tenantId: TenantId,
        techPackId: String? = null,
        status: CostingSheetStatus? = null
    ): Result<List<CostingSheet>> = runCatching {
        when {
            techPackId != null -> sheetRepository.findByTechPack(tenantId, techPackId).let { list ->
                if (status != null) list.filter { it.status == status } else list
            }
            status != null -> sheetRepository.findByStatus(tenantId, status)
            else -> sheetRepository.findAll(tenantId)
        }
    }

    suspend fun listByTechPack(tenantId: TenantId, techPackId: String): Result<List<CostingSheet>> =
        runCatching { sheetRepository.findByTechPack(tenantId, techPackId) }

    suspend fun listByStatus(tenantId: TenantId, status: CostingSheetStatus): Result<List<CostingSheet>> =
        runCatching { sheetRepository.findByStatus(tenantId, status) }

    suspend fun getById(tenantId: TenantId, sheetId: CostingSheetId): Result<CostingSheet> = runCatching {
        sheetRepository.findById(tenantId, sheetId)
            ?: error("Lembar HPP tidak ditemukan: ${sheetId.value}")
    }
}
